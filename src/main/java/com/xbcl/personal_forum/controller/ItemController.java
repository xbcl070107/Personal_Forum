package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.ItemPublishDTO;
import com.xbcl.personal_forum.pojo.dto.ItemStatusDTO;
import com.xbcl.personal_forum.pojo.entity.Category;
import com.xbcl.personal_forum.pojo.vo.ItemVO;
import com.xbcl.personal_forum.pojo.vo.PageVO;
import com.xbcl.personal_forum.service.CategoryService;
import com.xbcl.personal_forum.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口。W2 完成：发布 / 我发布的 / 商品分类。
 * W3 骨架已搭好（实现留空，调用返回 501）：集市列表 / 详情 / 上下架。
 *
 * <p>完整路径（注意前缀 /dev-api 是 context-path，不写在注解里）：
 * <pre>
 *   POST   /dev-api/item              发布商品           需 token
 *   GET    /dev-api/item/mine         我发布的（含下架）  需 token
 *   GET    /dev-api/item/category/list 商品分类           游客可见（白名单）
 *   GET    /dev-api/item/list         集市列表           游客可见（白名单，W3）
 *   GET    /dev-api/item/{id}         商品详情           需 token（W3）
 *   PATCH  /dev-api/item/{id}/status  上架 / 下架        需 token，且必须是店主（W3）
 * </pre>
 *
 * <p>/item/mine 和 /item/{id} 会不会打架：不会。Spring MVC 匹配时
 * "更具体的 pattern" 优先，/mine 是字面量、/{id} 是变量，所以 /item/mine
 * 永远进 mine()。但反过来说，如果你哪天写 /item/{id} 且 id 允许非数字，
 * 那 /item/abc 也会进来 —— 所以 id 这里用 Long，类型对不上直接 400。
 *
 * <p>为什么不把商品分类接口放在 /category 下面：
 * /category/list 已经被论坛首页占了（返回的是帖子板块），
 * 复用同一个 URL 会让前端不得不传 ?scope= 来区分，
 * 一个 URL 两种返回形状是接口设计里最坑的事之一。按业务路径分开：
 * 论坛的走 /category/list，商城的走 /item/category/list。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/item")
public class ItemController {

    private final ItemService itemService;

    private final CategoryService categoryService;

    /**
     * 发布商品。
     *
     * <p>请求体：{"title":"九成新显示器","categoryId":4,"priceCents":49900,
     * "stock":1,"images":["/dev-api/uploads/images/2026-11/a.jpg"],"description":"自提"}
     * <br>响应体：{"code":200,"data":7,"message":"ok"}，data 是新商品 id
     *
     * <p>shopId 不在请求体里：由 ItemService 按当前用户的店查出来。
     * 前端传了也不认 —— 否则可以把商品挂到别人店里。
     */
    @PostMapping
    public Result<Long> publish(@RequestBody @Valid ItemPublishDTO dto,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(itemService.publish(userId, dto));
    }

    /**
     * 我发布的商品（含已下架的），分页。
     *
     * <p>这是卖家工作台，不是集市列表，所以：
     * <ul>
     *   <li>下架的商品也要返回 —— 不然没法重新上架</li>
     *   <li>不需要白名单 —— 只能看自己的</li>
     * </ul>
     */
    @GetMapping("/mine")
    public Result<PageVO<ItemVO>> mine(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(itemService.mine(userId, pageNum, pageSize));
    }

    /**
     * 商品分类（集市列表的分类 tab 用），游客可见 —— 在白名单里。
     *
     * <p>返回的是 category 表里 scope='ITEM' 的那 5 行，
     * 不会混进"日常 / 技术 / 水贴"。
     */
    @GetMapping("/category/list")
    public Result<List<Category>> categories() {
        return Result.success(categoryService.listByScope(Category.SCOPE_ITEM));
    }

    // =====================================================================
    // 以下是 W3 骨架：路径、入参、返回都定好了，方法体在 Service 里，
    // 那边已经实现完了。Controller 这一层基本不用改，要调逻辑去 ItemService。
    // =====================================================================

