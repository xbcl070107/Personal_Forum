package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表的 Mapper。
 *
 * <p>extends BaseMapper&lt;User&gt; 之后，单表增删改查就全都有了：
 * insert / deleteById / updateById / selectById / selectList / selectCount ...
 * 一个方法都不用自己写。只有连表、聚合这类复杂查询才需要把它写成接口方法 + XML。
 *
 * <p>泛型 &lt;User&gt; 告诉它「我管的是 user 表」，返回类型也据此推断，
 * 所以 selectOne 直接返回 User，不用强转。
 *
 * <p>@Mapper 是给 MyBatis 的记号，让它启动时扫到并生成实现类。
 * 本工程的启动类上已经写了 @MapperScan("com.xbcl.personal_forum.mapper")，
 * 所以这个 @Mapper 其实可以不写；两个都留着不算错。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
