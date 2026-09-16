package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子表的 Mapper。
 *
 * <p>extends BaseMapper&lt;Post&gt; 之后，insert / deleteById / updateById /
 * selectById / selectList / selectPage 就全都有了，跟 UserMapper 一样。
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {
}