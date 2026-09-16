package com.xbcl.personal_forum.common;

/**
 * 业务异常：表示「请求本身没毛病，但规则上不允许」。
 *
 * <p>比如：用户名已存在、密码错误、帖子已经被删了。
 * 这些不是程序 bug，是用户操作引起的，message 可以直接给用户看。
 *
 * <p>为什么不继续用 RuntimeException：
 * 全局处理器需要区分「能明说的错」和「真出事了」——
 * 前者返回 400 + 一句人话，后者返回 500 + 把堆栈打进日志。
 * 都用 RuntimeException 的话，这两类就分不开了。
 *
 * <p>为什么继承 RuntimeException 而不是 Exception：
 * 编译器不会强制调用方 try-catch，Service 里可以直接 throw 出去，
 * 上一层不用写任何处理，最后统一被 GlobalExceptionHandler 接住。
 *
 * <p>code 字段：默认 400（参数 / 业务问题）。
 * 留一个带 code 的重载，是为了后面拦截器能写
 * {@code throw new BusinessException(401, "请先登录")}。
 */
public class BusinessException extends RuntimeException {

    /** 放进 Result.code 的错误码 */
    private final Integer code;

    /** 最常用的一种：400 + 提示语 */
    public BusinessException(String message) {
        this(400, message);
    }

    /** 需要指定 code 的时候用（比如 401 未登录） */
    public BusinessException(Integer code, String message) {
        super(message);   // message 交给父类存着，之后 getMessage() 取到的就是它
        this.code = code;
    }

    /** GlobalExceptionHandler 要读它，所以必须给 getter */
    public Integer getCode() {
        return code;
    }
}