package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.mapper.OrderItemMapper;
import com.xbcl.personal_forum.mapper.OrdersMapper;
import com.xbcl.personal_forum.mapper.ShopMapper;
import com.xbcl.personal_forum.mapper.StockLogMapper;
import com.xbcl.personal_forum.pojo.dto.OrderCreateDTO;
import com.xbcl.personal_forum.pojo.entity.Address;
import com.xbcl.personal_forum.pojo.entity.Item;
import com.xbcl.personal_forum.pojo.entity.OrderItem;
import com.xbcl.personal_forum.pojo.entity.Orders;
import com.xbcl.personal_forum.pojo.entity.Shop;
import com.xbcl.personal_forum.pojo.entity.StockLog;
import com.xbcl.personal_forum.pojo.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单业务（W5 下半场：下单 + 模拟支付）。
 *
 * <p>W5 只做「能下单、能付钱」。发货 / 确认收货 / 取消 / 超时关单都是 W6，
 * 这里先把状态位和到期时间留好（status = 0 待付款，expire_at = 下单 + 15 分钟）。
 *
 * <p>写之前先把这三件事想清楚，这个文件 90% 的难度都在这里：
 * <ol>
 *   <li><b>库存只能在数据库里扣，而且必须是条件更新。</b>
 *       先 selectById 拿 stock、判断够不够、再 update 减掉 —— 两个请求同时进来会都读到 1、
 *       都判断通过、都扣成功，超卖就是这么来的（"读-判断-写"之间没有任何保护）。
 *       正确做法是一条 SQL 把判断和扣减合并成一次原子操作：
 *       {@code UPDATE item SET stock = stock - n WHERE id = ? AND stock >= n}，
 *       然后看<b>影响行数</b>：1 = 抢到了，0 = 已经没了。
 *       ItemMapper.deductStock 已经写好了，你只要判断返回值。</li>
 *   <li><b>一次事务里写完，且异常必须能触发回滚。</b>
 *       下单要写 orders、order_item、stock_log 三张表，任何一步失败都得整体撤销，
 *       否则会留下"扣了库存却没有订单"的对不上账的数据。
 *       {@code @Transactional(rollbackFor = Exception.class)} 写在 public 方法上；
 *       注意同类内部自调用不走代理，注解等于没写（真踩到就是"我明明加了注解，怎么没回滚"）。</li>
 *   <li><b>幂等。</b>用户连点 5 次「提交订单」，不能变成 5 个订单。两道闸：
 *       Service 里先按 (user_id, client_token) 查一次（挡住正常的超时重发），
 *       数据库 uk_client_token 唯一键兜底（挡住两个并发请求同时查空的情况）。</li>
 * </ol>
 *
 * <p>金额全程「分」，全在服务端算，不信前端。P1 没有券，discount 恒 0。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /** 未付款订单的有效期。W6 的定时关单按 (status, expire_at) 扫表，索引已经建好 */
    private static final int PAY_TIMEOUT_MINUTES = 15;

    /** 库存流水的动作标识，和 uk_order_item_reason(order_id, item_id, reason) 一起保证「一单一商品只记一次」 */
    private static final String REASON_ORDER = "ORDER";

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final StockLogMapper stockLogMapper;
    private final ItemMapper itemMapper;
    private final ShopMapper shopMapper;
    private final AddressService addressService;

    /**
     * 下单。
     *
     * <pre>
     * 步骤 1：幂等第一道闸 —— 先按 (userId, clientToken) 查一次：
     *   Orders exist = ordersMapper.selectOne(new LambdaQueryWrapper&lt;Orders&gt;()
     *           .eq(Orders::getUserId, userId)
     *           .eq(Orders::getClientToken, dto.getClientToken()));
     *   命中就把它 + 它的 order_item 组一个 OrderVO 返回（List.of(orderItemVO)），
     *   不要报错 —— 对用户来说"上次其实成功了，只是网络超时"，重试应该看到同一个订单。
     *
     * 步骤 2：地址 → 快照。
     *   Address addr = addressService.requireOwnAddress(userId, dto.getAddressId());
     *   把 receiverName / phone / region / detail 四个值拷到局部变量。
     *   为什么是拷而不是存 addressId：订单一旦创建就要和地址表解耦，
     *   用户之后改地址、删地址，历史订单的收货信息不能跟着变。
     *
     * 步骤 3：商品校验。
     *   Item item = itemMapper.selectById(dto.getItemId())；
     *   item == null || item.getStatus() != Item.STATUS_ON → 404 "商品不存在或已下架"；
     *   Shop shop = shopMapper.selectById(item.getShopId())，null → 404；
     *   Objects.equals(shop.getOwnerUserId(), userId) → new BusinessException("不能购买自己店铺的商品")。
     *   最后一句是 P1 的产品决定（自己刷自己的销量没意义），写注释说明是你刻意拦的。
     *
     * 步骤 4：算钱。
     *   long total = item.getPriceCents() * dto.getQuantity();   // long，别用 int
     *   long discount = 0L;                                      // P1 没券
     *   long pay = total - discount;
     *   精度提示：单价是"分"的 long，quantity 最多 99，乘出来不会溢出；但如果哪天改成 int，
     *   1 亿分 × 99 就翻车了 —— 钱相关的变量一律 long。
     *
     * 步骤 5：插 orders（先插订单，再扣库存）。
     *   先插的理由：order_item / stock_log 都要 order_id，而且如果把扣库存放前面，
     *   插订单撞唯一键时你还得想办法把库存加回去。先插订单，撞键时库里什么都没写，回滚最干净。
     *   字段：orderNo（见 generateOrderNo）、userId、shopId = item.getShopId()、
     *   status = Orders.STATUS_UNPAID、totalCents / discountCents / payCents、
     *   itemCount = dto.getQuantity()、clientToken、expireAt = now.plusMinutes(PAY_TIMEOUT_MINUTES)、
     *   四个收货快照。
     *   插的时候用 try / catch (DuplicateKeyException e) 包住：
     *     log.warn("重复提交命中唯一键 userId={} token={}", userId, dto.getClientToken());
     *     throw new BusinessException("请勿重复提交，稍后在「我的订单」里查看");
     *   （是的，第二道闸最终就是这条 400。验收要求只是"只生成 1 单"，不是"5 次都返回同一个 200"。）
     *   ⚠️ 不要在这里 catch 住再查一次"上次那单"返回：MySQL 默认 REPEATABLE READ，
     *   本事务的快照在步骤 1 那句 SELECT 时就定了，另一个事务刚提交的那行你这里查不到，
     *   会得到一个 null 又去 NPE。要查得到只能加锁读（... .last("FOR UPDATE")），P1 不值得。
     *
     * 步骤 6：扣库存 —— 整个文件最值钱的一行。
     *   int rows = itemMapper.deductStock(item.getId(), dto.getQuantity());
     *   if (rows == 0) {
     *       throw new BusinessException("商品已下架或库存不足");   // 0 行 = 上面那个 WHERE 没命中
     *   }
     *   为什么 0 行就够了：那条 UPDATE 的 WHERE 同时判断了「商品存在、没被删除、在售、库存够」，
     *   任何一条不满足都是 0 行。所以千万不要在它前面再 selectById 判断一遍库存 ——
     *   那又变成"读-判断-写"了，判断是白做的（但用它来给出更具体的错误文案是可以的）。
     *
     * 步骤 7：插 order_item（商品快照）。
     *   orderId = order.getId()（insert 后回填）、itemId、itemTitle = item.getTitle()、
     *   itemCoverUrl（可空，见步骤 9 的提示）、priceCents = item.getPriceCents()、
     *   quantity = dto.getQuantity()、subtotalCents = total、discountCents = discount、
     *   payCents = pay。
     *
     * 步骤 8：插 stock_log（库存流水）。
     *   orderId、itemId、delta = -dto.getQuantity()、beforeStock = item.getStock()、
     *   afterStock = item.getStock() - dto.getQuantity()、reason = REASON_ORDER。
     *   流水只用来对账 / 排障，不参与任何判断；并发下 beforeStock 不一定是"恰好那一刻"的值，
     *   但只要 delta 对得上，账就能核平。W6 取消订单回补库存时，靠 uk_order_item_reason 挡住重复回补。
     *
     * 步骤 9：return OrderVO.of(order, List.of(orderItem))。
     *   itemCoverUrl 想填的话：item.getImagesJson() 是 JSON 数组字符串，
     *   取第一张即可。ItemService.fromJson 是 private，你可以
     *   a) 注入 ObjectMapper（包名 tools.jackson.databind）自己解析，或
     *   b) 干脆填 null —— 列允许空，前端用占位图。
     *   W5 不卡这个，先把库存和幂等跑通。
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long userId, OrderCreateDTO dto) {
        Orders exist = ordersMapper.selectOne(new LambdaQueryWrapper<Orders>()
                           .eq(Orders::getUserId, userId)
                           .eq(Orders::getClientToken, dto.getClientToken())
        );
        if (exist != null) {
            List<OrderItem> orderItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getOrderId, exist.getId())
            );
            OrderVO vo = OrderVO.of(exist, orderItems);
            // 重试返回的 VO 要和首次一致，否则前端"店名"那一栏在重试后变空
            Shop existShop = shopMapper.selectById(exist.getShopId());
            if (existShop != null) {
                vo.setShopName(existShop.getName());
            }
            return vo;
        }

        // 地址 → 收货快照：只拷值，不存 addressId，订单要和地址表解耦
        Address addr = addressService.requireOwnAddress(userId, dto.getAddressId());

        Item item = itemMapper.selectById(dto.getItemId());

        if(item == null || item.getStatus() != Item.STATUS_ON){

            throw new BusinessException(404,"商品不存在或者已经下架");
        }

        Shop shop = shopMapper.selectById(item.getShopId());
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在");
        }
        if (Objects.equals(shop.getOwnerUserId(), userId)) {
            throw new BusinessException("不能购买自己店铺的商品");
        }
        long total = item.getPriceCents() * dto.getQuantity();
        long discount = 0L; // P1 没券
        long pay = total - discount;

        LocalDateTime now = LocalDateTime.now();
        // 步骤 5：先插 orders，再扣库存
        Orders order = new Orders();
        // 临时订单号：正式号里要 id，而 id 要 insert 之后才有（见 generateOrderNo / formatOrderNo）
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setShopId(item.getShopId());
        order.setStatus(Orders.STATUS_UNPAID);
        order.setTotalCents(total);
        order.setDiscountCents(discount);
        order.setPayCents(pay);
        order.setItemCount(dto.getQuantity());
        order.setClientToken(dto.getClientToken());
        order.setExpireAt(now.plusMinutes(PAY_TIMEOUT_MINUTES));
        order.setReceiverName(addr.getReceiverName());
        order.setReceiverPhone(addr.getPhone());
        order.setReceiverRegion(addr.getRegion());
        order.setReceiverDetail(addr.getDetail());
        // 显式写创建时间：created_at 的 DEFAULT CURRENT_TIMESTAMP 只作用于 SQL 层，
        // MP insert 不会把库里的默认值回填到 Java 对象上，不写的话返回的 VO 里 createdAt = null
        order.setCreatedAt(now);
        try {
            ordersMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("重复提交命中唯一键 userId={} token={}", userId, dto.getClientToken());
            throw new BusinessException("请勿重复提交，稍后在「我的订单」里查看");
            // 注意：不要在这里再查一次“上次那单”返回。
            // MySQL 默认 REPEATABLE READ，本事务快照在步骤 1 的 SELECT 时已定，
            // 另一个事务刚提交的行这里查不到，会得到 null 又 NPE。
        }
        // 拿到自增 id 才能拼正式订单号，回写一次（临时号只在 insert 那一瞬间存在）
        order.setOrderNo(formatOrderNo(order.getId(), now));
        ordersMapper.updateById(order);

        int rows = itemMapper.deductStock(item.getId(), dto.getQuantity());
        if (rows == 0) {
            throw new BusinessException("商品已下架或库存不足");
        }

        // 步骤 7：插 order_item（商品快照）
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setItemId(item.getId());
        orderItem.setItemTitle(item.getTitle());
        orderItem.setItemCoverUrl(null); // P1 可空，前端用占位图
        orderItem.setPriceCents(item.getPriceCents());
        orderItem.setQuantity(dto.getQuantity());
        orderItem.setSubtotalCents(total);
        orderItem.setDiscountCents(discount);
        orderItem.setPayCents(pay);
        orderItemMapper.insert(orderItem);

        // 步骤 8：插 stock_log 库存流水
        StockLog stockLog = new StockLog();
        stockLog.setOrderId(order.getId());
        stockLog.setItemId(item.getId());
        stockLog.setDelta(-dto.getQuantity());
        stockLog.setBeforeStock(item.getStock());
        stockLog.setAfterStock(item.getStock() - dto.getQuantity());
        stockLog.setReason(REASON_ORDER);
        stockLogMapper.insert(stockLog);

        // 步骤 9：返回（店名 OrderVO.of 不会填，这里手动补）
        OrderVO vo = OrderVO.of(order, List.of(orderItem));
        vo.setShopName(shop.getName());
        return vo;
    }



    /**
     * 模拟支付（P1 不接真支付，点一下就算付了）。
     *
     * <pre>
     * 步骤 10：条件更新，一条 SQL 同时完成三个判断 + 一次状态变更：
     *   LocalDateTime now = LocalDateTime.now();
     *   int rows = ordersMapper.update(null, new LambdaUpdateWrapper&lt;Orders&gt;()
     *           .eq(Orders::getId, orderId)
     *           .eq(Orders::getUserId, userId)              // 只能付自己的单
     *           .eq(Orders::getStatus, Orders.STATUS_UNPAID) // 只有待付款能付
     *           .gt(Orders::getExpireAt, now)                // 且没过期
     *           .set(Orders::getStatus, Orders.STATUS_PAID)
     *           .set(Orders::getPaidAt, now));
     *
     * 步骤 11：rows == 0 → 查一下订单，按原因给具体错误（都按 400）：
     *   查不到 / 不是自己的 → 404 "订单不存在"
     *   已支付  → "订单已支付，请勿重复付款"
     *   已取消/已完成 → "订单当前状态不可支付"
     *   expireAt 已过 → "订单已超时，请重新下单"（W5 的状态还是 0，W6 才有定时关单把它改成 4）
     *   ⚠️ 同一个事务里再查可能看到旧快照（另一笔刚提交的支付你查不到），
     *   所以"已支付"这条文案可能偶尔落到"不可支付"分支 —— 可以接受。
     *   想更准就用 .last("FOR UPDATE") 加锁读。别为了文案完美引入锁。
     *
     * 步骤 12：支付成功后，把这一单里每件商品的 sold_count 加上去。
     *   为什么要等到支付才加：下单不付款不能算"已售"，否则卖家会看到一堆虚假销量。
     *   P1 一单一商品，直接一条 UPDATE 就行：
     *   itemMapper.update(null, new LambdaUpdateWrapper&lt;Item&gt;()
     *           .eq(Item::getId, orderItem.getItemId())
     *           .setSql("sold_count = sold_count + " + orderItem.getQuantity()));
     *   setSql 里拼的是数字，而 quantity 已经被 @Min/@Max 卡在 1~99，
     *   所以没有注入风险；但凡换成用户能随便传的字符串，就必须用参数化写法。
     *   sold_count 更新失败要不要让支付失败？P1 让它一起失败（简单、可解释）：
     *   同事务里抛异常 = 状态回到待付款，用户再点一次。
     *
     * 步骤 13：查 order_item（eq OrderItem::getOrderId, orderId）→ return OrderVO.of(order, items)。
     *   ⚠️ order 这个对象是你从库里查出来的，update 用 wrapper 改的是库，
     *   对象上的 status / paidAt 还是旧值 —— 要么手动 set 到对象上，要么重新 selectById 一次。
     *   OrderVO 直接拿旧对象拼，前端会看到"支付成功但状态还是待付款"。
     * </pre>
     *
     * <p>为什么不把"重复支付"做成静默幂等（第二次也回 200 当前订单）：
     * 支付类接口静默成功会掩盖真正的重复扣款 bug。宁可报错，让用户看到"已经付过了"。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long userId, Long orderId) {
        LocalDateTime now = LocalDateTime.now();

        // 一条条件更新同时完成四个判断：是我的单、待付款、未过期、存在。
        // 返回值 1 = 抢到了这次支付；0 = 上面任一条件不满足（含并发下被别人抢先）。
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId)
                .eq(Orders::getStatus, Orders.STATUS_UNPAID)
                .gt(Orders::getExpireAt, now)
                .set(Orders::getStatus, Orders.STATUS_PAID)
                .set(Orders::getPaidAt, now));

        if (rows == 0) {
            // 更新失败才去查库，为的是给一句人话；正常路径上这里不该执行
            Orders order = ordersMapper.selectById(orderId);
            if (order == null || !Objects.equals(order.getUserId(), userId)) {
                throw new BusinessException(404, "订单不存在");
            }
            if (Integer.valueOf(Orders.STATUS_PAID).equals(order.getStatus())) {
                throw new BusinessException("订单已支付，请勿重复付款");
            }
            if (order.getExpireAt() != null && order.getExpireAt().isBefore(now)) {
                throw new BusinessException("订单已超时，请重新下单");
            }
            throw new BusinessException("订单当前状态不可支付");
        }

        // 支付成功才加销量：下单不付款不算"已售"。
        // 和状态更新在同一个事务里 —— 这里失败会整体回滚，用户再点一次即可。
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem oi : items) {
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, oi.getItemId())
                    .setSql("sold_count = sold_count + " + oi.getQuantity()));
        }

        // 重新查一次：wrapper 改的是库，内存里的对象还是"待付款"
        Orders paid = ordersMapper.selectById(orderId);
        OrderVO vo = OrderVO.of(paid, items);
        Shop shop = shopMapper.selectById(paid.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    /**
     * 订单详情。买家和卖家都能看，其他人一律 404。
     *
     * <pre>
     * 步骤 14：
     * 1) Orders order = ordersMapper.selectById(orderId)；null → 404 "订单不存在"。
     * 2) 权限：userId 等于 order.getUserId()（买家），或者等于
     *    shopMapper.selectById(order.getShopId()).getOwnerUserId()（卖家）。
     *    两者都不是 → 404（不是 403：403 会泄露"这单存在"）。
     * 3) List&lt;OrderItem&gt; items = orderItemMapper.selectList(...eq(OrderItem::getOrderId, orderId))。
     * 4) OrderVO vo = OrderVO.of(order, items)；vo.setShopName(shop.getName()) 回填店名。
     * 5) return vo。
     * </pre>
     *
     * <p>不在这里判断"是否超时"、也不顺手改状态：列表页会一次查 20 单，
     * 每单都写一次库就变成"读接口偷偷写数据"，很危险。超时只认 expire_at 这个字段，
     * 状态由 W6 的定时任务统一改。
     */
    public OrderVO detail(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        // 只有买家能看自己的单，店主能看自家店的单，其他人一律 404。
        // 用 404 而不是 403：别让人拿 id 试出"这单存在"。
        Shop shop = shopMapper.selectById(order.getShopId());
        boolean isBuyer = Objects.equals(order.getUserId(), userId);
        boolean isSeller = shop != null && Objects.equals(shop.getOwnerUserId(), userId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(404, "订单不存在");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        OrderVO vo = OrderVO.of(order, items);
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    /**
     * 订单号：OD + yyyyMMdd + 10 位补零 id（实体注释里定的格式，例：OD202511190000000042）。
     *
     * <pre>
     * 步骤 15 —— 这里有个"先有鸡还是先有蛋"：
     * 订单号里要 id，但 id 要 INSERT 之后才有；而 order_no 是 NOT NULL + 唯一键，
     * 又不能先插个空。两条路，选一条：
     *
     * 路 A（按实体注释）：先用临时号占位，拿到 id 后 updateById 改正式号。
     *   临时号唯一即可，比如 "T" + UUID 前 16 位（order_no 是 varchar(32) ascii，别超长）：
     *   String tmp = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
     *   insert 后：order.setOrderNo("OD" + ORDER_NO_FMT.format(now) + String.format("%010d", id));
     *   ordersMapper.updateById(order);
     *
     * 路 B（更省一次 UPDATE）：插入前就生成随机号，例如 OD + yyyyMMddHHmmss + 4 位随机，
     *   唯一键兜底，撞了重生成（概率百万分之一）。
     *
     * 两种都行，但注释写清楚你选了哪种、为什么。别写成"生成一个号就完事，不管重不重复"——
     * uk_order_no 会给你一个 DuplicateKeyException，而那时你已经分不清是订单号撞了
     * 还是 clientToken 撞了（都是 DuplicateKeyException！）。
     * 所以 catch 的地方要么用 e.getCause() 的约束名区分，要么把两种撞键处理成同一句提示。
     * </pre>
     */
    private String generateOrderNo() {
        // 走的是上面那条"路 A"：先占坑，拿到 id 后由 formatOrderNo 覆盖成正式号。
        // "T" 前缀是记号 —— 库里真留下 T 开头的行 = 插入成功、回写失败，可捞出来人工修。
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 正式订单号：OD + yyyyMMdd + 10 位补零 id，例：OD202511190000000042（20 位，够 VARCHAR(32)） */
    private String formatOrderNo(Long id, LocalDateTime now) {
        return "OD" + ORDER_NO_FMT.format(now) + String.format("%010d", id);
    }
}
