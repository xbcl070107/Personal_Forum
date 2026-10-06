import request from './request'

/**
 * 评论相关。
 *
 * 只有两个接口，而且都跟帖子绑定 ——
 * 评论不属于「评论模块」，它属于「某一条帖子的评论区」。
 */

/** 某条帖子的评论列表，按时间正序。返回 data 是 CommentVO[]，带 username */
export function listComments(postId) {
  return request.get('/comment/list', { params: { postId } })
}

/** 发表评论。data: { postId, content }，返回新评论 id */
export function publishComment(data) {
  return request.post('/comment/publish', data)
}
