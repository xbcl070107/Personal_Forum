package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;



@Mapper
public interface UserMapper extends BaseMapper<User> {
}