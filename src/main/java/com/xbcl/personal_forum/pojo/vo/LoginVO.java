package com.xbcl.personal_forum.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功后返回给前端的数据（VO = View Object）。
 *
 * <p>前端拿到之后做两件事：
 * <ul>
 *   <li>token 存起来（localStorage），之后每个请求都塞进请求头：
 *       <pre>Authorization: Bearer eyJhbGciOi...</pre>
 *       那七个字符 "Bearer " 加空格是 HTTP 的约定写法，拦截器就是按它切字符串的，
 *       少了它一律按「没带 token」处理 —— 现象是登录明明成功了，下一个请求还是 401。</li>
 *   <li>id / username / role 存起来，渲染右上角的昵称，
 *       以及决定「这条帖子下面要不要显示删除按钮」。</li>
 * </ul>
 *
 * <p>为什么不能直接返回 User 实体：
 * User 里有 passwordHash。整个返回出去等于把密码哈希送到浏览器 ——
 * 而 BCrypt 的设计前提就是「哈希不外泄」，拿到哈希就有无限时间慢慢爆破。
 * VO 的作用就是「只放该给前端看的字段」，跟 User 是两个不同用途的类。
 *
 * <p>和 LoginDTO 一对：DTO 管进，VO 管出，方向相反、字段也不同。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** 用户 id。前端用它做本地判断（比如「是不是我的帖子」），也用于发帖后的跳转 */
    private Long id;

    /** 登录名，显示在页面右上角 */
    private String username;

    /**
     * 角色：ADMIN / USER。目前注册一律给 USER。
     * ADMIN 能删任何人的帖子，USER 只能删自己的 —— 这个判断在删帖接口里做。
     */
    private String role;

    /**
     * 登录凭证，{@link com.xbcl.personal_forum.util.JwtUtil#generate} 生成，形如 xxx.yyy.zzz。
     *
     * <p>它自带有效期（配置里的 jwt.expire-hours 小时）。到期之后拦截器会返回 401，
     * 前端收到 401 就该清掉本地存的 token、跳回登录页 —— 这是前端唯一需要处理的登录失效场景。
     */
    private String token;
}
