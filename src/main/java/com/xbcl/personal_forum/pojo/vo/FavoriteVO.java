package com.xbcl.personal_forum.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收藏按钮要的两样东西。结构和 LikeVO 一模一样，只是字段叫 favorited。
 *
 * <p>为什么不让收藏直接复用 LikeVO：字段名对不上。收藏按钮拿到一个叫
 * liked 的字段，三个月后读代码的人就得停下来想「这个 liked 到底指哪个」——
 * 多一个类是有成本的，但省掉的这个类，代价是这里每次都要猜。
 */
@Data
@AllArgsConstructor
public class FavoriteVO {

    /** 请求这个接口的人收藏过没 */
    private Boolean favorited;

    /** 这个帖一共被收藏了多少次 */
    private long count;
}
