package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.OrderCreateDTO;
import com.xbcl.personal_forum.pojo.vo.OrderVO;
import com.xbcl.personal_forum.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（W5 下半场）—— 骨架已定，业务在 OrderService 里等你填。
 *
 * <p>完整路径：
 * <pre>
 *   POST /dev-api/order              下单                需 token
 *   GET  /dev-api/order/{id}         订单详情            需 token（买家或卖家）
 *   POST /dev-api/order/{id}/pay     模拟支付            需 token（只能付自己的单）
 * </pre>
 *
 * <p>W5 就这三个。发货 / 确认收货 / 取消 / 订单列表 / 超时关单是 W6，
 * 到时候在这个类里继续加方法就行，Service 里的状态位已经留好了。
 *
 * <p>下单的完整链路（也是验收顺序）：
 * <pre>
 * 1. 注册两个号：卖家 S、买家 B（B 不能是 S 本人，否则会撞"不能买自己店铺的商品"）
 * 2. S 开店 + 发布一件库存 10、单价 1000 分的商品 → itemId
 * 3. B 新增收货地址 → addressId
 * 4. B 下单 {"itemId":..,"quantity":2,"addressId":..,"clientToken":"uuid-a"}
 *    → 200，totalCents = 2000，status = 0
 * 5. GET /item/{itemId} → stock 10 → 8
 * 6. 用<b>同一个</b> clientToken 连点 5 次（并发更真实）
 *    → 只有 1 次成功，其余 400「请勿重复提交」；库存还是 8（不是 0）
 * 7. POST /order/{id}/pay → status 变成 1、paidAt 有值；商品 soldCount +2
 * 8. 再 pay 一次 → 400「订单已支付」
 * 9. 第三个账号 C 调 GET /order/{id} → 404
 * </pre>
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 下单。
     *
     * <pre>
     * POST /dev-api/order
     * Authorization: Bearer xxx
     * {"itemId":7,"quantity":2,"addressId":3,"clientToken":"0f2a9c6e-…-uuid"}
     *
     * 200 {"code":200,"data":{"id":42,"orderNo":"OD20251119…","status":0,
     *        "statusText":"待付款","totalCents":2000,"payCents":2000,
     *        "receiverName":"张三",…,"expireAt":"2025-11-19T16:15:00",
     *        "items":[{"itemTitle":"…","priceCents":1000,"quantity":2}]}}
     *
     * 400 库存不足 / 商品已下架 / 不能购买自己店铺的商品 / 请勿重复提交
     * 404 商品不存在 / 收货地址不存在
     * 401 未登录
     * </pre>
     *
     * <p>clientToken 必须是前端在「点下单」那一刻生成的 UUID，同一次点击的所有重试都复用它。
     * 这是「连点 5 次只生成 1 单」的入口 —— 后端没法替你生成（每次请求都是新的就等于没做）。
     */
    @PostMapping
    public Result<OrderVO> create(@RequestBody @Valid OrderCreateDTO dto,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.create(userId, dto));
    }

    /**
     * 订单详情（买家本人或该店店主可见，其他人 404）。
     *
     * <pre>
     * GET /dev-api/order/42
     * Authorization: Bearer xxx
     *
     * 200 {"code":200,"data":{...同上，多带 shopName 和 items...}}
     * 404 订单不存在 / 与你无关
     * </pre>
     *
     * <p>注意：状态以外的判断（比如"是不是超时了"）前端看 expireAt 自己算，
     * 后端不在这里偷偷改状态。
     */
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.detail(userId, id));
    }

    /**
     * 模拟支付：不接真支付，点一下就把 status 0 → 1。
     *
     * <pre>
     * POST /dev-api/order/42/pay
     * Authorization: Bearer xxx
     * （无请求体）
     *
     * 200 {"code":200,"data":{"status":1,"statusText":"待发货","paidAt":"…"}}
     * 400 订单已支付 / 订单已超时 / 当前状态不可支付
     * 404 订单不存在
     * </pre>
     *
     * <p>真实支付里这个接口要配合支付回调 + 签名校验；P1 先不碰，
     * 但"重复调用不能变成付两次"这点已经按真支付的要求设计了（条件更新 + 明确报错）。
     */
    @PostMapping("/{id}/pay")
    public Result<OrderVO> pay(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.pay(userId, id));
    }
}
