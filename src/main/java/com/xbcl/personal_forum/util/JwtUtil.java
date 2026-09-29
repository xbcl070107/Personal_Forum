package com.xbcl.personal_forum.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：签发 token、解析 token。
 *
 * <p>token 长这样，三段用点隔开：
 * <pre>eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.某段签名</pre>
 *
 * <p>⚠️ 中间那段只是 Base64，解码不需要密钥 —— 谁拿到都能看见里面写了什么。
 * 所以：<b>别往里放密码、手机号这类东西。</b>
 * 签名保证的是「没人改过它」，不是「没人看得见它」。
 *
 * <p>⚠️ 网上大部分教程用的还是 jjwt 0.9.x 的写法
 * （{@code Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody()}），
 * 你 pom 里是 0.12.6，那些方法已经删了，照抄会得到一整屏红色。
 * 0.12 的对照写在下面两个方法的注释里。
 *
 * <p>@Component 让它进 Spring 容器，拦截器直接构造器注入就能用。
 */
@Component
public class JwtUtil {

    /** 签名用的密钥。构造时就建好，每次签发不用重建 */
    private final SecretKey key;

    /** 有效期，毫秒。配置文件里给的是小时，这里换算一次 */
    private final long expireMillis;

    /**
     * @param secret      从 application-local.yaml 的 jwt.secret 读进来
     * @param expireHours 从 jwt.expire-hours 读进来
     *
     * <p>{@code @Value("${jwt.secret}")} 的意思是「去配置文件里找 jwt.secret 这个键」。
     * 找不到会在启动时报 IllegalArgumentException，不会悄悄给你个 null。
     *
     * <p>hmacShaKeyFor 按密钥长度自动选算法：
     * 32~47 字节 → HS256，48~63 → HS384，64 以上 → HS512。
     * 我们的 secret 是 45 个字符，所以是 HS256。
     */


    /**
     * @Value 注解：这是 Spring 的依赖注入方式。
     * 它会从配置文件（如 application.yml）中读取 jwt.secret（密钥字符串）和 jwt.expire-hours（过期小时数）。
     * */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours}") long expireHours) {
        //Keys.hmacShaKeyFor(...)：这是 JJWT 0.12+ 版本的新写法。它会根据你传入的字节数组长度，
        //自动选择合适的 HMAC 算法（如 HS256、HS384 或 HS512）。
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60 * 60 * 1000;
    }

    /**
     * 签发 token。登录成功时调用一次。
     *
     * <p>三段内容：
     * <ul>
     *   <li>subject —— 放 userId，是这个 token 的「主人」，解析时第一个取它</li>
     *   <li>claim —— 自定义字段。这里塞了 username 和 role，
     *       拦截器放行之后不用再查一次库就能知道是谁、能不能删帖</li>
     *   <li>issuedAt / expiration —— 签发时间和过期时间，过期由 JJWT 自己判</li>
     * </ul>
     *
     * <p>0.12 的写法：{@code Jwts.builder().subject(...).claim(...).signWith(key).compact()}
     * 0.9 的写法是 {@code setSubject(...) / setClaims(...) / signWith(SignatureAlgorithm.HS256, secret)}。
     * 所有 setXxx 都改成了同名不带 set 的，signWith 也不再收算法参数 —— 它从 key 自己推断。
     */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))                    // 1. 设置主体（通常放用户ID）
                .claim("username", username)                  // 2. 自定义声明（放用户名）
                .claim("role", role)                          // 3. 自定义声明（放角色权限）
                .issuedAt(now)                                      // 4. 签发时间
                .expiration(new Date(now.getTime() + expireMillis)) // 5. 过期时间
                .signWith(key)                                      // 6. 使用密钥进行签名
                .compact();                                         // 7. 压缩成最终的字符串

    }

    /**
     * 解析并校验 token，返回里面的内容。
     *
     * <p>它会同时做三件事：验签名、验过期时间、把 payload 还原成 Claims。
     * 任何一项不通过都会抛异常，所以调用方必须 catch：
     * <ul>
     *   <li>ExpiredJwtException    —— 过期了，让前端重新登录</li>
     *   <li>SignatureException     —— 签名对不上，说明是伪造的</li>
     *   <li>MalformedJwtException  —— 根本不是 JWT 格式</li>
     * </ul>
     * 这三个都是 JwtException 的子类，拦截器里 catch 父类一起处理。
     *
     * <p><b>这个方法抛异常是正常的、预期内的。</b>
     * 别在拦截器里写 try { ... } catch (Exception e) { } 把它吞掉 ——
     * 吞掉就等于「任何 token 都算通过」，白名单也白设了。
     *
     * <p>0.12 的写法：{@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()}
     * 0.9 的写法是 {@code Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody()}。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)            // 1. 指定用于验签的密钥
                .build()                    // 2. 构建解析器
                .parseSignedClaims(token)   // 3. 解析并校验 Token
                .getPayload();              // 4. 获取载荷内容
    }
}
