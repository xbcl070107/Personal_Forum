package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布商品的请求体：
 * {"title":"九成新显示器","categoryId":4,"priceCents":49900,"stock":1,
 *  "images":["/dev-api/uploads/images/2026-11/a.jpg"],"description":"自提"}
 *
 * <p>店铺不在这里指定 —— shopId 由服务端按「当前用户的唯一一家店」查出来。
 * 让前端传 shopId 等于允许他把商品挂到别人店里。
 *
 * <p>价格用 <b>Long priceCents（分）</b>，不用 double、也不收 "49.9" 这种字符串：
 * 浮点表示不了 0.1，前端算钱和后端算钱对不上时的排查成本远高于多点一次转换。
 *
 * <p>库存 W2 只做「发布时写一个数」，扣减逻辑是 W5 下单时才进来的。
 */
@Data
public class ItemPublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 80, message = "标题最长 80 字")
    private String title;

    @Size(max = 5000, message = "详情最长 5000 字")
    private String description;

    /** 商品分类，可以空（集市里有「未分类」）。空就不校验 scope */
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @Min(value = 1, message = "价格至少 1 分")
    @Max(value = 99_999_999, message = "价格上限 999999.99 元")
    private Long priceCents;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    @Max(value = 999_999, message = "库存上限 999999")
    private Integer stock;

    /**
     * 图片 URL 列表，最多 9 张。存进 item.images_json 时序列化成 JSON 数组。
     * 前端拿到的是 /file/upload 返回的路径，这里只校验数量和单个长度，
     * 不校验"这个文件真的存在"—— 上传接口已经保证过了。
     */
    @Size(max = 9, message = "最多 9 张图")
    private List<@Size(max = 255, message = "图片地址过长") String> images;
}
