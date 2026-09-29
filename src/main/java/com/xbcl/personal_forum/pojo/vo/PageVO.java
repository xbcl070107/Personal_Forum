package com.xbcl.personal_forum.pojo.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;


/**
 * 分页返回的统一外壳。
 *
 * <p>为什么不直接把 MyBatis-Plus 的 Page 返回给前端：
 * Page 里有一堆给插件自己用的字段（orders、optimizeCountSql、countId…），
 * 序列化出去前端看得一脸问号，而且那些字段的含义以后 MP 升级可能就变了。
 * 这里只挑出前端真正要用的五个，接口形状就固定下来了。
 *
 * <p>用 getter 取 current / size，不是 pageNum / pageSize ——
 * MP 的 Page 里这两个字段叫 current 和 size，这边换回我们自己的叫法，
 * 免得前端一会儿 pageNum 一会儿 current。
 *
 * @param <T> 列表里元素的类型，帖子就是 Post，评论就是 Comment
 */
@Data
public class PageVO<T> {

    /**
     * 当前这一页的数据
     */
    private List<T> records;

    /**
     * 总条数。前端拿它算总页数、显示「共 N 条」
     */
    private long total;

    /**
     * 第几页，从 1 开始
     */
    private long pageNum;

    /**
     * 每页几条
     */
    private long pageSize;

    /**
     * 一共几页。MP 已经算好了，别自己在 Java 里再除一次
     */
    private long pages;

    /**
     * 把 MP 的 Page 转成我们的 PageVO
     */
    public static <T> PageVO<T> of(Page<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }
}