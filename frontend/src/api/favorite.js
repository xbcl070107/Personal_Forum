import request from './request'

/**
 * 收藏。和 like.js 完全同构，路径换成 /post/favorite，
 * 字段从 liked 换成 favorited —— 后端也是两个独立的 VO。
 *
 * 为什么不在一个文件里用参数区分：两个模块各自可能长歪
 * （比如收藏以后要加「收藏夹分组」），到时候拆文件反而要动调用方。
 */

/** 翻面。返回 data: { favorited, count } */
export function toggleFavorite(postId) {
  return request.post('/post/favorite', null, { params: { postId } })
}

/** 查状态。返回 data: { favorited, count } */
export function getFavorite(postId) {
  return request.get('/post/favorite', { params: { postId } })
}
