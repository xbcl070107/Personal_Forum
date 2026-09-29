package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论表的 Mapper。
 *
 * <p>extends BaseMapper&lt;Comment&gt; 之后，
 * insert / selectById / selectPage / deleteById 就都继承了，不用写 SQL。
 *
 * <p>这条帖的评论列表要按时间排，用 LambdaQueryWrapper 拼 orderBy，
 * 所以这个接口里暂时一个自定义方法都不需要。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}