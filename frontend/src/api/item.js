import request from './request'

/**
 * 集市商品（W2 发布 / W3 列表与上下架 / W4 按店铺过滤）。
 *
 * 每个函数对应后端一个接口，参数形状照抄 Controller 的签名：
 * - GET 的 query 参数（pageNum / keyword / shopId …）用 { params } 传
 * - POST / PATCH 的请求体（发布商品、改状态）直接当第二个参数
 */

/**
 * 集市列表。**游客也能看**（/item/list 在白名单里）。
 *
 * params: { pageNum, pageSize, keyword, categoryId, shopId, minPriceCents, maxPriceCents, sort }
 * - sort 只能是 new | priceAsc | priceDesc | hot，传别的后端会当 new 处理
 * - shopId 就是 W4 的「店铺主页」：同一个接口，多带一个条件而已
 * - 返回 data 是 { records, total, pages, pageNum, pageSize }，商品在 records 里
 */
export function listItems(params) {
  return request.get('/item/list', { params })
}

/**
 * 商品详情。**要登录** —— /item/{id} 不在白名单。
 * 下架的商品只有店主自己能看到，别人拿到的是一句 404「商品不存在」。
 */
export function getItem(id) {
  return request.get(`/item/${id}`)
}

/** 我的商品（要登录）。没开店的人拿到的是空列表，不是报错 */
export function myItems(params) {
  return request.get('/item/mine', { params })
}

/** 商品分类（游客可看）。返回的是 category 表里 scope = 'ITEM' 的那 5 条 */
export function listItemCategories() {
  return request.get('/item/category/list')
}

/**
 * 发布商品。data: { title, description, categoryId, priceCents, stock, images }
 *
 * 注意两件事：
 * 1. priceCents 是**分**，不是元。99.9 元要传 9990，传 99.9 后端直接 400
 * 2. shopId 不在参数里 —— 后端用 token 里的 userId 去查你的店，
 *    让前端传 shopId 就等于允许别人往你店里挂商品
 */
export function publishItem(data) {
  return request.post('/item', data)
}

/**
 * 上下架。status: 1 = 上架，0 = 下架。
 *
 * 是 **PATCH** 不是 PUT：这里只改一个字段，PUT 的语义是「整条替换」。
 * 请求体是 JSON（后端 @RequestBody ItemStatusDTO），不是 query 参数。
 */
export function changeItemStatus(id, status) {
  return request.patch(`/item/${id}/status`, { status })
}
