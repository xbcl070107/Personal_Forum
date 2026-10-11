package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.mapper.PostMapper;
import com.xbcl.personal_forum.mapper.UserMapper;
import com.xbcl.personal_forum.pojo.dto.PostPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Post;
import com.xbcl.personal_forum.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbcl.personal_forum.pojo.vo.PageVO;
import com.xbcl.personal_forum.pojo.vo.PostVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子业务。
 *
 * <p>新增任何「读帖子」的方法时，记住 status 这个字段：
 * MyBatis-Plus 的逻辑删除（isDeleted）会自动带上条件，status 不会，
 * 每个查询都得自己写。写漏了，待审和已驳回的帖就直接漏给前端了。
 *
 * <p>另外：<b>返回给前端的读方法一律返回 PostVO，不返回 Post 实体。</b>
 * 实体里只有 user_id，前端拿不到作者名。别再往实体上挂 username
 * 这种「数据库里没有的字段」—— 那样 insert 时也得操心它会不会被写进去。
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;

    /** 用来校验发帖时选的板块真的存在 */
    private final CategoryMapper categoryMapper;

    /**
     * 读帖子时要把作者名一起带出去（PostVO.username），所以要读 user 表。
     *
     * <p>和 CommentService 一个道理：<b>User 实体永远不直接返回给前端</b>，
     * 它带着 password_hash。这里只从它身上取 id 和 username 两个字段。
     */
    private final UserMapper userMapper;

    /**
     * 批量把帖子作者查出来。
     *
     * <p>返回 Map&lt;用户id, User&gt;，调用方从内存里取。
     *
     * <p>为什么返回整个 User 而不是「id → 用户名」：现在帖子上要显示两个字段
     * （nickname 展示、username 当稳定标识），各查一张 Map 就是同一个查询做两遍。
     * User 在这里只在内存里流转，<b>不会</b>被直接返回给前端 ——
     * 出去的是 PostVO，passwordHash 没有出口。
     *
     * <p>为什么必须「批量」：组装的时候挨个 selectById，一页 10 条帖子就是
     * 10 次查用户 —— 这就是 N+1。而一页帖子的作者撑死 10 个人、去重后通常更少，
     * 所以规矩还是那条：查询次数只跟「有几种东西要查」有关，跟「多少条」无关。
     */
    private Map<Long, User> usersOf(Collection<Post> posts) {

        // 空集合进去的话，下面 selectBatchIds 会拼出 WHERE id IN () —— MySQL 语法错。
        // 这个判断不能省：分页查出来是空页、或者列表本来就没数据，都会走到这里
        if (posts == null || posts.isEmpty()) {
            return Map.of();
        }

        // toSet 而不是 toList：同一个人发了 5 条，用户只查一次
        Set<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .collect(Collectors.toSet());

        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    /**
     * 组装 VO。作者查不到时传 null 进去，由 PostVO 自己给「已注销」占位名 ——
     * 占位文案只留一处，不在这里再写一遍。
     */
    private PostVO toVO(Post post, Map<Long, User> users) {
        return PostVO.of(post, users.get(post.getUserId()));
    }

    /**
     * 发帖。注意发出来的帖子是「待审」状态，不是直接发布。
     *
     * @param dto    前端传的标题 / 正文 / 板块
     * @param userId 作者，由 Controller 从 token 里取出来传进来 ——
     *               故意不放进 dto，前端就伪造不了作者
     * @return 新帖的 id
     *         <p>帖子此时是「待审」，首页列表里看不到它 —— 所以前端发帖成功后
     *         跳的是「我的帖子」，不是首页（见 frontend 的 PostEdit.vue）。
     *         这个 id 作者本人能打开自己的详情页（detail 对作者放行），
     *         换个人拿到就只会得到 404。
     */
    public Long publish(PostPublishDTO dto, Long userId) {

        // 1. 板块得真的存在。post 表上没建外键约束（建了删板块会很麻烦），
        //    所以「categoryId 是不是有效」得自己在代码里看，
        //    不然能发出一条 category_id 指向空气的帖子，详情页会查不到板块名。
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException("板块不存在");
        }

        // 2. 拼实体。只设用户填的那几个，
        //    isDeleted / createdAt / updatedAt 一律不碰：
        //    is_deleted 靠数据库的 DEFAULT 0，两个时间靠 DEFAULT CURRENT_TIMESTAMP。
        //    MyBatis-Plus 不会把 null 字段写进 INSERT 语句，所以它们是安全的。
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setCategoryId(dto.getCategoryId());

        // 3. 待审。数据库那列写的是 DEFAULT 0，不给也能跑，
        //    但那样「发帖 = 待审」这条规则就只藏在建表语句里，看代码看不出来。
        //    显式写出来，以后改审核流程的人一眼就知道入口在哪。
        post.setStatus(Post.STATUS_PENDING);

        // 4. 落库。@TableId(type = IdType.AUTO) 的作用就在这：
        //    insert 之后新 id 会回填到 post 对象上，可以直接读出来用。
        postMapper.insert(post);

        return post.getId();
    }

    /** 每页最多返回多少条，防止前端传 pageSize=100000 把整张表拖出来 */
    private static final long MAX_PAGE_SIZE = 50;

    /**
     * 把前端传来的分页参数夹到合法范围。
     *
     * <p>pageNum 传 0 或者负数，拼出来的 LIMIT 是负数，SQL 直接报语法错；
     * pageSize 传 100000 就是把整张表查回来。这两种都不算「用户填错了」，
     * 是接口自己该扛住的输入 —— 夹一下，比报 500 体面。
     */
    private static <T> Page<T> page(long pageNum, long pageSize) {
        return new Page<>(Math.max(pageNum, 1), Math.min(pageSize, MAX_PAGE_SIZE));
    }

    /**
     * 帖子列表：只出「已通过」的，游客也能看。
     *
     * @param categoryId 板块 id。传 null 表示「全部板块」
     */
    public PageVO<PostVO> listApproved(long pageNum, long pageSize, Long categoryId) {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, Post.STATUS_APPROVED)
                // 三个参数的 eq 是「条件版」重载：第一个参数为 false 时，
                // 这个条件整条不拼进 SQL。所以 categoryId 为 null 就是查全部，
                // 不用再写一个 if 把查询拆成两条。
                .eq(categoryId != null, Post::getCategoryId, categoryId)
                .orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId);

        // 走 (status, created_at) 那个联合索引：status 定位，created_at 反向扫
        Page<Post> result = postMapper.selectPage(page(pageNum, pageSize), wrapper);

        // 先按这一页的作者批量查出来，再连同分页信息一起转成 VO。
        // 顺序不能反：转换时要用到 users 这张表
        Map<Long, User> users = usersOf(result.getRecords());
        return PageVO.of(result, post -> toVO(post, users));
    }

    /**
     * 帖子详情。
     *
     * <p>权限判断放在这里，但 Service 不认识 HttpServletRequest ——
     * 「你是谁」由 Controller 从 token 里取出来当参数传进来。
     *
     * @param currentUserId 当前登录用户，可能为 null（不在白名单，一般都有）
     * @param isAdmin       是不是管理员
     */
    public PostVO detail(Long postId, Long currentUserId, boolean isAdmin) {

        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }

        // 已经通过审核的，谁都能看
        // 没通过的，只有作者本人和管理员能看。
        // 用 Objects.equals 而不是 post.getUserId().equals(...)：
        // 前者两个都为 null 时返回 true，不会因为某一头是 null 就 NPE。
        //
        // 这里回 404，不是 403。
        // 403 等于告诉对方「这个 id 存在，只是不给你看」——
        // 拿 id 从 1 数到 100，别人就能数出你有几条待审帖。
        boolean visible = post.getStatus() == Post.STATUS_APPROVED
                || isAdmin
                || Objects.equals(post.getUserId(), currentUserId);
        if (!visible) {
            throw new BusinessException(404, "帖子不存在");
        }

        // 详情页只有一条帖子，所以这一步就是一次单点查询，不会 N+1
        return toVO(post, usersOf(List.of(post)));
    }

    /**
     * 我的帖子。作者看自己的东西，四种状态都要出。
     */
    public PageVO<PostVO> listMy(long pageNum, long pageSize, Long userId) {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        // 这里故意不筛 status。
        // 列表和详情是「只给已经通过的」，这一条不一样 ——
        // 待审和被驳回的帖，作者自己必须看得见，否则他不知道发出去之后发生了什么。
        wrapper.eq(Post::getUserId, userId)
                .orderByDesc(Post::getCreatedAt);

        Page<Post> result = postMapper.selectPage(page(pageNum, pageSize), wrapper);
        Map<Long, User> users = usersOf(result.getRecords());
        return PageVO.of(result, post -> toVO(post, users));
    }

    /**
     * 待审列表，给管理员用。
     *
     * @return 所有待审的帖子，按提交时间<b>正序</b>（最早的排最前，先来先审）
     */
    public List<PostVO> listPending() {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, Post.STATUS_PENDING)
                .orderByAsc(Post::getCreatedAt);

        // is_deleted 不用写。MyBatis-Plus 的逻辑删除会自动加上 is_deleted = 0，
        // 这就是它比手写 SQL 省事的地方。
        List<Post> posts = postMapper.selectList(wrapper);

        // 审核台一屏要显示好几份委托，作者名得带上 ——
        // 以前这里直接返回实体，前端只能按 userId 猜名字，审核台会显示成一串假名
        Map<Long, User> users = usersOf(posts);
        return posts.stream().map(post -> toVO(post, users)).toList();

        // 数据量大了再换成分页（项目里已经配了分页插件）：
        // return postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 审核一条帖子：通过或者驳回。
     *
     * <p>权限判断不在这里 —— Service 不该认识 HttpServletRequest。
     * 「是不是管理员」由 Controller 从 token 里取 role 判断，见 PostController#checkAdmin。
     *
     * @param postId 要审的帖子
     * @param pass   true 通过 / false 驳回
     * @throws BusinessException 帖子不存在，或者已经不是待审状态了
     */
    public void audit(Long postId, boolean pass) {

        // 用「条件更新」，不是「先查再改」。
        //
        // 先 selectById 看一眼 status 是不是 0，然后再 update —— 那样有竞态：
        // 两个管理员同时点，两边都查到是「待审」，两边都通过，看起来没事，
        // 但「先驳回再点通过」这种情况状态就会乱跳。
        //
        // 条件写在 WHERE 里，让数据库自己保证原子性：
        // 只有 status 还等于 0 的行会被更新，第二次执行影响的函数就是 0 行。
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Post::getStatus, pass ? Post.STATUS_APPROVED : Post.STATUS_REJECTED)
                .eq(Post::getId, postId)
                .eq(Post::getStatus, Post.STATUS_PENDING);

        // update(null, wrapper)：entitiy 传 null，因为要改的值都在 wrapper 的 set 里。
        // 返回的是「影响行数」，不是成功与否 —— 这个值必须看。
        int rows = postMapper.update(null, wrapper);

        if (rows == 0) {
            // 走到这说明 WHERE 没匹配上。可能是 id 写错了，也可能是这条已经被审过了。
            // 分不清具体是哪种，就只说事实，别替用户下结论。
            throw new BusinessException("帖子不存在，或者已经被审核过了");
        }
    }
    /**
     * 删帖。作者本人删自己的，管理员删谁的都行。
     *
     * <p>这是逻辑删除：不执行 DELETE，只把 is_deleted 改成 1。
     * 靠 application.yaml 里的 logic-delete-field，postMapper.delete(...) 会被
     * 改写成 UPDATE post SET is_deleted = 1 WHERE ...，并自动补上 is_deleted = 0。
     *
     * @param currentUserId 当前登录用户，由 Controller 从 token 里取
     * @param isAdmin       管理员不受「只能删自己的」限制
     */
    public void delete(Long postId, Long currentUserId, boolean isAdmin) {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getId, postId)
                // 条件版 eq：isAdmin 为 true 时这条整个不拼进 SQL，id 对上就算数；
                // 否则拼成 user_id = ?，删别人的帖子匹配 0 行，落到下面的 404。
                .eq(!isAdmin, Post::getUserId, currentUserId);

        // 用 delete(wrapper) 而不是 deleteById(id)：后者只有 id 一个条件，
        // 表达不了「必须还是我发的」。也别先 selectById 再删 —— 那是先查再改的
        // 竞态，和 audit 里那段的道理一样，条件写进 WHERE 让数据库保证原子性。
        int rows = postMapper.delete(wrapper);

        if (rows == 0) {
            throw new BusinessException(404, "帖子不存在，或者不是你发的");
        }
    }

    /**
     * 确认帖子存在、且已经通过审核，否则抛 404。
     *
     * <p>点赞 / 收藏 / 评论这类「挂在帖子下的动作」共用这一个前提，
     * 所以它是 public，被 LikeService、FavoriteService 注入调用 ——
     * 规则只留一份，将来改可见性不会漏掉某个入口。
     *
     * <p><b>不能拿 detail() 顶替这个检查</b>：detail 对作者本人和管理员是放行的，
     * 于是作者能给自己的待审帖点赞，管理员的赞还会留在一个别人看不见的帖子上。
     * 这里只认「已过审」，和游客能看到的范围一致。
     *
     * @return 查出来的帖子，调用方要标题之类的可以直接拿去用
     */
    public Post requireApproved(Long postId) {

        // selectById 由 MyBatis-Plus 自动带上 is_deleted = 0，
        // 软删的帖子查出来是 null —— 「删掉的帖不能被点赞」不用额外写。
        Post post = postMapper.selectById(postId);

        if (post == null || post.getStatus() != Post.STATUS_APPROVED) {
            throw new BusinessException(404, "帖子不存在");
        }
        return post;
    }
}
