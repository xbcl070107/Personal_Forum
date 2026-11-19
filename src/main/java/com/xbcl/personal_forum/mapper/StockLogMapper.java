package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存流水表的 Mapper。
 *
 * <p>库存在数据库层用 UPDATE ... SET stock = stock - n WHERE stock >= n 保证不超卖，
 * 每成功扣一次就往这张表记一条流水（谁、因为哪笔订单、扣了几件、扣前扣后）。
 * W2 只建表，W5 下单时才会真的写。
 */
@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {
}
