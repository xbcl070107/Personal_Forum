package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址表的 Mapper。
 *
 * <p>W2 用不到（下单才用），但表已经建好了，先把 Mapper 放这儿，
 * 免得 W5 写订单时还要回头补。
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
