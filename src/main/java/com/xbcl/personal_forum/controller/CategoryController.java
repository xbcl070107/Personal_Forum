package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.entity.Category;
import com.xbcl.personal_forum.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 板块接口。
 *
 * <p>完整地址：GET http://localhost:9090/dev-api/category/list
 *
 * <p>Controller 这一层只干三件事：收参数、调 Service、包成 Result。
 * 一旦你在这里写起了 if / for / SQL，说明这段代码放错层了。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/category")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 查全部板块，按 sort_order 排好序返回。
     *
     * <p>GET /dev-api/category/list，没有参数，也不需要登录（白名单里的接口）。
     */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }
}
