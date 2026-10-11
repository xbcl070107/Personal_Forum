package com.xbcl.personal_forum.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 点赞按钮要的两样东西。
 *
 * <p>为什么不是只返回一个数字：按钮要画成两种样子（赞过 / 没赞过），
 * 旁边还要显示总数。只给总数的话，前端得自己记「我点没点」——
 * 刷新一下浏览器它就不记得了。
 *
 * <p>为什么点完还要把 count 带回来：前端大可以自己把数字 +1，
 * 但两个人同时点的时候它那个数就不对了。后端数完原样带回去，前端只管显示。
 */
@Data
@AllArgsConstructor
public class LikeVO {

    /** 请求这个接口的人赞过没。前端拿它决定按钮是实心还是空心 */
    private Boolean liked;

    /** 这个帖一共被赞了多少次 */
    private long count;
}
