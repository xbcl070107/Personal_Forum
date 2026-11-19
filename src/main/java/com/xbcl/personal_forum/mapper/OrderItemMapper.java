package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细表的 Mapper。
 *
 * <p>下单时把商品快照（标题/价格/图片）写进明细里，
 * 之后商品改价或下架都不影响历史订单。W2 只建表。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
