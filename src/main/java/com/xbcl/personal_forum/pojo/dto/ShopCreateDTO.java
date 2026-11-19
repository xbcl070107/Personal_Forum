package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开店请求体：
 * {"name":"冒险者商店","avatarUrl":"/dev-api/uploads/images/2026-11/x.jpg","description":"卖点杂物"}
 *
 * <p>注意这里<b>没有 ownerUserId</b>：店主是谁从 token 里取。
 * 前端传了也不认 —— 否则谁都能替别人开店。
 *
 * <p>长度限制和 migration-v4-shop.sql 的列定义对齐（shop.name VARCHAR(32)、
 * description VARCHAR(255)）。avatarUrl 允许为空：先开店、后传头像。
 */
@Data
public class ShopCreateDTO {

    @NotBlank(message = "店名不能为空")
    @Size(max = 32, message = "店名最长 32 字")
    private String name;

    /** 店铺头像 URL，就是 /file/upload 返回的那个路径；可以为空 */
    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;

    @Size(max = 255, message = "店铺简介最长 255 字")
    private String description;

    /**
     * 店铺类型：1 个人店 / 2 公会店。不传默认个人店。
     * 只做标记，不做权限区分，所以这里不校验"你有没有资格开公会店"。
     */
    @Min(value = 1, message = "店铺类型只能是 1（个人）或 2（公会）")
    @Max(value = 2, message = "店铺类型只能是 1（个人）或 2（公会）")
    private Integer type;
}
