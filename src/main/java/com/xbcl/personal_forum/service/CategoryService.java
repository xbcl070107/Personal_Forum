package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.pojo.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 板块 / 商品分类业务层。
 *
 * <p>本版这两类东西都不做在线增删改（见可行性分析 1.3），
 * 数据由 sql/init.sql 和 sql/migration-v4-shop.sql 的 INSERT 写死，
 * 所以这里只有「查」。
 *
 * <p>帖子板块和商品分类共用 category 表，靠 scope 列区分：
 * 'POST' 是帖子板块（日常 / 技术 / 水贴），'ITEM' 是商品分类（数码 / 图书 …）。
 * 两个接口各查各的，别互相污染 —— 论坛首页的下拉框里出现"虚拟"是错的。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 帖子板块（GET /category/list）。
     *
     * <p>只返回 scope = 'POST' 的：商城那 5 个商品分类不该出现在发帖的下拉框里。
     * 这是 W2 加 scope 之后的行为变化，之前是「全表返回」——
     * 那时表里只有 3 个板块，等于全量；现在不一样了。
     */
    public List<Category> list() {
        return listByScope(Category.SCOPE_POST);
    }

    /**
     * 按 scope 查分类，按 sort_order 升序。
     *
     * <p>排序交给 SQL 的 ORDER BY，别查回 Java 里 sort：
     * 排序规则将来要改（比如加权重列）只改一处。
     *
     * <p>为什么 selectList 要带 wrapper 而不是传 null：null 的意思是
     * 「没有 WHERE 条件」，那样 return 顺序由数据库自己决定，没有保证。
     */
    public List<Category> listByScope(String scope) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getScope, scope)
                        .orderByAsc(Category::getSortOrder)
        );
    }
}
