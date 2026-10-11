package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏表的 Mapper。和 LikeMapper 一样，一个方法都不用写。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
