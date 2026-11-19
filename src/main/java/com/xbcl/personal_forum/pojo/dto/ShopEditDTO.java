package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改店铺信息的请求体（PUT /shop）。
 *
 * <p>每个字段都允许为空，含义是<b>「这个字段不改」</b>（局部更新），
 * 不是「改成空串」。想真的把简介清空，传一个空格 " " 比传空串诚实。
 *
 * <p>为什么不和 ShopCreateDTO 合用一份：那边 name 是 @NotBlank，
 * 这边 name 可以为 null。合成一个类就得在 Service 里判断"这次是新建还是修改"，
 * 校验注解反而帮不上忙。
 *
 * <p>type 不给改：个人店升公会店是运营动作，不该由卖家自己 PUT 一下完成。
 */
@Data
public class ShopEditDTO {

    @Size(max = 32, message = "店名最长 32 字")
    private String name;

    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;

    @Size(max = 255, message = "店铺简介最长 255 字")
    private String description;
}
