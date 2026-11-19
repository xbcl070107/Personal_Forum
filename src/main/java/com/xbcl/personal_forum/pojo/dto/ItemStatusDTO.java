package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 上架 / 下架商品的请求体：
 * {"status":1}   上架
 * {"status":0}   下架
 *
 * <p>为什么只让改 status、不做一个通用的"改商品"接口：
 * 改标题、改价、改库存是「编辑」，上下架是「开关」，两者的权限判断和
 * 副作用都不一样（下架之后集市里立刻看不到，编辑不会）。
 * 混成一个 PUT 的话，前端一个表单提交就可能顺手把商品下架掉。
 *
 * <p>为什么用 {@code Integer} 而不是 {@code boolean onSale}：
 * 列里存的是 TINYINT，将来可能加 2 = 违规下架（管理员操作），
 * boolean 到时候要改协议；现在多留一位数字，代价是零。
 *
 * <p>校验只允许 0 / 1：传 2、传 99 直接 400，别让它写进库。
 * （如果以后真加了"违规下架"，把这里的 Max 改成 2，同时记得
 * Item.STATUS_xxx 常量和 Service 里的判断一起改。）
 */
@Data
public class ItemStatusDTO {

    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 只能是 0（下架）或 1（上架）")
    @Max(value = 1, message = "status 只能是 0（下架）或 1（上架）")
    private Integer status;
}
