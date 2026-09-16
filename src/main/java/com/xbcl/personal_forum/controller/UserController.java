package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.LoginDTO;
import com.xbcl.personal_forum.pojo.dto.RegisterDTO;
import com.xbcl.personal_forum.pojo.entity.User;
import com.xbcl.personal_forum.pojo.vo.LoginVO;
import com.xbcl.personal_forum.service.UserService;
import com.xbcl.personal_forum.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录。
 *
 * <p>@RestController = @Controller + @ResponseBody，
 * 方法返回的对象会被 Jackson 自动转成 JSON 写进响应体，
 * 所以只要 return Result.xxx(...)，不用碰 HttpServletResponse。
 *
 * <p>@RequestMapping("/auth") 是类级别的路径前缀。再加上
 * application-local.yaml 里的 context-path: /dev-api，下面那些方法的完整地址是：
 * POST http://localhost:9090/dev-api/auth/register
 * POST http://localhost:9090/dev-api/auth/login
 *
 * <p>注意这个类里没有一行 try-catch —— 异常直接让它往上抛，
 * 由 common/GlobalExceptionHandler 统一接住转成 Result。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserController {

    /** 注册和登录都靠它：查用户名是否被占、比对密码 */
    private final UserService userService;

    /**
     * 签发 token 用。它不是 new 出来的 —— JwtUtil 上有 @Component，
     * Spring 启动时就建好了，这里声明成 final 字段，构造器参数由 Lombok 生成。
     *
     * <p>写在构造器里注入而不是给字段加 @Autowired：JwtUtil 是 final 的，
     * 哪天它没了这个类直接编译不过，而不是跑到线上才蹦 NullPointerException。
     */
    private final JwtUtil jwtUtil;

    /**
     * 注册。
     *
     * <p>请求体是 JSON：{"username":"alice","password":"123456"}
     *
     * <p>@RequestBody：把请求体里的 JSON 反序列化成 RegisterDTO。
     * @Valid：顺手跑一遍 DTO 上的 @NotBlank / @Size。校验不过会抛
     * MethodArgumentNotValidException，被全局处理器接住返回 400 ——
     * 所以这里不用写 if (username == null)。
     *
     * <p>返回 Result&lt;Void&gt;：这个接口没有数据给前端，成功就是 code=200。
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.success(null);
    }

    /**
     * 登录。整个 JWT 链路的起点 —— 全项目只有这一个地方签发 token。
     *
     * <p>请求体：{"username":"alice","password":"123456"}
     * <br>响应体：{"code":200,"message":"ok","data":{"id":1,"username":"alice",
     * "role":"USER","token":"eyJhbGciOi..."}}
     *
     * <p>三步：验密码 → 签发 token → 打包返回。
     * 第一步在 Service 里做（BCrypt 比对），这里只管后两步。
     *
     * <p>前端拿到之后：token 存进 localStorage，之后每个请求都带
     * <pre>Authorization: Bearer &lt;token&gt;</pre>
     * 换来身份，不用再登第二次。这就是 JWT 的全部意义 ——
     * 服务端不存 session，谁有没有登录过，看 token 本身就知道。
     *
     * <p>为什么返回 LoginVO 而不是直接返回 User：
     * User 里有 passwordHash 字段。整个返回出去等于把密码哈希送到浏览器，
     * 而拿到哈希就能离线慢慢爆破 —— BCrypt 的设计前提是「哈希不外泄」。
     * LoginVO 的作用就是「只放该给前端看的字段」。
     *
     * <p>为什么要把 role 塞进 token：删帖接口要判断「作者本人或 ADMIN」，
     * token 里带着 role 就不用为了这个再查一次库。代价是登录之后改了角色，
     * 得等 token 过期才生效（本项目有效期几小时，可以接受）。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        // 密码不对会由 Service 抛 BusinessException，走不到下面这行
        User user = userService.login(dto);

        // 签发。三个参数都会被写进 token：userId 进 subject，另两个进自定义 claim，
        // 拦截器解析时原样取出来
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        return Result.success(new LoginVO(user.getId(), user.getUsername(), user.getRole(), token));
    }
}
