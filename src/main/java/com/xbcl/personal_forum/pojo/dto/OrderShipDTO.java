package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发货请求体（W6）。
 *
 * <pre>
 * {"trackingNo":"SF1234567890","shippingCompany":"顺丰"}
 * </pre>
 *
 * <p>只有两个字段，都是卖家填的<b>物流信息</b>。故意没有的：
 * <ul>
 *   <li>没有 orderId —— 它在 URL 里（POST /order/{id}/ship）。表单里再放一份，
 *       两边不一致时你还要先决定信谁，纯属给自己找活</li>
 *   <li>没有 userId —— 从 token 取。前端传了也不认，认了就等于允许替别人发货</li>
 *   <li>没有 status —— 能发货的状态只有一个（1 待发货），由 Service 的条件更新决定。
 *       让前端传 status 等于把状态机交给浏览器</li>
 * </ul>
 *
 * <p>为什么快递单号必填：买家看到「已发货」之后唯一会做的事就是查物流，
 * 没有单号这个状态就只是个摆设。P1 只存不校验（不调快递接口、不查轨迹），
 * 但「有没有单号」是能拦住的第一道门。
 */
@Data
public class OrderShipDTO {

    /** 快递单号，最长 64（和 orders.tracking_no VARCHAR(64) 对齐，别让数据库来截断） */
    @NotBlank(message = "快递单号不能为空")
    @Size(max = 64, message = "快递单号最长 64 个字符")
    private String trackingNo;

    /** 快递公司（顺丰 / 中通 / 自己送…），可空；最长 32，和 orders.shipping_company 对齐 */
    @Size(max = 32, message = "快递公司最长 32 个字符")
    private String shippingCompany;
}
