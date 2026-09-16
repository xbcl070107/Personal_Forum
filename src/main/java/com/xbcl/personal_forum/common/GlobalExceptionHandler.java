package com.xbcl.personal_forum.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>为什么要有它：
 * 每个 Controller 方法里都写 try-catch，业务代码会被埋掉一半，
 * 而且很容易 catch 到一个 RuntimeException 就顺手返回 500 —— 真正的原因就丢了。
 * 这里的做法是：Controller / Service 直接往外抛，统一在这一处接住，
 * 转成规范的 Result 返回，同时把堆栈打进后台日志。
 *
 * <p>两个注解：
 * <ul>
 *   <li>@RestControllerAdvice：对所有 Controller 生效，返回值自动转成 JSON</li>
 *   <li>@ExceptionHandler(Xxx.class)：声明「我负责处理 Xxx 这个异常」</li>
 * </ul>
 *
 * <p>匹配规则：抛异常时 Spring 挑「离异常类型最近」的那个方法。
 * 抛 BusinessException 就走 handleBusiness，不会被最后的 handleException 兜走。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：请求没问题，是规则不允许（用户名已存在、密码错误）。
     *
     * <p>message 是我们自己在代码里写的，可以放心给前端看，所以原样返回。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：@Valid 没通过（用户名没填、密码长度不够）。
     *
     * <p>重点：这个异常是在「进入 Controller 方法体之前」抛的，
     * 所以写在方法里的 try-catch 永远接不到它，只能在这里接。
     *
     * <p>一个 DTO 可能有好几个字段同时不合格，
     * getFieldError() 只取第一条；要全部列出来得用 getFieldErrors()。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        // 先取出来存着。下面再调一次也行，但没必要。
        var fieldError = e.getBindingResult().getFieldError();

        // getDefaultMessage() 拿到的就是 DTO 上 @NotBlank(message = "...") 里那串文字
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : "参数错误";

        return Result.error(400, msg);
    }

    /**
     * 兜底：剩下所有没被上面接住的异常（空指针、SQL 报错、类型转换失败……）。
     *
     * <p>堆栈用 log.error 打进后台控制台，前端只给一句「系统异常」。
     * 不要把 e.getMessage() 直接返回给前端 —— SQL 报错会把表名、字段名带出去。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常");
    }
}