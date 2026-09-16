package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 板块表的 Mapper。
 *
 * <p>和 UserMapper 一样，继承了 BaseMapper 就是一个方法都不用写。
 * 板块在业务上只读，所以这个接口到现在为止全是在用父类给的方法。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
