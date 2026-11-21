package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.mapper.OrderItemMapper;
import com.xbcl.personal_forum.mapper.OrdersMapper;
import com.xbcl.personal_forum.mapper.ShopMapper;
import com.xbcl.personal_forum.mapper.StockLogMapper;
import com.xbcl.personal_forum.pojo.dto.OrderCreateDTO;
import com.xbcl.personal_forum.pojo.dto.OrderShipDTO;
import com.xbcl.personal_forum.pojo.entity.Address;
import com.xbcl.personal_forum.pojo.entity.Item;
import com.xbcl.personal_forum.pojo.entity.OrderItem;
import com.xbcl.personal_forum.pojo.entity.Orders;
import com.xbcl.personal_forum.pojo.entity.Shop;
import com.xbcl.personal_forum.pojo.entity.StockLog;
import com.xbcl.personal_forum.pojo.vo.OrderVO;
import com.xbcl.personal_forum.pojo.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.util.Strings.trimToNull;

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

    /** W6：买家主动取消时，回补库存的 stock_log.reason */
    private static final String REASON_CANCEL = "CANCEL";

    /** W6：超时关单时，回补库存的 stock_log.reason（和 CANCEL 分开，同一单的两条路不会互相顶掉） */
    private static final String REASON_TIMEOUT = "TIMEOUT";

    /** 订单列表每页最多 50 条，和 ItemService / PostService 是同一套夹取规则 */
    private static final long MAX_PAGE_SIZE = 50;

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final StockLogMapper stockLogMapper;
    private final ItemMapper itemMapper;
    private final ShopMapper shopMapper;
    private final AddressService addressService;
    /** W6 才需要：发货/订单列表要先确认「当前用户是某家店的店主」 */
    private final ShopService shopService;

    /**
     * 下单。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order</code>，买家视角，带 token（userId 从 token 取，请求体里没有）。
     * <pre>
     * 请求体  {"itemId":7,"quantity":1,"addressId":3,"clientToken":"9f1c2a41-...-UUID"}
     *          itemId 必填 / quantity 1~99 / addressId 必填 / clientToken 必填（前端生成，重试要传同一个）
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"orderNo":"OD202511190000000012","status":0,"statusText":"待付款",
     *            "shopName":"XX 小店","totalCents":2000,"discountCents":0,"payCents":2000,
     *            "expireAt":"2025-11-19T21:20:00",
     *            "items":[{"itemTitle":"二手自行车","quantity":1,"payCents":2000}]}}
     * </pre>
     *
     * <p><b>会改的数据</b>
     * <ul>
     *   <li>orders：新增 1 行 —— status = 0 待付款、expire_at = 现在 + 15 分钟（过了这个点 W6 的定时任务会关掉它）</li>
     *   <li>item：stock -= quantity（条件更新，影响 0 行就算失败）</li>
     *   <li>order_item：新增 1 行，存商品标题 / 单价的<b>快照</b></li>
     *   <li>stock_log：新增 1 行，delta = -quantity、reason = ORDER</li>
     * </ul>
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 收货地址不存在 —— addressId 不是自己的 / 已删</li>
     *   <li>404 商品不存在或者已经下架</li>
     *   <li>404 店铺不存在</li>
     *   <li>400 不能购买自己店铺的商品</li>
     *   <li>400 商品已下架或库存不足</li>
     *   <li>400 请勿重复提交，稍后在「我的订单」里查看 —— clientToken 撞了唯一键</li>
     * </ul>
     *
     * <p><b>注意</b>
     * <ul>
     *   <li>事务：三张表要么全成要么全不成，所以 {@code @Transactional(rollbackFor = Exception.class)}。</li>
     *   <li>幂等两道闸：先按 (userId, clientToken) 查一次（挡住正常的超时重发），
     *       真并发靠 uk_client_token 唯一键兜底（撞键就是最后那条 400）。</li>
     *   <li>库存只认 deductStock 的影响行数，不要自己先 selectById 判断库存 —— 读-判断-写在并发下必超卖。</li>
     * </ul>
     *
     * @param dto itemId / quantity / addressId / clientToken，全部必填
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long userId, OrderCreateDTO dto) {
        // 1. 幂等第一道闸：同一个 clientToken 已经下过单 = 上次其实成功了（只是网络超时），
        //    把那单原样返回，不要报错 —— 用户重试应该看到「同一笔订单」。
        //    ps：这里只挡正常的超时重发；两个请求并发都查空时，靠 uk_client_token 唯一键兜底
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

        // 2. 地址 → 收货快照：只拷四个字段，不存 addressId
        //    ps：用户之后改地址、删地址，历史订单的收货信息不能跟着变 —— 订单要和地址表解耦
        Address addr = addressService.requireOwnAddress(userId, dto.getAddressId());

        // 3. 商品校验：存在 + 在售（下架就当不存在，别让人拿 id 试出「这商品曾经在卖」）
        Item item = itemMapper.selectById(dto.getItemId());

        if(item == null || item.getStatus() != Item.STATUS_ON){

            throw new BusinessException(404,"商品不存在或者已经下架");
        }

        Shop shop = shopMapper.selectById(item.getShopId());
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在");
        }
        if (Objects.equals(shop.getOwnerUserId(), userId)) {
            // ps：P1 的产品决定（自己刷自己的销量没意义），是刻意拦的，别当成 bug 删掉
            throw new BusinessException("不能购买自己店铺的商品");
        }
        // 4. 算钱：钱一律用 long，别用 int
        //    ps：单价是「分」的 long、quantity 最多 99，乘不溢出；万一改成 int，「1 亿分 × 99」就翻车
        long total = item.getPriceCents() * dto.getQuantity();
        long discount = 0L; // P1 没券
        long pay = total - discount;

        LocalDateTime now = LocalDateTime.now();
        // 5. 先插 orders，再扣库存
        //    ps：order_item / stock_log 都要 order_id，只能先有订单；反过来先扣库存，
        //        插订单撞唯一键时还得想办法把库存加回去 —— 先插，撞键时库里什么都没写，回滚最干净
        Orders order = new Orders();
        //    ps：先塞临时号 —— 正式号里要 id，而 id 要 insert 之后才有（见 generateOrderNo / formatOrderNo）
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
        //    ps：created_at 要显式写 —— 库里的 DEFAULT CURRENT_TIMESTAMP 只在 SQL 层生效，
        //        MP 的 insert 不会把默认值回填到 Java 对象，不写返回的 VO 里 createdAt 就是 null
        order.setCreatedAt(now);
        try {
            ordersMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("重复提交命中唯一键 userId={} token={}", userId, dto.getClientToken());
            throw new BusinessException("请勿重复提交，稍后在「我的订单」里查看");
            //    ps：不要在这里再查一次「上次那单」返回 —— MySQL 默认 REPEATABLE READ，
            //        本事务快照在第一条 SELECT 时就定了，另一个事务刚提交的行这里查不到，
            //        会得到 null 又 NPE。要查得到只能 .last("FOR UPDATE")，P1 不值得
        }
        //    ps：拿到自增 id 才能拼正式订单号，回写一次（临时号只在 insert 那一瞬间存在）
        order.setOrderNo(formatOrderNo(order.getId(), now));
        ordersMapper.updateById(order);

        // 6. 扣库存 —— 整个文件最值钱的一行
        //    ps：0 行就够了，不用再查「为什么没扣成」：那条 UPDATE 的 WHERE 已经同时判断了
        //        存在 / 没删 / 在售 / 库存够，任何一条不满足都是 0 行；再查一遍就成了读-判断-写
        int rows = itemMapper.deductStock(item.getId(), dto.getQuantity());
        if (rows == 0) {
            throw new BusinessException("商品已下架或库存不足");
        }

        // 7. 插 order_item（商品快照）
        //    ps：标题、单价都存快照 —— 卖家之后改价改标题，历史订单要能还原「当时买的是什么、多少钱」
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

        // 8. 插 stock_log 库存流水
        //    ps：流水只用于对账 / 排障，不参与任何判断；并发下 beforeStock 不一定是「恰好那一刻」的值，
        //        只要 delta 对得上账就能核平。W6 回补库存靠 uk_order_item_reason 挡重复
        StockLog stockLog = new StockLog();
        stockLog.setOrderId(order.getId());
        stockLog.setItemId(item.getId());
        stockLog.setDelta(-dto.getQuantity());
        stockLog.setBeforeStock(item.getStock());
        stockLog.setAfterStock(item.getStock() - dto.getQuantity());
        stockLog.setReason(REASON_ORDER);
        stockLogMapper.insert(stockLog);

        // 9. 返回：店名 OrderVO.of 不会填，这里手动补
        //    ps：itemCoverUrl 留 null 是允许的（列可空），前端用占位图 —— W5 不卡这个
        OrderVO vo = OrderVO.of(order, List.of(orderItem));
        vo.setShopName(shop.getName());
        return vo;
    }



    /**
     * 模拟支付（P1 不接真支付，点一下就算付了）。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/pay</code>，买家视角，带 token；没有请求体。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"status":1,"statusText":"待发货","payCents":2000,
     *            "paidAt":"2025-11-19T21:03:11","items":[…]}}
     * </pre>
     *
     * <p><b>会改的数据</b>
     * <ul>
     *   <li>orders：status 0 待付款 → 1 待发货、paid_at = 现在</li>
     *   <li>item：这一单里每件商品的 sold_count += quantity（只有支付成功才加销量）</li>
     * </ul>
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 订单不存在 —— 查不到，或者不是自己的单（两种情况故意不说清，别让人拿 id 试）</li>
     *   <li>400 订单已支付，请勿重复付款</li>
     *   <li>400 订单已超时，请重新下单</li>
     *   <li>400 订单当前状态不可支付 —— 已取消 / 已完成</li>
     * </ul>
     *
     * <p><b>注意</b>
     * <ul>
     *   <li>一条带条件的 UPDATE 同时完成「是我的单 + 待付款 + 未过期」，只看影响行数；0 行才去查库给具体文案。</li>
     *   <li>不把重复支付做成静默幂等：支付类接口静默成功会掩盖真正的重复扣款 bug，宁可报错让用户看到「已经付过了」。</li>
     *   <li>加销量和状态变更在同一个事务里 —— 这里失败会整体回滚，用户再点一次即可。</li>
     * </ul>
     *
     * @param orderId 订单 id；userId 从 token 取
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long userId, Long orderId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 一条条件更新同时完成四个判断：是我的单、待付款、未过期、存在
        //    ps：返回 1 = 抢到了这次支付；0 = 上面任一条件不满足（含并发下被别人抢先）
        //    ps：别先 selectById 判断状态再 update —— 两个请求同时进来会都判断通过、都更新成功
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId)
                .eq(Orders::getStatus, Orders.STATUS_UNPAID)
                .gt(Orders::getExpireAt, now)
                .set(Orders::getStatus, Orders.STATUS_PAID)
                .set(Orders::getPaidAt, now));

        if (rows == 0) {
            // 2. 只在更新失败时才查库，为的是给一句人话；正常路径上不该走到这里
            //    ps：同事务里再查可能还是旧快照（另一笔刚提交的支付你查不到），
            //        所以「已支付」这条文案偶尔会落到「不可支付」分支 —— 可以接受，别为文案完美加锁
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

        // 3. 支付成功才加销量 —— 下单不付款不算「已售」，否则卖家会看到一堆虚假销量
        //    ps：和状态更新在同一个事务里，这里失败整体回滚，用户再点一次即可
        //    ps：setSql 拼进去的是 quantity（1~99 的整数），没有注入面；换成用户能随便传的字符串就必须参数化
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem oi : items) {
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, oi.getItemId())
                    .setSql("sold_count = sold_count + " + oi.getQuantity()));
        }

        // 4. 重新查一次：wrapper 改的是库，内存里的对象还是「待付款」
        //    ps：直接拿旧对象拼 VO，前端会看到「支付成功但状态还是待付款」
        Orders paid = ordersMapper.selectById(orderId);
        OrderVO vo = OrderVO.of(paid, items);
        Shop shop = shopMapper.selectById(paid.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    /**
     * 订单详情：买家看自己的单、店主看自家店的单，其他人一律 404。
     *
     * <p><b>接口</b>：<code>GET /dev-api/order/{id}</code>，带 token；没有请求体。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"orderNo":"OD…","status":0,"statusText":"待付款","shopName":"XX 小店",
     *            "receiverName":"张三","payCents":2000,"expireAt":"2025-11-19T21:20:00","items":[…]}}
     * </pre>
     *
     * <p><b>会改的数据</b>：没有，纯读。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 订单不存在 —— 查不到，或者既不是买家也不是店主</li>
     * </ul>
     *
     * <p><b>注意</b>：不在这里判断超时、也不顺手改状态 —— 列表页一次查 20 单，
     * 每单写一次库就成了「读接口偷偷写数据」。超时只认 expire_at，由 W6 的定时任务统一改。
     *
     * @param orderId 订单 id；userId 从 token 取
     */
    public OrderVO detail(Long userId, Long orderId) {
        // 1. 先按 id 查，查不到就是 404
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        // 2. 只有买家能看自己的单，店主能看自家店的单，其他人一律 404
        //    ps：用 404 不用 403 —— 403 会泄露「这单存在」，别人可以拿 id 一个个试
        Shop shop = shopMapper.selectById(order.getShopId());
        boolean isBuyer = Objects.equals(order.getUserId(), userId);
        boolean isSeller = shop != null && Objects.equals(shop.getOwnerUserId(), userId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(404, "订单不存在");
        }

        // 3. 查子表拼 VO，店名手动补 —— OrderVO.of 不填店名
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        OrderVO vo = OrderVO.of(order, items);
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    // =====================================================================
    // 以下是 W6 的订单生命周期：发货 / 收货 / 取消 / 超时关单 / 订单列表。
    // 契约写在每个方法的 javadoc 里，方法体是编号步骤 + ps（解释这一行为什么这么写）。
    //
    // 状态机（和 Orders 实体、sql/migration-v4-shop.sql 的注释是同一份）：
    //
    //   0 待付款 ──pay───→ 1 待发货 ──ship───→ 2 待收货 ──confirm──→ 3 已完成
    //     │                  │
    //     ├──cancel（买家主动）
    //     └──TIMEOUT（定时任务扫 expire_at）
    //                          ↓
    //                    4 已取消（回补库存 + 写 stock_log）
    //
    // 三条铁律，下面每一次流转都在用：
    //   ① 每一次流转 = 一条带 WHERE status = 旧状态 的 UPDATE，然后看影响行数。
    //      「先 select 判断，再 update」在并发下必然出错（W5 的 pay 已经示范过一遍）。
    //   ② 状态变更和库存回补必须在同一个事务里；回补的幂等靠 stock_log 的唯一键，
    //      不靠「这个接口只会被调一次」这种假设。
    //   ③ 权限判断（买家 / 店主）写进 WHERE 里一起做，而不是查出来再在 Java 里 if ——
    //      查到 update 之间数据可能已经变了。
    // =====================================================================

    /**
     * 发货：把「已付款」的订单改成「已发货」，并写上快递单号和快递公司。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/ship</code>，店主视角（买家调不了），带 token。
     * <pre>
     * 请求体  {"trackingNo":"SF1234567890","shippingCompany":"顺丰"}
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"orderNo":"OD202511190000000012","status":2,"statusText":"待收货",
     *            "shopName":"XX 小店","trackingNo":"SF1234567890","shippingCompany":"顺丰",
     *            "shippedAt":"2025-11-19T21:03:11",
     *            "items":[{"itemTitle":"二手自行车","quantity":1,"payCents":2000}]}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders 一条 —— status 1 → 2、shipped_at = 现在、
     * tracking_no、shipping_company。库存和优惠券都不动（发货不退不扣）。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 你还没有开店 —— 调用者没开过店</li>
     *   <li>404 订单不存在 —— 查不到，或者这单不是你店里的（两种情况故意不说清，别让人拿 id 试）</li>
     *   <li>400 订单还未支付，不能发货 / 订单已发货，请勿重复操作 / 订单已完成 / 订单已取消</li>
     *   <li>400 trackingNo 为空或超 64 字 —— Controller 的 @Valid 就拦住了，进不到这里</li>
     * </ul>
     *
     * <p><b>注意</b>：只有一条 UPDATE，天然原子，<b>不要</b>加 @Transactional。
     * 为了「看起来完整」给每个写方法都加事务，会让人误以为这里有「查 + 写」的复合逻辑。
     *
     * @param dto trackingNo 必填（@NotBlank），shippingCompany 可空
     */
    public OrderVO ship(Long userId, Long orderId, OrderShipDTO dto) {
        // 1. 确认「我有没有店」，顺手把 Shop 对象拿到手
        //    ps：直接调 shopService.requireOwnShop(userId)；没开店它会自己抛 404，别再查一遍 shopMapper
        //    ps：权限是「店主」不是买家——order.user_id 存的是买家，卖家只存在于 shop 表
        //    ps：trackingNo 先 trim()，前端粘贴单号常带空格；shippingCompany 全空格时当没填（trimToNull）
        Shop shop = shopService.requireOwnShop(userId);
        String trackingNo = dto.getTrackingNo().trim();
        String shippingCompany = trimToNull(dto.getShippingCompany());
        LocalDateTime now = LocalDateTime.now();

        // 2. 一条带条件的 UPDATE：status 1 → 2，同时写 shipped_at、tracking_no、shipping_company
        //    ps：shopId 和 status=1 都要放进 WHERE（CAS）。先 selectById 再 updateById 并发下必错：
        //        用户连点两下，两次都能通过检查，发货时间被第二次覆盖

        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getShopId, shop.getId())
                .eq(Orders::getStatus, Orders.STATUS_PAID)          // 1
                .set(Orders::getStatus, Orders.STATUS_SHIPPED)       // 2
                .set(Orders::getShippedAt, now)
                .set(Orders::getTrackingNo, trackingNo)
                .set(Orders::getShippingCompany, shippingCompany)
                // 没有看到 updatedAt 的 @TableField(fill=...)，这里手动写一下；
                // 如果库里是 ON UPDATE CURRENT_TIMESTAMP，删掉这行即可
                .set(Orders::getUpdatedAt, now));
        // 3. rows == 0 时才回查一次，给出一句人话（正常路径走不到这里）
        //    ps：查不到 / 不是自己店的 → 404「订单不存在」（别用 403，403 等于告诉对方「这单存在」）
        //    ps：其余按 status 给 400 的具体文案，见上面「失败情况」
        if (rows == 0) {
            Orders db = ordersMapper.selectById(orderId);
            // 查不到，或者不是自己店里的单 —— 一律 404，别用 403 告诉对方「这单存在」
            if (db == null || !Objects.equals(db.getShopId(), shop.getId())) {
                throw new BusinessException(404, "订单不存在");
            }
            // 到这里说明单是自己的，只是状态不对 —— 按状态给具体文案
            Integer st = db.getStatus();
            if (Integer.valueOf(Orders.STATUS_UNPAID).equals(st)) {
                throw new BusinessException("订单还未支付，不能发货");
            }
            if (Integer.valueOf(Orders.STATUS_SHIPPED).equals(st)) {
                throw new BusinessException("订单已发货，请勿重复操作");
            }
            if (Integer.valueOf(Orders.STATUS_FINISHED).equals(st)) {
                throw new BusinessException("订单已完成");
            }
            // 只剩已取消，但别把兜底写成「已取消」—— 状态以后会加，兜底文案宁可是「当前状态不可发货」
            throw new BusinessException("当前状态不可发货");
        }
        // 4. 重新查订单 + order_item，拼 OrderVO 返回
        //    ps：店名直接塞第 1 步那个 shop.getName()，别为了填店名再查一次库
        //    ps：wrapper 改的是数据库，内存里的对象还是旧值，一定要重新 selectById
        Orders order = ordersMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId));
        OrderVO vo = OrderVO.of(order, items);
        vo.setShopName(shop.getName());   // 店名用第 1 步那个 shop，别再查库
        return vo;

    }

    /**
     * 确认收货：买家把「已发货」的订单确认成「已完成」。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/confirm</code>，无请求体，买家视角，带 token。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"orderNo":"OD202511190000000012","status":3,"statusText":"已完成",
     *            "finishedAt":"2025-11-20T09:12:40",
     *            "items":[{"itemTitle":"二手自行车","quantity":1,"payCents":2000}]}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders 一条 —— status 2 → 3、finished_at = 现在。
     * 不动库存、不动钱（钱在 pay 那一步就已经是模拟收到了）。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 订单不存在 —— 查不到，或者不是我的单</li>
     *   <li>400 订单还未支付 / 卖家还没发货 / 订单已完成 / 订单已取消</li>
     * </ul>
     *
     * <p><b>注意</b>：P1 没有「发货 N 天后自动确认收货」，买家不点就一直停在 2。
     * 钱是模拟的可以先接受，但接真支付之前必须补这条 + 退款 ——
     * 否则买家忘了点，卖家的钱永远到不了账，而且没有任何提示。
     */
    public OrderVO confirm(Long userId, Long orderId) {
        // 1. 一条带条件的 UPDATE：status 2 → 3，写 finished_at
        //    ps：userId 和 status=2 都进 WHERE。少一条就等于「谁都能确认别人的单」
        //    ps：连点两下只有第一下命中，第二下 rows=0
        LocalDateTime now = LocalDateTime.now();
        Integer rows=ordersMapper.update(null,new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId)                       // 买家身份
                .eq(Orders::getStatus, Orders.STATUS_SHIPPED)        // 2 已发货
                .set(Orders::getStatus, Orders.STATUS_FINISHED)      // 3 已完成
                .set(Orders::getFinishedAt, now)
                .set(Orders::getUpdatedAt, now));                    // 和 ship 一致，没有自动填充就手动写)
        // 2. rows == 0 → 回查一次分流
        //    ps：查不到 / 不是自己的 → 404；按 status 给不同的 400 文案（见上面「失败情况」）
        if (rows == 0) {
            Orders db = ordersMapper.selectById(orderId);
            if (db == null || !Objects.equals(db.getUserId(), userId)) {
                throw new BusinessException(404, "订单不存在");
            }
            Integer st = db.getStatus();
            if (Integer.valueOf(Orders.STATUS_UNPAID).equals(st)) {
                throw new BusinessException("订单还未支付");
            }
            if (Integer.valueOf(Orders.STATUS_PAID).equals(st)) {
                throw new BusinessException("卖家还没发货");
            }
            if (Integer.valueOf(Orders.STATUS_FINISHED).equals(st)) {
                throw new BusinessException("订单已完成");
            }
            throw new BusinessException("当前状态不可确认收货");   // 只剩已取消
        }
        // 3. 重新查订单 + order_item，拼 VO
        //    ps：这里手上没有 Shop 对象（不像 ship），店名要 shopMapper.selectById(order.getShopId()) 查一次

        Orders order = ordersMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        OrderVO vo = OrderVO.of(order, items);
        //    ps：confirm 没有 shop 对象（不像 ship 第 1 步），店名得查一次 —— 就一条记录，直接 selectById
        Shop shop = shopMapper.selectById(order.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    /**
     * 取消订单：买家把「待付款」的订单取消掉，并把占着的库存还回去。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/cancel</code>，无请求体，买家视角，带 token。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{
     *            "id":12,"orderNo":"OD202511190000000012","status":4,"statusText":"已取消",
     *            "closedAt":"2025-11-19T21:30:02","closeReason":"BUYER_CANCEL"}}
     * </pre>
     *
     * <p><b>会改的数据</b>
     * <ul>
     *   <li>orders：status 0 → 4、closed_at = 现在、close_reason = BUYER_CANCEL</li>
     *   <li>item：stock += quantity（这单里有几件商品就几条）</li>
     *   <li>stock_log：每件商品插一条 delta = +quantity、reason = CANCEL 的流水</li>
     * </ul>
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 订单不存在 —— 查不到，或者不是我的单</li>
     *   <li>400 订单已付款，暂不支持取消 —— P1 没有退款流程，收了钱不能直接撤单</li>
     *   <li>400 订单已发货，不能取消 / 订单已完成，不能取消 / 订单已取消</li>
     * </ul>
     *
     * <p><b>注意</b>：改状态和回补库存必须在同一个事务，所以有 @Transactional；
     * 重复取消不会重复加库存 —— CAS 保证 rows==1 只出现一次，stock_log 唯一键再兜一次底。
     * 为什么只放开「待付款」：把 status 改成 4 却不退钱，买家付的钱就凭空消失了，
     * 这种「看起来能用、其实对不上账」的功能比没有更危险。前端也按 status 控制按钮。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(Long userId, Long orderId) {
        // 1. 时间只取一次：closed_at 和 updated_at 共用这个 now
        // ps：别在 set 里各调一次 LocalDateTime.now()，两个字段会因为毫秒差变成不同值
        LocalDateTime now = LocalDateTime.now();

        // 2. 一条 CAS UPDATE 定胜负：条件里带 userId 防越权、带 status=0 防重复回补
        // ps：WHERE 里少 userId，谁都能取消别人的单；少 status = 0，取消已付款的单会重复回补库存
        // ps：SET 里写 4 个字段，close_reason 用常量 CLOSE_BUYER_CANCEL，和定时关单的 TIMEOUT 区分开
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId)
                .eq(Orders::getStatus, Orders.STATUS_UNPAID)
                .set(Orders::getStatus, Orders.STATUS_CANCELED)
                .set(Orders::getClosedAt, now)
                .set(Orders::getCloseReason, Orders.CLOSE_BUYER_CANCEL)
                .set(Orders::getUpdatedAt, now));

        // 3. 只有失败路径才回查一次，按状态分流
        // ps：查不到、或者不是我的单，一律 404「订单不存在」—— 别返回 403，那等于告诉别人这单存在
        if (rows == 0) {
            Orders db = ordersMapper.selectById(orderId);
            if (db == null || !Objects.equals(db.getUserId(), userId)) {
                throw new BusinessException(404, "订单不存在");
            }
            Integer st = db.getStatus();
            if (Integer.valueOf(Orders.STATUS_PAID).equals(st)) {
                throw new BusinessException("订单已付款，暂不支持取消");
            }
            if (Integer.valueOf(Orders.STATUS_SHIPPED).equals(st)) {
                throw new BusinessException("订单已发货，不能取消");
            }
            if (Integer.valueOf(Orders.STATUS_FINISHED).equals(st)) {
                throw new BusinessException("订单已完成，不能取消");
            }
            if (Integer.valueOf(Orders.STATUS_CANCELED).equals(st)) {
                throw new BusinessException("订单已取消");
            }
        }

        // 4. 状态确实从 0 翻成 4 了，才回补库存
        // ps：rows==1 只可能出现一次（CAS 保证），天然防重复回补；restoreStock 里还有唯一键兜底
        // ps：回补必须留在本方法的 @Transactional 里 —— 状态改了、库存没回去，账就废了
        if (rows == 1) {
            Orders order = ordersMapper.selectById(orderId);
            restoreStock(order, REASON_CANCEL);
        }

        // 5. 重新查一次拿新状态：第 4 步那个 order 是回补库存时读的，别拿它拼 VO
        Orders fresh = ordersMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId));

        // 6. 拼 VO：明细 + 店名
        OrderVO vo = OrderVO.of(fresh, items);
        Shop shop = shopMapper.selectById(fresh.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;


    }

    /**
     * 我的订单列表：买家看「我买的」，卖家看「我店里卖的」。
     *
     * <p><b>接口</b>：<code>GET /dev-api/order/list?role=buyer&amp;status=1&amp;pageNum=1&amp;pageSize=10</code>，带 token。
     * <ul>
     *   <li>role：buyer（默认）| seller；传别的按 buyer 处理，<b>不要 500</b></li>
     *   <li>status：可空；0/1/2/3/4 精确筛。传 9 这种不存在的值应返回空列表，而不是当成「不筛」把全部订单吐出来</li>
     *   <li>pageNum 至少 1，pageSize 夹在 1..50</li>
     * </ul>
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{
     *            "records":[{"id":12,"orderNo":"OD202511190000000012","status":1,"statusText":"待发货",
     *                        "shopName":"XX 小店","itemCount":1,"payCents":2000,
     *                        "items":[{"itemTitle":"二手自行车","quantity":1,"payCents":2000}]}],
     *            "total":3,"pageNum":1,"pageSize":10,"pages":1}}
     * </pre>
     *
     * <p><b>失败情况</b>：只有 404「你还没有开店」（role=seller 但没开过店）；其余情况都正常返回。
     *
     * <p><b>注意</b>
     * <ul>
     *   <li>列表<b>要带 items</b>：一单只有「OD2025… 待付款 2000 分」，用户根本不知道买的是什么</li>
     *   <li>但要<b>只查这一页、只查一次</b>（一页 10 单总共 3 条 SQL），不许在 for 里逐单查（N+1）</li>
     *   <li>排序按 id desc，<b>不按 created_at</b>：DATETIME 只精确到秒，同一秒下两单时翻页会出现重复或漏条</li>
     * </ul>
     */
    public PageVO<OrderVO> list(Long userId, String role, Integer status, long pageNum, long pageSize) {
        // 1. 先定「看哪一侧」：seller 看自己店里卖的单，其余（buyer / role 为空 / 乱值）看自己买的单
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        if ("seller".equals(role)) {
            // ps：seller 但没开店 → requireOwnShop 自己抛 404「你还没有开店」，这里不用再判
            wrapper.eq(Orders::getShopId, shopService.requireOwnShop(userId).getId());
        } else {
            // ps：写成 "seller".equals(role) 而不是 role.equals("seller")——role 可能是 null，反过来调会 NPE
            wrapper.eq(Orders::getUserId, userId);
        }

        // 2. 状态是可选筛选：不传就不加条件
        if (status != null) {
            // ps：不能无脑 eq(null)，那会拼成 status = NULL，永远 0 行；传 9 这种值让它自然命中 0 行，别特判
            wrapper.eq(Orders::getStatus, status);
        }

        // 3. 排序 + 分页
        // ps：按 id 排而不是 created_at —— DATETIME 只精确到秒，同一秒下两单时翻页会重复或漏条
        // ps：page(...) 是本类私有方法，pageNum 兜到 ≥1、pageSize 夹到 1..50
        wrapper.orderByDesc(Orders::getId);
        Page<Orders> p = ordersMapper.selectPage(page(pageNum, pageSize), wrapper);

        // 4. 明细只查一次：先收集这一页的订单 id，再用一条 IN 把明细全捞回来，按 orderId 分组
        // ps：不许在 for 里逐单查（N+1）—— 一页 10 单会从 2 条 SQL 变成 11 条
        List<Long> ids = p.getRecords().stream().map(Orders::getId).toList();
        List<OrderItem> allItems = ids.isEmpty()
                ? List.<OrderItem>of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, ids)
                        .orderByAsc(OrderItem::getId));
        // ps：ids 为空时不能拿空列表去拼 in ()，有的库直接语法错误，所以上面短路成空 List
        Map<Long, List<OrderItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 5. 店名也批量补：这一页涉及的 shopId 去重后一次查完
        // ps：一页 10 单可能只来自 3 个店，查 3 条就够，不是 10 条
        List<Long> shopIds = p.getRecords().stream()
                .map(Orders::getShopId)
                .distinct()
                .toList();
        Map<Long, String> shopNameMap = shopIds.isEmpty()
                ? Map.<Long, String>of()
                : shopMapper.selectBatchIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Shop::getName));

        // 6. 逐条转 VO，把明细和店名挂上；PageVO.of(Page, Function) 会顺手带上分页信息
        // ps：getOrDefault 兜底 —— 理论上每单都有明细，但历史脏数据不该让整个列表 500
        return PageVO.of(p, o -> {
            OrderVO vo = OrderVO.of(o, itemsMap.getOrDefault(o.getId(), List.of()));
            vo.setShopName(shopNameMap.get(o.getShopId()));
            return vo;
        });
    }

    /**
     * 扫一批「过了付款截止时间、但状态还停在待付款」的订单 id —— 定时任务的第一步，<b>只读</b>。
     *
     * <p><b>被谁调用</b>：OrderTimeoutTask.scan()，默认每分钟一次
     * （{@code forum.order.timeout-scan-interval-ms}）。返回的 id 交给 closeTimeoutOrder 逐单关，
     * <b>这里不许写库</b>。
     *
     * <p><b>返回</b>：订单 id 列表，按 expire_at 升序（先到期的先处理）；没货了就空 List，不是 null。
     *
     * <p><b>注意</b>
     * <ul>
     *   <li>走 idx_status_expire(status, expire_at)，就是给这条查询建的索引</li>
     *   <li>分页拿 limit 条，别用 wrapper.last("LIMIT " + n) 拼字符串</li>
     *   <li>limit ≤ 0 直接 return 空列表（Page 的 size 非法会抛异常）</li>
     *   <li>扫完到关单之间用户可能已经付款了，所以关单时还要再判一次时间</li>
     * </ul>
     */
    public List<Long> findTimeoutOrderIds(int limit) {
        // 1. 挡非法入参：new Page<>(1, 0) 会抛 IllegalArgumentException，别让定时任务炸在这里
        if (limit <= 0) {
            return List.of();
        }

        // 2. 时间只取一次
        LocalDateTime now = LocalDateTime.now();

        // 3. 三个条件正好是 idx_status_expire(status, expire_at) 的索引前缀，别加多余字段
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .select(Orders::getId)
                .eq(Orders::getStatus, Orders.STATUS_UNPAID)
                .lt(Orders::getExpireAt, now)
                .orderByAsc(Orders::getExpireAt);

        // 4. 用 Page 传 limit，别用 wrapper.last("LIMIT " + limit) 拼字符串
        Page<Orders> p = ordersMapper.selectPage(new Page<>(1, limit), wrapper);

        // 5. 没扫到返回空 List，不是 null —— 调用方直接 for 就行
        return p.getRecords().stream().map(Orders::getId).toList();
    }

    /**
     * 关掉一张超时订单（定时任务的第二步）。<b>一单 = 一个事务。</b>
     *
     * <p><b>被谁调用</b>：只给 OrderTimeoutTask 循环调用。
     * <b>⚠️ 不能在 OrderService 内部自己调自己</b> —— 自调用不走 Spring 代理，
     * @Transactional 等于没写，出了问题极难查。
     *
     * <p><b>会改的数据</b>：orders：status 0 → 4、closed_at、close_reason = TIMEOUT；
     * item.stock 回补；stock_log 插 reason = TIMEOUT 的流水。
     *
     * <p><b>注意</b>：WHERE 里除了 status=0，还要带 expire_at &lt; now ——
     * 扫描和关单之间买家可能已经付了钱（付完 status 变 1，这里自然不命中）。
     * 两道条件互相兜底，少任何一边都可能出现「已付款的单被关掉」。
     *
     * @return true = 这一单是本次关掉的；false = 不用关（已付 / 已被别人关了），跳过即可
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTimeoutOrder(Long orderId) {
        // 1. 时间只取一次
        LocalDateTime now = LocalDateTime.now();

        // 2. CAS：status=0 且 expire_at 已过才关，两条条件互相兜底
        // ps：expire_at < now 必须有 —— 扫到关单之间买家可能刚付了钱（status 变 1 就不再命中）
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getStatus, Orders.STATUS_UNPAID)
                .lt(Orders::getExpireAt, now)
                .set(Orders::getStatus, Orders.STATUS_CANCELED)
                .set(Orders::getClosedAt, now)
                .set(Orders::getCloseReason, Orders.CLOSE_TIMEOUT)
                .set(Orders::getUpdatedAt, now));

        // 3. 0 行是正常情况（别人先关了 / 用户刚付款），不抛异常、不记 error
        if (rows == 0) {
            return false;
        }

        // 4. 只有 rows==1 才走到这里，所以每单只回补一次；回补留在本方法事务里
        Orders order = ordersMapper.selectById(orderId);
        restoreStock(order, REASON_TIMEOUT);
        return true;
    }

    /**
     * 把一单里所有商品的库存加回去，并写 stock_log 流水。cancel 和超时关单共用。
     *
     * <p><b>被谁调用</b>：只有 cancel 和 closeTimeoutOrder 调，私有方法，不对外。
     *
     * <p><b>会改的数据</b>：item.stock += quantity；stock_log 每个商品一行
     * （delta = +quantity，reason = CANCEL 或 TIMEOUT，before/after 反推）。
     *
     * <p><b>为什么幂等靠唯一键</b>：uk_order_item_reason(order_id, item_id, reason)，
     * 第二次插同一条流水会抛 DuplicateKeyException → 整个事务回滚 → 库存不会被加两遍。
     * 这也是 reason 要分 CANCEL / TIMEOUT 的原因：人工修数据导致两条路都走过时，
     * 流水不互相挡，账上也看得出是从哪条路进来的。<b>这是设计，别去 catch 掉。</b>
     *
     * <p><b>beforeStock / afterStock 在并发下不是精确快照</b>（和下单时一样），
     * 只要 delta 对得上，账就能核平；要精确就得 SELECT ... FOR UPDATE，P1 不值得。
     *
     * @param order  已经关掉的订单（status 已经是 4 了）
     * @param reason {@link #REASON_CANCEL} 或 {@link #REASON_TIMEOUT}
     */
    private void restoreStock(Orders order, String reason) {
        // 1. 查这单的全部明细（用 order.getId()，别再让调用方多传一个 orderId）
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

        // 2. 逐条回补：先加库存 → 拿回补后的值反推 before → 写流水
        for (OrderItem oi : items) {
            int changed = itemMapper.restoreStock(oi.getItemId(), oi.getQuantity());
            // ps：商品真被物理删了就没人认领；抛异常会让这单永远关不掉（每轮都回滚），宁可少一次回补
            if (changed == 0) {
                log.warn("回补库存失败：orderId={}, itemId={}", order.getId(), oi.getItemId());
                continue;
            }

            // ps：先回补再查，查到的就是 after；before 用它反推，不用再写 SELECT ... FOR UPDATE
            Integer after = itemMapper.selectById(oi.getItemId()).getStock();
            int before = after - oi.getQuantity();

            StockLog stockLog = new StockLog();
            stockLog.setOrderId(order.getId());
            stockLog.setItemId(oi.getItemId());
            stockLog.setDelta(oi.getQuantity());
            stockLog.setBeforeStock(before);
            stockLog.setAfterStock(after);
            stockLog.setReason(reason);
            stockLog.setCreatedAt(LocalDateTime.now());
            // ps：撞 uk_order_item_reason(order_id, item_id, reason) 会抛 DuplicateKeyException，
            //     整个事务回滚 —— 这正是「库存不会被加两遍」的保险，别 try-catch 吞掉
            stockLogMapper.insert(stockLog);
        }
    }

    /** 每页最多 50 条，pageNum 最少 1，和 ItemService / PostService 同一套夹取规则 */
    private static <T> Page<T> page(long pageNum, long pageSize) {
        return new Page<>(Math.max(pageNum, 1), Math.min(pageSize, MAX_PAGE_SIZE));
    }

    /**
     * 生成插入用的临时订单号（正式号由 {@link #formatOrderNo} 在拿到 id 后覆盖）。
     *
     * <p><b>被谁调用</b>：{@link #create}，插 orders 之前。
     *
     * <p><b>为什么要有临时号</b>：正式号是「OD + yyyyMMdd + 10 位补零 id」，需要 id；
     * 而 id 要 INSERT 之后才有，order_no 又是 NOT NULL + 唯一键，不能先插空 ——
     * 所以先塞一个一定唯一的临时号占坑，insert 拿到 id 再回写正式号。
     *
     * <p><b>注意</b>：订单号和 clientToken 撞键都是 DuplicateKeyException，分不清，
     * 所以 create 里统一按「请勿重复提交」提示。"T" 前缀是记号 ——
     * 库里真留下 T 开头的行 = 插入成功但回写失败，可以人工捞出来修。
     */
    private String generateOrderNo() {
        // ps：先占坑，拿到 id 后由 formatOrderNo 覆盖成正式号；"T" 前缀 = 回写失败的记号
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 正式订单号：OD + yyyyMMdd + 10 位补零 id，例：OD202511190000000042（20 位，够 VARCHAR(32)） */
    private String formatOrderNo(Long id, LocalDateTime now) {
        return "OD" + ORDER_NO_FMT.format(now) + String.format("%010d", id);
    }
}
