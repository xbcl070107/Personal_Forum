package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Like;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞表的 Mapper。
 *
 * <p>一个方法都不用写：insert / delete / selectCount 全从 BaseMapper 来。
 * 点赞在 SQL 层面全是「一行」的操作，没有需要手写 SQL 的地方 ——
 * 和 PostMapper 那种要写 listPage 的不一样。
 *
 * <p>@Mapper 其实可有可无：启动类上有
 * @MapperScan("com.xbcl.personal_forum.mapper")，扫到就生成实现类。
 * 写上是和另外四个 Mapper 保持一致。
 */
@Mapper
public interface LikeMapper extends BaseMapper<Like> {
}
