package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Item;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品表的 Mapper。
 *
 * <p>W2 只做"发布 + 看自己发的"，列表筛选（关键词/分类/价格区间）
 * 全部能用 selectPage + QueryWrapper 拼出来，所以暂时不写 XML。
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
