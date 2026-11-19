package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单表的 Mapper。
 *
 * <p>表名是保留字 orders，实体上用 @TableName("orders") 标注过了，
 * 这里不用重复写。W5 下单前只建表不写业务。
 */
@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
}
