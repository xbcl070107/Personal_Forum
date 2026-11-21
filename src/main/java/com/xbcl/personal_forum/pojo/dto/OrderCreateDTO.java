package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 下单请求体（W5）。
 *
 * <p>请求体长这样：
 * <pre>
 * {"itemId":7,"quantity":1,"addressId":3,"clientToken":"0f2a9c6e-…-uuid"}
 * </pre>
 *
 * <p>里面<b>没有</b>这几个东西，每个都是故意的：
 * <ul>
 *   <li>没有 shopId —— 店铺是商品的属性，服务端从 item 上取。
 *       让前端传只会多一个「传错店」的坑。</li>
 *   <li>没有价格 —— 钱一律服务端算。前端传 priceCents 是经典的越权改价口子。</li>
 *   <li>没有 userId —— 从 JWT 里拿（request attribute），谁登录谁下单。</li>
 *   <li>没有收货人信息 —— 只传 addressId，服务端查地址、拷快照。
 *       让前端传四个字段，就得再校验一遍「这个地址是不是他的」。</li>
 * </ul>
 */
@Data
public class OrderCreateDTO {

    /** 商品 id */
    @NotNull(message = "商品不能为空")
    private Long itemId;

    /** 购买数量。P1 没有购物车，一单只有一件商品，quantity 就是「买几件」 */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少 1 件")
    @Max(value = 99, message = "一次最多买 99 件")
    private Integer quantity;

    /** 收货地址 id，必须是自己的地址（服务端校验，不是自己的按不存在处理） */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /**
     * 幂等键：前端在「点下单」那一刻生成一个 UUID，同一次点击的所有重试都复用它。
     *
     * <p><b>为什么必须前端给：</b>后端每次请求现生成一个新的，连点 5 次就是 5 个 token，
     * 等于没做幂等。这个值代表的其实是「用户的一次点击意图」，只有前端知道。
     *
     * <p><b>为什么必填：</b>可选就等于允许前端「忘了传」，忘了就是 5 个订单。
     * 契约写死：不传直接 400，前端第一次联调就会看到。
     *
     * <p><b>落库之后靠谁兜底：</b>orders 表上有 uk_client_token(user_id, client_token)。
     * 数据库唯一键才是最后一道闸 —— Service 里那句「先查一下有没有」挡不住两个并发请求同时查空。
     */
    @NotBlank(message = "缺少 clientToken，前端每次点击要生成一个 UUID")
    @Size(max = 64, message = "clientToken 最多 64 个字符")
    private String clientToken;
}
