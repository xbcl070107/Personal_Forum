import request from './request'

/**
 * 店铺（W2 开店 / W4 店铺主页）。
 *
 * 一人一店：同一个账号再调 createShop 会被后端拒绝
 * （唯一键 + DuplicateKeyException 兜底，并发点两次也只会成功一家）。
 */

/** 我的店铺。要登录。**没开店返回 data: null，不是报错** —— 前端要判空 */
export function myShop() {
  return request.get('/shop/mine')
}

/**
 * 店铺详情。游客可看（/shop/* 在白名单里）。
 *
 * 返回里有 ownerNickname —— 这个字段不是 shop 表里的，
 * 是服务端拿 ownerUserId 去 user 表查出来补上的（W4 的关联查询）。
 * 没有它，店铺主页只能显示一串数字 id。
 */
export function getShop(id) {
  return request.get(`/shop/${id}`)
}

/** 开店。data: { name, avatarUrl, description, type }，type 1 = 个人店，2 = 公会店 */
export function createShop(data) {
  return request.post('/shop', data)
}

/** 改店。data: { name, avatarUrl, description }，**只传要改的字段**，没传的保持原样 */
export function updateShop(data) {
  return request.put('/shop', data)
}
