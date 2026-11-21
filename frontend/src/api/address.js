import request from './request'

/**
 * 收货地址（W5）。
 *
 * 地址是订单的**快照来源**：下单那一刻会把这里的收货人/电话/地址
 * 抄进 orders 表，之后你改地址、删地址，都不影响已经下过的单。
 * 所以这里的增删改只影响「以后下的单」，前端不要把它当成订单的一部分。
 *
 * 另一个容易忘的规则：**第一个地址会自动变成默认地址**（后端做的）。
 * 前端不用先 POST 一条 isDefault=1 的再补一条普通地址。
 */

/** 地址列表。默认地址排最前；没填过地址返回空数组，不是 404 */
export function listAddresses() {
  return request.get('/address/list')
}

/**
 * 新增地址。
 * data: { receiverName, phone, region, detail, isDefault }
 * - isDefault 用 0 / 1，不是 boolean
 * - 最多 20 条，第 21 条后端会 400
 */
export function createAddress(data) {
  return request.post('/address', data)
}

/**
 * 修改地址。**整体替换**，不是局部更新。
 *
 * 所以表单里每个字段都要传（哪怕没改），漏传的字段会被当成空。
 * isDefault 这里只在传 1 时生效；传 0 不会把默认地址取消 ——
 * 「取消默认」这个动作后端故意没提供，否则会出现一条默认都没有的状态。
 */
export function updateAddress(id, data) {
  return request.put(`/address/${id}`, data)
}

/** 设为默认地址。幂等：本来就是默认再点一次也不报错 */
export function setDefaultAddress(id) {
  return request.patch(`/address/${id}/default`)
}

/**
 * 删除地址（逻辑删除）。
 *
 * 删掉的如果正好是默认地址，后端会把剩下最新的一条自动顶上来；
 * 同一个 id 删第二次会 404 —— 页面按「已经没了」处理即可。
 */
export function deleteAddress(id) {
  return request.delete(`/address/${id}`)
}
