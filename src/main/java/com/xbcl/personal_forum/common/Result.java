package com.xbcl.personal_forum.common;

import lombok.Data;

/**
 * 所有接口的统一返回格式。
 *
 * <p>为什么要有它：
 * 没有它的话，每个接口返回什么全凭手感 —— 有的返回 List，有的返回 Map，
 * 有的成功了返回 null，有的失败了返回一个字符串。
 * 结果是前端要写十几种判断，后端改一个接口就要动前端好几处。
 *
 * <p>有了它，前端只需要认这三个字段：
 * <ul>
 *   <li>code    200 = 成功，其他都算失败</li>
 *   <li>message 给人看的一句话</li>
 *   <li>data    真正的数据。失败的时候是 null</li>
 * </ul>
 *
 * <p>&lt;T&gt; 是泛型，意思是「data 可以是任何类型」。
 * 返回板块列表时它是 List&lt;Category&gt;，返回一个帖子时它是 Post。
 * 如果没有泛型，就得为每种数据类型单独写一个返回类。
 *
 * @param <T> data 的类型
 */
@Data
public class Result<T> {

    /** 200 = 成功，其他都算失败 */
    private Integer code;

    /** 给人看的一句话，前端可以直接弹出来 */
    private String message;

    /** 真正的数据，失败时为 null */
    private T data;

    /*
     * 构造器是 private 的：外面不许直接 new Result(...)，
     * 只能用下面的 ok() / fail()。
     * 这样能保证「成功一定带 200，失败一定带提示语」，格式不会被人写歪。
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /*
     * 方法前面的 static 是为了能直接写 Result.ok(数据)，
     * 不用先 new 一个空对象再往里塞。
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
