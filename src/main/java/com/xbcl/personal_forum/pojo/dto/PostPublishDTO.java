package com.xbcl.personal_forum.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发帖请求体：
 * {"title":"第一帖","content":"测试正文","categoryId":1}
 *
 * <p>注意这里<b>没有 userId</b>。作者是谁不由前端说了算 ——
 * 前端传什么都能伪造，所以 userId 从 token 里取，见 PostController。
 *
 * <p>长度限制和 init.sql 里的列定义对齐，改了记得两边一起看：
 * <ul>
 *   <li>title VARCHAR(100) → @Size(max = 100)</li>
 *   <li>content TEXT → 数据库能放 6 万多字节，但接口不设上限的话，
 *       谁都能 POST 一个 60KB 的正文，所以按业务给个 10000</li>
 * </ul>
 */
@Data
public class PostPublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 10000, message = "正文最长 10000 字")
    private String content;

    @NotNull(message = "必须选择板块")
    private Long categoryId;
}
