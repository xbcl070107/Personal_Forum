package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 修改收货地址的请求体（W5）。
 *
 * <p>为什么不拆成 AddressCreateDTO + AddressEditDTO：
 * 地址表单是一屏填完、整体提交的，PUT 的「整体替换」语义正好对得上，
 * 没有任何「只改手机号、别的字段不传」的场景。所以两个接口共用一个 DTO，
 * 除了 isDefault 以外全是必填。
 *
 * <p>请求体长这样：
 * <pre>
 * {"receiverName":"张三","phone":"13800138000",
 *  "region":"浙江省 杭州市 西湖区","detail":"文三路 100 号 3 幢 501","isDefault":1}
 * </pre>
 *
 * <p>字段长度按数据库列宽卡（receiver_name varchar(32) 之类），别让超长字符串
 * 在 INSERT 那一刻才炸 —— 那时候前端只能看到一条 500，不知道是哪一栏填长了。
 */
@Data
public class AddressSaveDTO {

    /** 收货人姓名 */
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 32, message = "收货人姓名最多 32 个字")
    private String receiverName;

    /**
     * 手机号。
     *
     * <p>用正则一次说清「是不是一个大陆手机号」，比 {@code @Size(min = 11, max = 11)} 准：
     * 11 个 8 也能通过长度校验，但显然不是手机号。
     *
     * <p>注意转义：Java 源码里写 {@code \\d}，也就是编译后的正则 {@code \d}。
     * 这个字段最容易被写成 {@code "\d"} —— 那是非法转义，编译都过不去。
     *
     * <p>不做的（留到以后）：国际号码、座机、脱敏回传。
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;

    /** 省市区，一整块存（例如"浙江省 杭州市 西湖区"）。P1 不做三级联动，前端一个输入框 */
    @NotBlank(message = "省市区不能为空")
    @Size(max = 64, message = "省市区最多 64 个字")
    private String region;

    /** 详细地址（街道、门牌号、楼栋房号） */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 128, message = "详细地址最多 128 个字")
    private String detail;

    /**
     * 是否设为默认地址：1 = 设为默认，0 / 不传 = 不动默认标记。
     *
     * <p>这里刻意不接受「取消默认」：一个人总得有一条默认地址，否则下单页预选什么？
     * 要换默认地址，就去把另一条设成默认（POST 时传 1，或走 PATCH /address/{id}/default）。
     * 接口语义钉死，比猜「前端这次到底想干什么」可靠。
     *
     * <p>用 Integer 不用 boolean：null（不传）和 0（传了但没勾）在这里合并处理，
     * 而且和数据库的 tinyint 直接对上，省一层转换。
     */
    @Min(value = 0, message = "isDefault 只能是 0 或 1")
    @Max(value = 1, message = "isDefault 只能是 0 或 1")
    private Integer isDefault;
}