    /**
     * 集市列表（W3）。游客可见，在白名单里。
     *
     * <p>请求：
     * <pre>
     *   GET /dev-api/item/list
     *       ?keyword=显示器        （可空，标题模糊搜）
     *       &amp;categoryId=4         （可空，分类筛选）
     *       &amp;shopId=1             （可空，W4 店铺主页用：只看某一家店的商品）
     *       &amp;minPriceCents=1000   （可空，价格下限，单位：分）
     *       &amp;maxPriceCents=100000 （可空，价格上限，单位：分）
     *       &amp;sort=new             （可空，new|priceAsc|priceDesc|hot，默认 new）
     *       &amp;pageNum=1&amp;pageSize=10
     * </pre>
     *
     * <p>响应（分页形状由 PageVO 决定，字段名不要自己另起）：
     * <pre>
     *   {"code":200,"message":"ok","data":{
     *      "records":[{"id":7,"shopId":1,"shopName":"禤布出来的小店",
     *                  "categoryId":4,"title":"九成新显示器","priceCents":49900,
     *                  "stock":1,"soldCount":0,"images":["/dev-api/uploads/..."],
     *                  "status":1,"createdAt":"2026-11-17T19:00:00"}],
     *      "total":1,"pageNum":1,"pageSize":10,"pages":1}}
     * </pre>
     *
     * <p>验收：不带 token 直接浏览器访问，应该返回上架商品；
     * 下架商品（status=0）不出现。
     */
    @GetMapping("/list")
    public Result<PageVO<ItemVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Long shopId,
                                       @RequestParam(required = false) Long minPriceCents,
                                       @RequestParam(required = false) Long maxPriceCents,
                                       @RequestParam(required = false) String sort) {
        return Result.success(itemService.listMarket(
                pageNum, pageSize, keyword, categoryId, shopId,
                minPriceCents, maxPriceCents, sort));
    }

    /**
     * 商品详情（W3）。
     *
     * <p>请求：GET /dev-api/item/7
     * <br>响应：{"code":200,"message":"ok","data":{ ...ItemVO... }}
     *
     * <p><b>权限怎么定（这一条要你决定）</b>：
     * 现在这个接口<b>要求登录</b>，因为白名单只能按路径放行，
     * 一旦把 /item/* 加进白名单，/item/mine 也一起放开了，
     * 那时候 userId 取出来是 null，Controller 里 (Long) 拆箱直接 NPE。
     * 想让游客也能看详情，正确做法是改 {@code JwtInterceptor}：
     * <b>有 token 就解析并塞 attribute，没有也放行</b>（软解析），
     * 再在 Service 里判断"下架的商品只有店主本人能看"。
     *
     * <p>@PathVariable 写成 {id}、类型写 Long：/item/abc 会 400，
     * 不会拿个字符串进 Service 再炸。
     */
    @GetMapping("/{id}")
    public Result<ItemVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(itemService.detail(id, userId));
    }

    /**
     * 上架 / 下架（W3）。
     *
     * <p>请求：PATCH /dev-api/item/7/status
     * <br>请求体：{"status":0}（0=下架，1=上架）
     * <br>响应：{"code":200,"message":"ok","data":{ ...改完之后的 ItemVO... }}
     *
     * <p>为什么用 PATCH 不是 PUT：只改一个字段，PUT 语义是"整体替换"，
     * 前端只传 status 会被理解成"其余字段清空"。PATCH 正好表达局部更新。
     * （Postman 里记得把 method 选成 PATCH，选 POST 会 405。）
     *
     * <p>验收：非店主调这个接口应该 403；店主自己调，status 变了、
     * 集市列表里立刻消失/出现。
     */
    @PatchMapping("/{id}/status")
    public Result<ItemVO> changeStatus(@PathVariable Long id,
                                       @RequestBody @Valid ItemStatusDTO dto,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(itemService.changeStatus(userId, id, dto.getStatus()));
    }
}
