package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发评论的请求体：
 *
 * <pre>{"postId": 1, "content": "顶一个"}</pre>
 *
 * <p>postId 不是「让用户选的」—— 用户在帖子详情页底下打字，前端把当前页面的
 * 帖子 id 塞进这个字段，界面上从来没有「选择帖子」这个动作。它是上下文，不是输入。
 * 但后端一定得要它：comment.post_id 是 NOT NULL，没有归属的评论数据库不收。
 *
 * <p>这里同样没有 userId。评论人是谁由后端从 token 里取 —— 和 PostPublishDTO
 * 一个道理，前端传什么都可能是伪造的。
 *
 * <p>长度上限和 init.sql 里的列定义对齐：comment.content 是 VARCHAR(500)，
 * 这里就是 @Size(max = 500)。接口放开到 1000 而库里只收 500，
 * 那不叫校验，那叫把异常推给数据库。
 */
@Data
public class CommentPublishDTO {

    /** 所属帖子 id。@NotNull 拦 null，不让 null 一路带到 selectById 里去 */
    @NotNull(message = "缺少帖子 id")
    private Long postId;

    /**
     * 评论内容。
     *
     * <p>@NotBlank 和 @Size 是两件事，缺一不可：
     * @NotBlank 拦 null / 空串 / 纯空格，@Size 只管长度。
     * 只留 @Size 的话，null 会被放行 —— 它遇到 null 直接判过 ——
     * 然后一路走到 insert，撞上库里的 NOT NULL，前端收到一个 500。
     */
    @NotBlank(message = "评论不能为空")
    @Size(max = 500, message = "评论最长500字")
    private String content;
}
