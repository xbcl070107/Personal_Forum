package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 店铺表的 Mapper。
 *
 * <p>继承 BaseMapper 之后增删改查都是现成的，和 PostMapper 一样。
 * 一个用户最多一家店，由 shop 表的 user_id 唯一键兜住，
 * 所以这里连"按 user_id 查店铺"都先用 selectOne + QueryWrapper 解决。
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
