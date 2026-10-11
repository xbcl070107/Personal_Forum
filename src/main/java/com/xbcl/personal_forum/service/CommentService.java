package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.mapper.CommentMapper;
import com.xbcl.personal_forum.mapper.UserMapper;
import com.xbcl.personal_forum.pojo.dto.CommentPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Comment;
import com.xbcl.personal_forum.pojo.entity.User;
import com.xbcl.personal_forum.pojo.vo.CommentVO;
import com.xbcl.personal_forum.pojo.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论业务。
 *
 * <p>评论不做审核 —— 帖子有 status 是为了别让乱七八糟的东西上首页，
 * 评论挂在各自帖子下面，没有这个问题。所以这个类里没有审核相关的方法。
 *
 * <p>三个依赖各有用途：CommentMapper 是评论本表；UserMapper 用来批量取作者名；
 * PostService 只用来调 requireApproved —— 「这条帖游客看得见吗」这个前提全项目只留一份。
 *
 * <p>⚠️ 校验用 requireApproved，不用 detail()：detail 对作者本人和管理员是放行的，
 * 拿它当校验，等于放行作者在自己还没过审的帖子下面自嗨。
 * 以前这里自己抄了一份同样的判断（读 post 表 + 比 status），已经收口回 PostService ——
 * 规则只有一处，以后改可见性不会漏掉评论这个入口。
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;

    /** 只为了调 requireApproved，本类不碰任何帖子数据 */
    private final PostService postService;

    /** 评论列表要显示作者名，所以这里也要读 user 表 */
    private final UserMapper userMapper;

    /**
     * 发表评论。
     *
     * @param dto    请求体，postId 和 content 已经过 @Valid 校验
     * @param userId 评论人，由 Controller 从 token 里取出来传进来。
     *               不能改成从 dto 里读 —— 那是前端说了算的东西
     * @return 新评论的 id
     */
    public Long publish(CommentPublishDTO dto, Long userId) {

        // 帖子必须存在、且已经通过审核，否则不给评论。
        // 这一关收在 PostService.requireApproved 里，和下面 listByPost 用的是同一份 ——
        // 校验只写一遍，以后改规则不会漏掉另一个入口。
        postService.requireApproved(dto.getPostId());

        // 组装实体，只设这三个字段。
        // id 交给自增；isDeleted 和 createdAt 留 null —— MyBatis-Plus 的 insert
        // 默认跳过 null 字段，这两列不进 INSERT 语句，走数据库自己的
        // DEFAULT 0 和 DEFAULT CURRENT_TIMESTAMP。手动 set 一个值进去，反而绕开了默认值。
        Comment comment = new Comment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());

        // 落库。insert 之后 MP 会把自增主键回填到 comment 这个对象上，
        // 所以紧接着 getId() 拿到的就是新评论的 id，不用再查一次
        commentMapper.insert(comment);
        return comment.getId();
    }

    /**
     * 一条帖子的评论列表，按时间正序（旧的在上）。
     *
     * <p>没分页。评论真上量了（一条帖几百条）再改成分页，
     * 那时候前端也得跟着做「加载更多」，不是后端单方面能改的。
     */
    public List<CommentVO> listByPost(Long postId) {

        // 和发评论同一道关：帖子不可见，它下面的评论也不该被看见
        postService.requireApproved(postId);

        // ① 查评论。正序：评论区是对话，得从旧读到新。
        // 第二行 orderByAsc(id) 是兜底 —— created_at 只精确到秒，
        // 同一秒发的两条顺序不固定，刷新一下可能就换位置。
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreatedAt)
                        .orderByAsc(Comment::getId)
        );

        // 没评论就直接回空列表。这行不能省：
        // 下面的 selectBatchIds 空集合进去会拼出 WHERE id IN ()，MySQL 报语法错
        if (comments.isEmpty()) {
            return List.of();
        }

        // ② 一次把所有作者查出来。
        // 组装时挨个 selectById 的话，20 条评论就是 21 次查询 —— 那就是 N+1。
        // 规矩：查询次数只跟「有几种东西要查」有关，跟「多少条」无关。
        // toSet 不用 toList：同一个人评论 5 次，用户只查一次
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());

        // selectBatchIds 拼出来的是 WHERE id IN (?,?)，一次拿回所有作者。
        // 注意它查的是 user 整行，包括 password_hash —— 这里没漏，
        // 因为下面组装 VO 时只取了 username。但这条线要一直记住：
        // User 实体永远不直接返回给前端，一次都不行。
        List<User> users = userMapper.selectBatchIds(userIds);

        // 收成 Map<用户id, User>，下面组装时从内存取，不再碰数据库。
        // 存整个 User 而不是只存用户名：评论区要显示 nickname（展示名），
        // 同时还要 username 给前端算头像配色 —— 两个字段都在这一个对象上，
        // 存两份 Map 等于把同一个查询做两遍。
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // ③ 组装 VO
        return comments.stream().map(comment -> {
            CommentVO vo = new CommentVO();
            vo.setId(comment.getId());
            vo.setUserId(comment.getUserId());

            // 兜底：作者被物理删除时 Map 里没这个人，给句「已注销」，
            // 别让前端显示 null。两个名字字段都要给，否则前端那一处会显示空白
            User author = userMap.get(comment.getUserId());
            if (author == null) {
                vo.setUsername(PostVO.UNKNOWN_AUTHOR);
                vo.setNickname(PostVO.UNKNOWN_AUTHOR);
            } else {
                vo.setUsername(author.getUsername());
                // displayName() 自带「昵称为空退回 username」的兜底
                vo.setNickname(author.displayName());
            }

            vo.setContent(comment.getContent());
            vo.setCreatedAt(comment.getCreatedAt());
            return vo;
        }).toList();
    }
}
