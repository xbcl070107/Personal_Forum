package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.ShopCreateDTO;
import com.xbcl.personal_forum.pojo.dto.ShopEditDTO;
import com.xbcl.personal_forum.pojo.vo.ShopVO;
import com.xbcl.personal_forum.service.ShopService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺接口。
 *
 * <p>开店：POST http://localhost:9090/dev-api/shop
 * <br>改店：PUT  http://localhost:9090/dev-api/shop
 * <br>我的店：GET http://localhost:9090/dev-api/shop/mine
 * <br>店铺详情：GET http://localhost:9090/dev-api/shop/{id}
 *
 * <p>四个接口都<b>不在白名单里</b>：开 / 改店显然要登录，
 * "我的店"要靠 token 认人，店铺详情也先要求登录（游客可看的集市是 W3 的事，
 * 到时候只把需要的那个接口放出去，而不是整片 /shop/**）。
 *
 * <p>注意路径顺序的问题：{@code /shop/mine} 和 {@code /shop/{id}} 长得一样，
 * Spring 匹配时字面量优先于路径变量，所以 mine 不会被当成 id=mine 解析。
 * 但如果哪天把 mine 改成 /shop/{id} 并加个 type 参数，就要小心这个顺序。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop")
public class ShopController {

    private final ShopService shopService;

    /**
     * 开店。
     *
     * <p>请求体：{"name":"冒险者商店","description":"卖点杂物"}
     * <br>响应体：{"code":200,"data":{"id":1,"name":"冒险者商店",...},"message":"ok"}
     *
     * <p>ownerUserId 从 token 里取，<b>不从请求体取</b> —— 否则谁都能替别人开店。
     * 这一点和 PostController.publish 是同一个道理。
     */
    @PostMapping
    public Result<ShopVO> create(@RequestBody @Valid ShopCreateDTO dto,
                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(shopService.create(userId, dto));
    }

    /**
     * 改店名 / 头像 / 简介（局部更新：没传的字段不动）。
     *
     * <p>改的是谁家的店由 token 决定，请求体里没有 shopId ——
     * 一人一店，不需要，也免得有人传别人的 shopId。
     */
    @PutMapping
    public Result<ShopVO> update(@RequestBody @Valid ShopEditDTO dto,
                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(shopService.update(userId, dto));
    }

    /**
     * 我的店。
     *
     * <p>没开过店返回 200 + data:null（不是 404）：
     * 「还没开店」是正常状态，前端按 data 是否为空决定显示"去开店"按钮。
     */
    @GetMapping("/mine")
    public Result<ShopVO> mine(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(shopService.mine(userId));
    }

    /**
     * 店铺详情（按店铺 id）。
     *
     * <p>关了的店返回 404，和"店铺不存在"同一个响应 ——
     * 前端不需要为"存在但关了"多写一个分支。
     */
    @GetMapping("/{id}")
    public Result<ShopVO> detail(@PathVariable Long id) {
        return Result.success(shopService.detail(id));
    }
}
