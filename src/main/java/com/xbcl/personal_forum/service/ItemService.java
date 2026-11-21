package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.mapper.OrdersMapper;
import com.xbcl.personal_forum.mapper.ShopMapper;
import com.xbcl.personal_forum.pojo.dto.ItemPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Category;
import com.xbcl.personal_forum.pojo.entity.Item;
import com.xbcl.personal_forum.pojo.entity.Shop;
import com.xbcl.personal_forum.pojo.vo.ItemVO;
import com.xbcl.personal_forum.pojo.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品业务。W2 已完成：发布、我发布的。W3 骨架：集市列表、详情、上下架
 * （签名与契约都定好了，方法体按下方编号步骤填）。
 *
 * <p>两件事值得记住：
 * <ul>
 *   <li>shopId <b>不由前端传</b>：由当前用户查出他唯一的那家店。
 *       前端传 shopId 等于允许把商品挂到别人店里</li>
 *   <li>images 在实体里是 JSON 字符串，VO 里是 List。转换只在这两个边界发生，
 *       中间层不碰 —— 否则同一个字段在三个地方有三种类型，谁都要猜现在它是哪种</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemMapper itemMapper;

    /** 发布时要校验商品分类真的存在且是 ITEM，所以依赖 category 表 */
    private final CategoryMapper categoryMapper;

    /** 拿到当前用户名下唯一的那家店 */
    private final ShopService shopService;

    /**
     * 用来把 images:List 写成 JSON 字符串、把库里的字符串读成 List。
     *
     * <p>Spring Boot 已经自动配了一个 ObjectMapper bean，这里直接注入，
     * 不要 new：new 出来的那份不认 application.yaml 里的 Jackson 配置，
     * 以后项目改了日期格式/命名策略，两处的行为就不一样了。
     *
     * <p><b>注意包名是 {@code tools.jackson.databind}，不是 {@code com.fasterxml.jackson.databind}。</b>
     * Spring Boot 4 已经换成 Jackson 3，容器里只自动配置 Jackson 3 的这一份；
     * Jackson 2 的 jar 还躺在 classpath 上（jjwt-jackson 带进来的），
     * 但容器里没有它的 bean —— 注入它，启动时直接报
     * "required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"。
     */
    private final ObjectMapper objectMapper;

    /**
     * 列表/详情要回填商品所属的店名（ItemVO.shopName）。
     *
     * <p>这里注入的是 Mapper 而不是 ShopService，是因为 W3 列表需要
     * {@code selectBatchIds} 一次把 20 件商品涉及的店全查出来；
     * ShopService 现在只有按单个 id 查的方法，逐条调就是 N+1。
     */
    private final ShopMapper shopMapper;

    /**
     * W6 才注入：下架前要数「这件商品还有几笔没走完的订单」。
     *
     * <p>用 Mapper 而不是 OrderService，是为了不把 ItemService ↔ OrderService
     * 变成循环依赖（OrderService 已经注入了 ItemMapper 去扣库存，虽然那是 Mapper 不是 Service，
     * 但两个 Service 互相注入这种结构最好一开始就别养成）。
     */
    private final OrdersMapper ordersMapper;

    /**
     * W7 才注入：商品详情的缓存读写 + 浏览量计数。
     *
     * <p>拆成一个独立 Service 而不是在这里直接 {@code @Autowired StringRedisTemplate}：
     * 详情缓存是「一个方法里插三步」的改造，如果 Redis 命令直接写在 detail 里，
     * 这个方法会同时负责"给谁看"和"怎么存"，两边一改就互相干扰。
     */
    private final ItemCacheService itemCacheService;

    /** 每页最多 50 条，和 PostService 同一套夹取规则 */
    private static final long MAX_PAGE_SIZE = 50;

    private static <T> Page<T> page(long pageNum, long pageSize) {
        return new Page<>(Math.max(pageNum, 1), Math.min(pageSize, MAX_PAGE_SIZE));
    }

    /**
     * 发布商品。
     *
     * @param userId 卖家，由 Controller 从 token 里取 —— 不放 DTO，
     *               前端传了也不认
     * @return 新商品 id
     */
    public Long publish(Long userId, ItemPublishDTO dto) {

        // 1. 卖家必须已经开过店。没店就发商品的话，item.shop_id 只能填 0 或 null，
        //    集市列表里这件商品永远没人认领
        Shop shop = shopService.requireOwnShop(userId);

        // 2. 分类校验：存在 + 必须是 ITEM。
        //    只判存在的话，前端可以传帖子板块的 id（比如"技术"），
        //    于是商品挂在"技术"下面 —— 集市的分类 tab 里永远筛不到它
        if (dto.getCategoryId() != null) {
            Category category = categoryMapper.selectById(dto.getCategoryId());
            if (category == null || !Category.SCOPE_ITEM.equals(category.getScope())) {
                throw new BusinessException("商品分类不存在");
            }
        }

        // 3. 拼实体。status 显式写 1（上架）：
        //    数据库 DEFAULT 也是 1，但那样"发出来就是上架"的规则只藏在建表语句里，
        //    显式写出来，以后加审核环节的人一眼能看到入口在哪
        Item item = new Item();
        item.setShopId(shop.getId());
        item.setCategoryId(dto.getCategoryId());
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription());
        item.setPriceCents(dto.getPriceCents());
        item.setStock(dto.getStock());
        item.setSoldCount(0);                       // 新商品还没卖出任何一件
        item.setImagesJson(toJson(dto.getImages())); // List → JSON 字符串
        item.setStatus(Item.STATUS_ON);

        itemMapper.insert(item);

        return item.getId();
    }

    /**
     * 我发布的商品（GET /item/mine），分页。
     *
     * <p>返回的是<b>全部</b>商品，包括下架的：这是卖家自己的工作台，
     * 下架的商品要是看不见，就没法重新上架了。
     *
     * <p>和集市列表（W3）的区别就在这一条 status 条件上。
     */
    public PageVO<ItemVO> mine(Long userId, long pageNum, long pageSize) {

        // 没开过店的人也不该看到别的：没有店就没有商品。
        // 这里不抛 404 —— "我的商品页是空的"比"你还没开店"更适合前端直接渲染
        Shop shop = shopService.findByOwner(userId);
        if (shop == null) {
            Page<Item> empty = page(pageNum, pageSize);
            empty.setRecords(List.of());
            return PageVO.of(empty, item -> ItemVO.of(item, List.of()));
        }

        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getShopId, shop.getId())
                // 上架的排前面，同状态里新的排前面：卖家最想看的是"刚发的"
                .orderByDesc(Item::getStatus)
                .orderByDesc(Item::getId);

        Page<Item> result = itemMapper.selectPage(page(pageNum, pageSize), wrapper);

        return PageVO.of(result, item -> {
            ItemVO vo = ItemVO.of(item, fromJson(item.getImagesJson()));
            vo.setShopName(shop.getName());
            return vo;
        });
    }

    /**
     * 把图片列表序列化成存库的字符串。
     *
     * <p>NULL 和空列表都存 NULL：省得一列里出现 "null"、""、[]" 三种"没有图"
     * 的表示，读的时候还要逐个判断。JSON 序列化最容易踩的坑就是
     * 把 null 写成字符串 "null" —— 读回来 fromJson 里也防着这一手。
     */
    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            // 走到这里说明 List<String> 都能序列化失败，是程序 bug 不是用户输入问题
            throw new BusinessException(500, "图片地址保存失败");
        }
    }

    /**
     * 把库里的字符串解析回图片列表。永不返回 null，出问题返回空列表。
     *
     * <p>为什么这里把异常吞掉只打日志：这一列是"展示用"的附属数据，
     * 某一行曾经被谁手工写坏过，不该让整个列表接口 500。
     * 坏掉的那一件商品显示成"没图"，其他正常商品照常展示。
     * （注意：publish 那边是反过来的 —— 写不进去要报错，免得静默存坏数据。）
     */
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("item.images_json 解析失败，按无图处理：{}", json);
            return List.of();
        }
    }

    // =====================================================================
    // 以下是 W3 的骨架。方法签名、返回类型、错误码都定好了，
    // 方法体按编号步骤注释 —— 每一步都能单独跑出来看结果。
    //
    // 通用套路（列表/详情都要用）：
    //   查实体 → 转 VO → 回填 shopName。
    // VO 之所以不直接查库，是因为它要能被序列化成前端认识的形状；
    // 实体里是 imagesJson 字符串，VO 里是 List，转换只在 ItemVO.of 里发生。
    // =====================================================================

    /**
     * 集市列表（GET /item/list）。
     *
     * <p>入参全部可空（Controller 传默认值），约定：
     * <ul>
     *   <li>keyword：标题模糊，空串/空白等于没传</li>
     *   <li>categoryId：分类精确匹配</li>
     *   <li>shopId：只看某一家店的商品（W4 店铺主页复用它，不另开接口）</li>
     *   <li>minPriceCents / maxPriceCents：价格区间，闭区间，单位"分"</li>
     *   <li>sort：new（默认）| priceAsc | priceDesc | hot</li>
     * </ul>
     *
     * <p>返回：{@code PageVO<ItemVO>}，records 里每个 ItemVO 都要有 shopName。
     *
     * <p>验收：库里插两件商品，一件 status=1 一件 status=0，
     * 集市列表只返回 status=1 的那件，并且 shopName 不是 null。
     */
    public PageVO<ItemVO> listMarket(long pageNum, long pageSize,
                                     String keyword, Long categoryId, Long shopId,
                                     Long minPriceCents, Long maxPriceCents,
                                     String sort) {

        // 步骤 1：条件拼装（用 LambdaQueryWrapper，别手写 SQL 字符串）
        //   起手式：wrapper.eq(Item::getStatus, Item.STATUS_ON)
        //   —— "集市只卖上架的" 是这个方法存在的理由，第一行就写上
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getStatus, Item.STATUS_ON);
        //
        // 步骤 2：keyword —— wrapper.like(...)，注意先 trim、空串跳过
        //   提示：org.springframework.util.StringUtils.hasText(x)
        //
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Item::getTitle, keyword.trim());
        }
        // 步骤 3：categoryId / minPriceCents / maxPriceCents —— eq / ge / le
        //   每个条件都要带"参数为 null 就不加这个条件"的判断，
        //   LambdaQueryWrapper 的 xxx(boolean condition, ...) 重载就是干这个的
        //
        wrapper.eq(categoryId != null, Item::getCategoryId, categoryId)
                // W4：店铺主页就是给这一行加个条件 —— 同一个方法，多一个筛选维度，
                // 不另开 /shop/{id}/items。店里的商品 = 按 shopId 过滤的集市列表，
                // 因为上面第一行已经把"只要上架的"卡死了，正好符合"店里只摆卖的货"
                .eq(shopId != null, Item::getShopId, shopId)
                .ge(minPriceCents != null, Item::getPriceCents, minPriceCents)
                .le(maxPriceCents != null, Item::getPriceCents, maxPriceCents);
        // 步骤 4：排序 —— switch(sort)，把前端字符串映射成 wrapper.orderByXxx
        //   new       → orderByDesc(Item::getId)
        //   priceAsc  → orderByAsc(Item::getPriceCents).orderByDesc(Item::getId)
        //   priceDesc → orderByDesc(Item::getPriceCents).orderByDesc(Item::getId)
        //   hot       → orderByDesc(Item::getSoldCount).orderByDesc(Item::getId)
        //   其它/空   → 同 new（前端传了怪值不该 500）
        //   ⚠️ 不要用 wrapper.last("order by " + sort)：那是字符串拼 SQL，能注入
        String s = sort == null ? "" : sort;
        switch (s) {
            case "priceAsc":
                wrapper.orderByAsc(Item::getPriceCents).orderByDesc(Item::getId);
                break;
            case "priceDesc":
                wrapper.orderByDesc(Item::getPriceCents).orderByDesc(Item::getId);
                break;
            case "hot":
                wrapper.orderByDesc(Item::getSoldCount).orderByDesc(Item::getId);
                break;
            case "new":
            default:
                // 空 / 怪值都走 new，避免 500
                wrapper.orderByDesc(Item::getId);
                break;
        }
        //
        // 步骤 5：分页查询 —— itemMapper.selectPage(page(pageNum, pageSize), wrapper)
        //   page(...) 这个私有方法已经帮你夹取了 1 <= pageNum、pageSize <= 50
        //
        Page<Item> result = itemMapper.selectPage(page(pageNum, pageSize), wrapper);
        // 步骤 6：回填 shopName（这一步最容易写出 N+1）
        //   错误写法：PageVO.of(result, item -> { /* 每条 selectById 一次店 */ })
        //   正确写法：先收集 distinct shopId → shopMapper.selectBatchIds(ids)
        //             → 转 Map<Long, String> → 再在 lambda 里 map.get(item.getShopId())
        //   提示：result.getRecords().stream().map(Item::getShopId).distinct().toList()
        //
        List<Long> shopIds = result.getRecords().stream()
                .map(Item::getShopId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> shopNameMap;
        if (shopIds.isEmpty()) {
            shopNameMap = Collections.emptyMap();
        } else {
            shopNameMap = shopMapper.selectBatchIds(shopIds).stream()
                    .collect(Collectors.toMap(
                            Shop::getId,
                            shop -> shop.getName() == null ? "" : shop.getName(),
                            (a, b) -> a          // 防御性 merge，避免重复 key 抛异常
                    ));
        }
        // 步骤 7：return PageVO.of(result, item -> {...})
        //   里面：ItemVO.of(item, fromJson(item.getImagesJson())) + setShopName
        //
        return PageVO.of(result, item -> {
            ItemVO vo = ItemVO.of(item, fromJson(item.getImagesJson()));
            vo.setShopName(shopNameMap.get(item.getShopId()));
            return vo;
        });

    }

    /**
     * 商品详情（W3 已实现，W7 加缓存）。
     *
     * <p><b>接口</b>：<code>GET /dev-api/item/{id}</code>，带 token。
     * <pre>
     * 成功  {"code":200,"message":"ok","data":{
     *          "id":7,"shopId":3,"shopName":"xx的小店","title":"九成新键盘",
     *          "priceCents":12000,"stock":5,"soldCount":2,"viewCount":37,
     *          "images":["/dev-api/uploads/images/2026-11/a.jpg"],
     *          "status":1,"createdAt":"2026-11-21T10:00:00","updatedAt":"..."}}
     * </pre>
     *
     * <p><b>W7 加了什么</b>：先查 Redis，命中就直接返回（日志里能看出 hit）；
     * 未命中才走 {@link #loadDetailFromDb} 查库。查库确认不存在时写 60 秒空值标记；
     * 在售商品回源后写缓存；每次成功浏览在 Redis 里 +1，由定时任务批量落库。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 商品不存在</li>
     *   <li>404 商品已下架，且当前用户不是店主</li>
     * </ul>
     *
     * <p><b>注意</b>：下架商品<b>不进正文缓存</b>。缓存是"谁读都是同一份"，
     * 而下架商品只有店主能看 —— 缓存一份就等于让游客也拿到了答案。
     */
    public ItemVO detail(Long itemId, Long viewerUserId) {
        // 1. 缓存优先：一次 Redis 往返，命中就直接返回
        ItemCacheService.CacheHit hit = itemCacheService.getDetail(itemId);
        if (hit.hit()) {
            // 命中空值标记：库里已经确认没有这件商品
            if (hit.vo() == null) {
                throw new BusinessException(404, "商品不存在");
            }
            // 命中正文：缓存里只会有在售商品，谁读都一样，不用判权限
            // ps：debug 不用 info —— 详情页是热接口，info 会刷屏；
            //     验收时把 logging.level.com.xbcl.personal_forum.service.ItemCacheService 调成 debug
            log.debug("商品详情缓存命中：id={}", itemId);
            itemCacheService.incrView(itemId);
            return hit.vo();
        }

        // 2. 未命中 → 回源查库
        ItemVO vo = loadDetailFromDb(itemId, viewerUserId);
        if (vo == null) {
            // 2.1 库里根本没有：写空值标记，防止同一个不存在的 id 把库打穿
            // ps："有但不可见"（下架且不是店主）走不到这里 —— loadDetailFromDb 自己抛 404。
            //     那种情况绝对不能进缓存：缓存是所有人共读的，下架只对店主可见
            itemCacheService.putNull(itemId);
            throw new BusinessException(404, "商品不存在");
        }

        // 3. 回源成功、且是在售的，才写缓存
        // ps：下架商品 loadDetailFromDb 也会返回（给店主看），但缓存一份就等于让游客也看到了
        if (Objects.equals(vo.getStatus(), Item.STATUS_ON)) {
            itemCacheService.putDetail(vo);
        }

        // 4. 记一次浏览
        // ps：放在最后。404 的路走不到这里，浏览量才不会把不存在的 id 也算进去
        itemCacheService.incrView(itemId);

        // 5. 返回
        return vo;
    }

    /**
     * 商品详情的"回源"路径：查库 + 判可见性 + 拼 VO。
     *
     * <p>从 W3 的 {@code detail} 原样搬出来，只改了名字。
     * 这样"缓存怎么用"全在 {@link #detail}，"一次干净的数据库查询"全在这里 ——
     * 这个方法不碰 Redis，可以单独测。
     *
     * <p><b>被谁调用</b>：{@link #detail}（缓存未命中时）。不要让别的 Service 直接调。
     *
     * <p><b>返回 null 的含义</b>：库里<b>根本没有</b>这件商品（包括被逻辑删除的）。
     * 调用方要负责把它变成 404、顺便写空值缓存 —— 这个方法自己不抛 404，
     * 因为"要不要缓存这个 404"是调用方的策略，不是查询的事。
     */
    private ItemVO loadDetailFromDb(Long itemId, Long viewerUserId) {
        // 1. 查商品：查不到就返回 null（不是抛 404，理由见方法头）
        //    ps：GlobalExceptionHandler 把 code 当 HTTP 状态码 —— 真正抛出去的 404
        //        是真的 404，不是 200 里包一个 code=404
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            return null;
        }
        Shop shop = shopMapper.selectById(item.getShopId());
        // 2. 下架商品只有店主能看
        //    ps：用 404 不用 403 —— 403 等于告诉游客"这商品存在，只是下架了"
        //    ps：shop 为 null（数据被硬删过）时按"不是店主"处理，别让 NPE 变成 500
        Integer st = item.getStatus();
        if (st == null || st != Item.STATUS_ON) {
            if (shop == null
                    || !Objects.equals(shop.getOwnerUserId(), viewerUserId)) {
                throw new BusinessException(404, "商品不存在");
            }
        }
        // 3. 拼 VO + 补店名
        //    ps：详情只有一条，selectById 就够，这里不存在列表那种 N+1 问题
        //    ps：店可能已经关了，店名照样给 —— 已成交订单以后还要展示它
        ItemVO vo = ItemVO.of(item, fromJson(item.getImagesJson()));
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        return vo;
    }

    /**
     * 上架 / 下架一件商品。下架前会先确认它没有没走完的订单。
     *
     * <p><b>接口</b>：<code>PATCH /dev-api/item/{id}/status</code>，带 token。
     * <pre>
     * 请求体  {"status":0}          0 下架 / 1 上架（DTO 已校验，进到方法里只有这两个值）
     * 成功    {"code":200,"msg":"ok","data":{"id":7,"status":0,"statusText":"已下架"}}
     *          前端拿返回的 VO 就地刷新那一行，不用再发一次详情请求
     * </pre>
     *
     * <p><b>会改的数据</b>：item.status 0 ↔ 1。目标状态本来就是当前状态时直接返回，不写库。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 还有 N 笔订单没完成，不能下架</li>
     *   <li>403 只能改自己店里的商品</li>
     *   <li>404 商品不存在</li>
     * </ul>
     *
     * <p><b>注意</b>：只在「下架」时数订单（status 是 0 待付款或 1 待发货），上架永远放行。
     * 为什么这么设计，见方法体第 4 步的 ps。
     */
    public ItemVO changeStatus(Long userId, Long itemId, Integer status) {
        // 1. 查商品，查不到就 404
        //    ps：先 404 再判归属 —— 商品不存在和「存在但不是你的」要能分开回
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(404, "商品不存在");
        }
        // 2. 判归属：这件商品的店是不是当前用户的
        //    ps：一律用 Objects.equals，不用 ==（Long 超过 127 时 == 比的是引用，会随机放行或拦错）
        //    ps：店也是 null（数据被硬删过）时按「不是你的」处理，别让 NPE 变成 500
        Shop shop = shopMapper.selectById(item.getShopId());
        if (shop == null || !Objects.equals(shop.getOwnerUserId(), userId)) {
            throw new BusinessException(403, "只能改自己店里的商品");
        }
        // 3. 幂等：目标状态和当前一样就直接返回，别白写一次库
        //    ps：前端网络重试、用户连点两下都会重复提交；第二次不该报错，也不该白改 updated_at
        if (Objects.equals(item.getStatus(), status)) {
            ItemVO vo = ItemVO.of(item, fromJson(item.getImagesJson()));
            vo.setShopName(shop.getName());
            return vo;
        }
        // 4. 下架前拦一道：还有没走完的订单，就不许下架
        //    ps：买家可能已经付了钱，或者正卡在「待付款」倒计时里。卖家这时候把商品一撤，
        //        这单要么得照常发货（商品都从集市上消失了），要么得退款（P1 没有退款流程），
        //        两个后果都很糟，所以直接拒绝，让卖家先把那几笔处理掉
        //    ps：只拦下架（目标 STATUS_OFF），上架永远放行
        //    ps：这是「先查再写」，但这里可以接受 —— 下架本身幂等，两个请求同时通过、都 update，结果一样；
        //        最坏是「刚下单 + 刚好下架」的窗口，由 OrderService 下单时的条件扣库存兜着（扣不到就下单失败）
        if (Objects.equals(status, Item.STATUS_OFF)) {
            long unfinished = ordersMapper.countUnfinishedByItem(itemId);
            if (unfinished > 0) {
                throw new BusinessException("还有 " + unfinished + " 笔订单没完成，不能下架");
            }
        }
        // 5. 改状态
        //    ps：updateById 会按逻辑删除自动补 is_deleted = 0，删掉的商品改不动，正好
        item.setStatus(status);
        itemMapper.updateById(item);
        // 6. 删详情缓存（W7）
        //    ps：下架必须立刻生效 —— 集市列表已经查不到它了，详情页却还能打开，就是越权
        //    ps：只删"详情"这一类，库存相关的缓存不在这里删（见 docs/W7 的一致性策略：
        //        库存展示允许滞后 TTL，下单以 deductStock 的条件更新为准）
        itemCacheService.evict(itemId);
        // 6. 返回改完的 VO
        //    ps：shopName 用第 2 步查到的 shop，别再查一次库
        ItemVO vo = ItemVO.of(item, fromJson(item.getImagesJson()));
        vo.setShopName(shop.getName());
        return vo;
    }
    
}
