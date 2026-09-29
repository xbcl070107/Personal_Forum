package com.xbcl.personal_forum.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

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
     * 业务异常。
     *
     * <p>HTTP 状态码跟着 BusinessException 里的 code 走 —— 400 就真的回 400，404 就真的回 404。
     * 不能靠 @ResponseStatus：那个注解的值是写死的，拿不到 e.getCode()。
     *
     * <p>这条规则要和 JwtInterceptor 的 401 对齐：那边是 setStatus(401)，
     * 这边是 ResponseEntity.status(code)，两边都是「body.code 等于 HTTP 状态码」。
     * 前端只要在一个地方判断就够了。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        Result<Void> body = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : "参数错误";
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(Result.error(500, "系统异常"));
    }

}