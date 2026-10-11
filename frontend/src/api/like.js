import request from './request'

/**
 * 点赞。
 *
 * 【为什么没有「赞」和「取消赞」两个函数】
 * 后端只有一个翻面接口：它自己看现在什么状态，然后反过来。
 * 前端不需要传「我要赞」还是「我要取消」—— 传了反而多一个会说谎的地方：
 * 前端记错一次自己的状态，后端就赞反了。
 *
 * 【为什么还要一个 getLike】
 * 翻面接口只在「用户点了按钮」时才调得到。刚打开详情页时，
 * 前端不知道这个人赞过没 —— 不查的话，刷新之后按钮永远画成「未赞」。
 * 所以进来先 GET 一次拿状态，之后每次点击用 POST 的返回值刷新。
 * 两个接口的响应形状完全一样，所以页面里一个处理函数就够。
 */

/** 翻面。返回 data: { liked, count } */
export function toggleLike(postId) {
  // ⚠️ postId 是 query 参数（后端签名是 @RequestParam），不是请求体。
  // axios 的 post(url, data, config) 里第三个参数才是 config
  return request.post('/post/like', null, { params: { postId } })
}

/** 查状态。返回 data: { liked, count } */
export function getLike(postId) {
  return request.get('/post/like', { params: { postId } })
}
