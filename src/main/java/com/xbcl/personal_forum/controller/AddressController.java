package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.AddressSaveDTO;
import com.xbcl.personal_forum.pojo.vo.AddressVO;
import com.xbcl.personal_forum.service.AddressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（W5 上半场）—— 骨架已定，业务在 AddressService 里等你填。
 *
 * <p>完整路径（/dev-api 是 context-path，不写在注解里）：
 * <pre>
 *   POST   /dev-api/address               新增地址            需 token
 *   GET    /dev-api/address/list          我的地址列表        需 token
 *   PUT    /dev-api/address/{id}          改地址（整体替换）  需 token
 *   PATCH  /dev-api/address/{id}/default  设为默认            需 token
 *   DELETE /dev-api/address/{id}          删除（逻辑删除）    需 token
 * </pre>
 *
 * <p>五个接口全都要登录 —— 地址是私有数据，没有一个是游客能看的，
 * 所以 WebMvcConfig 的拦截器白名单里<b>一条都不用加</b>（加了才是 bug）。
 *
 * <p>userId 一律从 request attribute 取（JwtInterceptor 塞进去的），
 * 绝不从路径或请求体里取 —— 否则改个 URL 就能看别人的地址。
 *
 * <p>为什么「设为默认」单独一个 PATCH 而不是复用 PUT：
 * 前端列表里点一下「设为默认」，不该逼用户把整条地址重新提交一遍（也拿不到最新值）。
 *
 * <p>验证顺序（Postman 里按这个来，也是验收口径）：
 * <pre>
 * 1. 新增带 isDefault=1 的地址 A → 200
 * 2. 列表 → A 在最前，isDefault=1
 * 3. 再新增地址 B（isDefault=1）→ 列表里 A 变成 0、B 是 1
 * 4. PATCH A/default → A 是 1、B 是 0
 * 5. DELETE A（A 是默认）→ 列表里 B 自动变成默认（1）
 * 6. 不带 token 调任何一个 → 401
 * 7. 用别人的 token 改你的地址 id → 404（不是 403）
 * </pre>
 */
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 我的地址列表（默认地址排最前）。
     *
     * <pre>
     * GET /dev-api/address/list
     * Authorization: Bearer xxx
     *
     * 200 {"code":200,"message":"ok","data":[
     *        {"id":3,"receiverName":"张三","phone":"13800138000",
     *         "region":"浙江省 杭州市 西湖区","detail":"文三路 100 号 3 幢 501",
     *         "isDefault":1,"createdAt":"2025-11-19T16:00:00"}]}
     *
     * 一条都没有 → 200 + data: []（空列表是正常结果，不是 404）
     * </pre>
     */
    @GetMapping("/list")
    public Result<List<AddressVO>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(addressService.list(userId));
    }

    /**
     * 新增地址。
     *
     * <pre>
     * POST /dev-api/address
     * {"receiverName":"张三","phone":"13800138000",
     *  "region":"浙江省 杭州市 西湖区","detail":"文三路 100 号 3 幢 501","isDefault":1}
     *
     * 200 {"code":200,"data":{"id":3,...}}       ← id 由 insert 回填
     * 400 手机号格式不对 / 最多保存 20 个收货地址
     * 401 未登录
     * </pre>
     *
     * <p>第一条地址即使不传 isDefault 也会被设成默认（Service 里处理），
     * 不然用户第一次下单，下单页预选不出地址。
     */
    @PostMapping
    public Result<AddressVO> create(@RequestBody @Valid AddressSaveDTO dto,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(addressService.create(userId, dto));
    }

    /**
     * 修改地址（整体替换：五个字段都按请求体重写）。
     *
     * <pre>
     * PUT /dev-api/address/3
     * {"receiverName":"张三丰","phone":"13900139000",
     *  "region":"浙江省 杭州市 余杭区","detail":"文一西路 969 号","isDefault":0}
     *
     * 200 {"code":200,"data":{...}}
     * 404 地址不存在（或不是你的地址 —— 这两种情况回一样的，别泄露存在性）
     * </pre>
     *
     * <p>isDefault 只在传 1 时动作（立即设为默认）；传 0 或不传 = 保持原样。
     * 想"取消默认"请去设另一条地址为默认 —— 一个人总得有一条默认地址。
     */
    @PutMapping("/{id}")
    public Result<AddressVO> update(@PathVariable Long id,
                                    @RequestBody @Valid AddressSaveDTO dto,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(addressService.update(userId, id, dto));
    }

    /**
     * 把某条地址设为默认（前端列表里那个"设为默认"按钮）。
     *
     * <pre>
     * PATCH /dev-api/address/3/default
     * （无请求体）
     *
     * 200 {"code":200,"data":{"id":3,"isDefault":1,...}}
     * 404 地址不存在
     * </pre>
     *
     * <p>幂等：本来就默认，再调一次也是 200、什么都不变。
     */
    @PatchMapping("/{id}/default")
    public Result<AddressVO> setDefault(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(addressService.setDefault(userId, id));
    }

    /**
     * 删除地址（逻辑删除，行还在库里，只是查不出来）。
     *
     * <pre>
     * DELETE /dev-api/address/3
     *
     * 200 {"code":200,"data":null}
     * 404 第二次删同一个 id（逻辑删除后 selectById 已经查不到，属预期）
     * </pre>
     *
     * <p>如果删掉的是默认地址，剩下最新的一条会自动顶上来当默认，
     * 否则用户下一单就没有预选地址了。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        addressService.delete(userId, id);
        return Result.success(null);
    }
}
