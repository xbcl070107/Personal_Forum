package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.OrderCreateDTO;
import com.xbcl.personal_forum.pojo.dto.OrderShipDTO;
import com.xbcl.personal_forum.pojo.vo.OrderVO;
import com.xbcl.personal_forum.pojo.vo.PageVO;
import com.xbcl.personal_forum.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（W5 下单/支付已实现，W6 生命周期是骨架）。
 *
 * <p>完整路径：
 * <pre>
 *   POST /dev-api/order              下单                需 token          （已实现）
 *   GET  /dev-api/order/{id}         订单详情            需 token（买家或卖家）（已实现）
 *   POST /dev-api/order/{id}/pay     模拟支付            需 token（只能付自己的单）（已实现）
 *   POST /dev-api/order/{id}/ship    发货                需 token（该店店主）   ← W6 骨架
 *   POST /dev-api/order/{id}/confirm 确认收货            需 token（买家本人）   ← W6 骨架
 *   POST /dev-api/order/{id}/cancel  取消订单（未付款）   需 token（买家本人）   ← W6 骨架
 *   GET  /dev-api/order/list         订单列表            需 token              ← W6 骨架
 * </pre>
 *
 * <p>这一层只做三件事：取 userId、转给 Service、包 {@link Result}。
 * 每个接口的契约（入参、返回形状、会改哪些表、失败码）写在各方法的 javadoc 里；
 * 怎么实现写在 {@link OrderService} 的方法体里（编号 todo + ps）。
 *
 * <p>W6 那四个接口的返回值已经定好了，但 Service 里还是 501 —— 现在调它们会得到
 * {@code {"code":501,"message":"W6 未实现：…"}}。按 OrderService 里每个方法的
 * javadoc 契约 + 方法体编号 todo 填完就通，这一层基本不用再动。
 *
 * <p>为什么 /order/list 不会被 /order/{id} 抢走：Spring 匹配路由时字面量段优先于
 * 路径变量，{@code list} 比 {@code {id}} 更"具体"。所以这两个不冲突，
 * 但前提是 id 是数字（{@code GET /order/abc} 会先命中 {id}，再在类型转换时 400）。
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
 * 10.（W6）S 调 ship → status 2、trackingNo 有值；B 调 confirm → status 3
 * 11.（W6）另开一单不付款，把 expire_at 改到过去，等定时任务 → status 4、库存回补
 * </pre>
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 下单：把选中的商品锁进一笔「待付款」订单，并立刻扣库存。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order</code>，带 token。
     * <pre>
     * 请求体  {"itemId":7,"quantity":2,"addressId":3,"clientToken":"0f2a9c6e-…-uuid"}
     *          四个字段都必填；clientToken 是前端点「下单」那一刻生成的 UUID，重试复用同一个
     * 成功    {"code":200,"msg":"ok","data":{"id":42,"orderNo":"OD20251119…","status":0,
     *           "statusText":"待付款","totalCents":2000,"payCents":2000,
     *           "receiverName":"张三","expireAt":"2025-11-19T16:15:00",
     *           "items":[{"itemTitle":"…","priceCents":1000,"quantity":2}]}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders 新增 1 行（status = 0）、order_item 快照、
     * item.stock 扣减、stock_log 记一笔负数。逐条细节见 {@link OrderService#create}。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 库存不足 / 商品已下架 / 不能购买自己店铺的商品 / 请勿重复提交</li>
     *   <li>404 商品不存在 / 收货地址不存在</li>
     *   <li>401 未登录 —— JwtInterceptor 拦在外面，进不到这里</li>
     * </ul>
     *
     * <p><b>注意</b>：clientToken 后端没法替你生成 —— 每次请求都新生成一个，就等于没做幂等。
     */
    @PostMapping
    public Result<OrderVO> create(@RequestBody @Valid OrderCreateDTO dto,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.create(userId, dto));
    }

    /**
     * 订单详情：买家本人能看到自己买的，店主能看到自己店里卖的，其他任何人都是 404。
     *
     * <p><b>接口</b>：<code>GET /dev-api/order/{id}</code>，带 token；没有请求体。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{…同下单，多带 shopName 和 items…}}
     * </pre>
     *
     * <p><b>会改的数据</b>：没有，纯读。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 订单不存在 / 与你无关 —— 两种情况都回 404，不告诉对方「这单存在但你没权限」</li>
     * </ul>
     *
     * <p><b>注意</b>：读接口不偷偷改状态。前端想知道「是不是超时了」，
     * 拿 expireAt 自己算；真正的关单由 W6 的定时任务统一做。
     */
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.detail(userId, id));
    }

    /**
     * 模拟支付：不接真支付，点一下就把 status 0 → 1。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/pay</code>，带 token；没有请求体。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{"status":1,"statusText":"待发货",
     *           "paidAt":"2026-11-21T13:02:00"}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders.status 0 → 1、orders.paid_at = 现在；
     * item.sold_count += 本单数量（每件商品各加各的）。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 订单已支付 / 订单已超时 / 当前状态不可支付</li>
     *   <li>404 订单不存在（不是自己的单也走 404）</li>
     * </ul>
     *
     * <p><b>注意</b>：真实支付要配合支付回调 + 签名校验，P1 先不碰；
     * 但「重复调用不能变成付两次」这点已经按真支付的要求设计（条件更新 + 明确报错）。
     */
    @PostMapping("/{id}/pay")
    public Result<OrderVO> pay(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.pay(userId, id));
    }

    // =====================================================================
    // 以下是 W6 的骨架（订单生命周期）：发货 / 确认收货 / 取消 / 订单列表。
    // 路径、入参、返回类型都定好了，业务在 OrderService 里（现在是 501），
    // 那边按 javadoc 契约填方法体，这一层基本不用再动。
    // =====================================================================

    /**
     * 发货：店主把自己店里已付款的单标成「已发货」，并登记快递单号。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/ship</code>，带 token（必须是这笔订单所属店的店主）。
     * <pre>
     * 请求体  {"trackingNo":"SF1234567890","shippingCompany":"顺丰"}
     *          trackingNo 必填、最长 64；shippingCompany 可选、最长 32
     * 成功    {"code":200,"msg":"ok","data":{"id":42,"status":2,"statusText":"待收货",
     *           "trackingNo":"SF1234567890","shippingCompany":"顺丰",
     *           "shippedAt":"2026-11-21T13:02:00","items":[...]}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders.status 1 → 2、shipped_at = 现在、
     * tracking_no / shipping_company 写入请求体里的值。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 订单还未支付，不能发货 / 订单已发货，请勿重复操作 / 订单已完成 / 订单已取消</li>
     *   <li>404 订单不存在（不是自己店的单也走 404）/ 你还没有开店</li>
     * </ul>
     *
     * <p><b>注意</b>：trackingNo 必填 —— 买家看到「已发货」之后唯一的动作就是查物流，
     * 没有单号这个状态没有意义。P1 只存不校验（不调快递 API）。
     */
    @PostMapping("/{id}/ship")
    public Result<OrderVO> ship(@PathVariable Long id,
                                @RequestBody @Valid OrderShipDTO dto,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.ship(userId, id, dto));
    }

    /**
     * 确认收货：买家本人点「我收到了」，订单走完。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/confirm</code>，带 token（必须是这笔订单的买家）。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{"status":3,"statusText":"已完成",
     *           "finishedAt":"2026-11-21T13:05:00"}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders.status 2 → 3、finished_at = 现在。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 订单还未支付 / 卖家还没发货 / 订单已完成 / 订单已取消</li>
     *   <li>404 订单不存在（不是自己的单也走 404）</li>
     * </ul>
     *
     * <p><b>注意</b>：P1 没有自动确认收货，也没有退款：买家不点，订单会一直停在待收货。
     */
    @PostMapping("/{id}/confirm")
    public Result<OrderVO> confirm(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.confirm(userId, id));
    }

    /**
     * 取消订单（只支持「未付款」，会回补库存）。
     *
     * <p><b>接口</b>：<code>POST /dev-api/order/{id}/cancel</code>，带 token（必须是这笔订单的买家）。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":{"status":4,"statusText":"已取消",
     *           "closedAt":"2026-11-21T13:10:00","closeReason":"BUYER_CANCEL"}}
     * </pre>
     *
     * <p><b>会改的数据</b>：orders.status 0 → 4、closed_at = 现在、close_reason = BUYER_CANCEL；
     * item.stock 按 order_item 的数量加回去；stock_log 记一笔正数回补。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 订单已付款，暂不支持取消 / 订单已发货，不能取消 / 订单已完成，不能取消 / 订单已取消</li>
     *   <li>404 订单不存在（不是自己的单也走 404）</li>
     * </ul>
     *
     * <p><b>注意</b>：为什么不支持取消已付款 —— P1 没有退款。状态改成「已取消」但钱不退，
     * 是最坑的一类 bug：前端看着一切正常，账永远对不上。等退款流程做完再放开。
     */
    @PostMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.cancel(userId, id));
    }

    /**
     * 订单列表：买家看自己买的，卖家看自己店里卖的。
     *
     * <p><b>接口</b>：<code>GET /dev-api/order/list</code>，带 token。
     * <pre>
     * query   role=buyer|seller   buyer（默认）—— 不传或传别的都按 buyer
     *         status=0..4         可空，按状态筛
     *         pageNum=1           默认 1
     *         pageSize=10         默认 10，服务端最多 50
     * 成功    {"code":200,"msg":"ok","data":{
     *           "records":[{"id":42,"orderNo":"OD20261121…","status":0,"statusText":"待付款",
     *                       "totalCents":2000,"payCents":2000,"expireAt":"…",
     *                       "items":[{"itemTitle":"…","quantity":2}],"shopName":"冒险者杂货铺"}],
     *           "total":12,"pageNum":1,"pageSize":10,"pages":2}}
     * </pre>
     *
     * <p><b>会改的数据</b>：没有，纯读。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 你还没有开店 —— 只有 role=seller 且自己没店时会遇到</li>
     * </ul>
     *
     * <p><b>注意</b>：status 传一个不存在的数字（比如 9）不报错，返回空列表 ——
     * 筛选条件只是命中 0 行，这比让前端下拉框的笔误变成 500 合理。
     */
    @GetMapping("/list")
    public Result<PageVO<OrderVO>> list(@RequestParam(required = false) String role,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(orderService.list(userId, role, status, pageNum, pageSize));
    }
}
