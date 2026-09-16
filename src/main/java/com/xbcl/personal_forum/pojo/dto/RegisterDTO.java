package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。前端 POST /auth/register 时 JSON 长这样：
 * {"username":"alice","password":"123456"}
 *
 * <p>和 LoginDTO 分开写，而不是合成一个、在注册时多检查几项 ——
 * 这两个接口的规则本来就不一样（注册有长度下限，登录只看能不能对上），
 * 共用一个类的结果是两边都塞一堆 if。
 *
 * <p>校验规则和 sql/init.sql 里的列定义是对齐的，
 * 改这里的时候记得回头看数据库那列够不够长：
 * <ul>
 *   <li>username VARCHAR(32) —— 所以 @Size(max = 32)</li>
 *   <li>password_hash VARCHAR(100) —— BCrypt 结果固定 60 位，够放</li>
 * </ul>
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名最长 32 位")
    private String username;

    /**
     * 密码明文，长度限制是业务规定（6-20）而不是数据库限制；
     * 落库时存的是 BCrypt 哈希，固定 60 位。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在 6-20 位之间")
    private String password;
}
