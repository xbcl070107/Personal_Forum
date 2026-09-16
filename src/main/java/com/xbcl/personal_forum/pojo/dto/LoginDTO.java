package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体。前端 POST /auth/login 时 JSON 长这样：
 * {"username":"alice","password":"123456"}
 *
 * <p>为什么不直接用 User 实体接参数：
 * 用实体接的话，前端只要字段名对得上，就能顺手多塞一个 {"role":"ADMIN"} 进来。
 * DTO 只声明「我允许你传什么」—— 多出来的字段在反序列化时就被丢掉了。
 *
 * <p>@NotBlank / @Size 这些注解光写不生效，必须在 Controller 参数上加 @Valid 才会跑，
 * 校验不过抛 MethodArgumentNotValidException，由 GlobalExceptionHandler 统一转成 400。
 * 所以这里不需要在方法里写 if (dto.getUsername() == null) return ...。
 */
@Data
public class LoginDTO {

    /** 登录名。message 里那句是给用户看的，会原样进 Result.message */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码明文 —— 它只在「HTTP 请求体 → 内存」这一段里存在，
     * 到了 Service 就被 encode 成哈希，之后没有任何地方再存它。
     * 顺便：日志里也别打这个字段。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
