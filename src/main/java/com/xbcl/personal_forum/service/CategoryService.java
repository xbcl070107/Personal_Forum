package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.pojo.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 板块业务层。
 *
 * <p>本版板块不做在线增删改（见可行性分析 1.3），
 * 数据由 sql/init.sql 里的 INSERT 写死，所以这里只有「查」。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 查全部板块，按 sort_order 升序。
     *
     * <p>selectList 的参数没有写成 null 了：null 的意思是「没有 WHERE 条件」，
     * 那样返回顺序由数据库自己决定（通常是主键序），没有保证。
     * category 表专门建了 sort_order，就得让 SQL 带上 ORDER BY sort_order ASC ——
     * 排序让数据库做，别查回来在 Java 里 sort。
     */
    public List<Category> list() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder)
        );
    }
}
