package com.xbcl.personal_forum.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
 * 抛 BusinessException 就走 handleBusiness，不会被最后的 handleException 兜走；
 * 参数类型转不过去（MethodArgumentTypeMismatchException）走 handleBadRequest，同理。
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

    /**
     * 参数「格式不对」这一类。
     *
     * <p>和 handleValid 的分工：handleValid 管的是 @Valid 校验不通过（字段值不合法，
     * 比如标题为空）；这里管的是**请求压根没被解析成方法参数** ——
     * 路径 / 查询参数类型转不过去、必填参数没传、请求体不是合法 JSON。
     *
     * <p>为什么必须单独接：不接的话它们会落到最下面的 handleException，
     * 变成 500「系统异常」。但这三种都是<b>调用方的问题</b>，本该是 400 ——
     * 用户看到 500 会以为服务器挂了，你翻日志才发现是自己少传了一个参数。
     *
     * <p>自己试：浏览器打开 /dev-api/post/list?pageNum=abc，
     * 加这段之前是 500，加之后是 400。
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,       // /post/list?pageNum=abc
            MissingServletRequestParameterException.class,   // /post/like 少传 postId
            HttpMessageNotReadableException.class            // 请求体不是合法 JSON
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        // 和 handleException 一样：细节只进日志，响应体里只给人话
        log.warn("请求参数不合法：{}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(400, "参数格式不对"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(Result.error(500, "系统异常"));
    }

}