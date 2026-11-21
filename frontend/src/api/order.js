import request from './request'

/**
 * 订单（W6）。
 *
 * 状态机只有一条主线：
 *   0 待付款 → 1 待发货 → 2 待收货 → 3 已完成
 *           ↘ 4 已取消（只有未付款时能取消，取消会回补库存）
 *
 * 前端不要自己「拼」下一个状态再传给后端 —— 每个动作一个接口，
 * 由后端做条件更新（UPDATE ... WHERE status = 当前状态）：
 * 两个人同时点「发货」，只会有一个成功，另一个拿到 400。
 */

/**
 * 下单。
 * data: { itemId, quantity, addressId, clientToken }
 *
 * clientToken 是**幂等令牌**：同一次结算页打开期间必须用同一个值。
 * 用户看网络慢连点两次「提交」，两次请求带同一个 token，
 * 后端靠 uk_client_token 只建一单，第二次回 400「请勿重复提交」。
 * 如果每次点击都重新生成，就会真的下出两单。
 */
export function createOrder(data) {
  return request.post('/order', data)
}

/**
 * 订单详情。买家本人或该店店主才能看，别人 404。
 * OrderVO **没有 userId** —— 前端要判断自己是买家还是卖家，
 * 只能拿 order.shopId 和自己的店铺 id 比（见 OrderDetail.vue）。
 */
export function getOrder(id) {
  return request.get(`/order/${id}`)
}

/** 模拟支付。0 → 1，同时给商品 sold_count 加数量（后端做） */
export function payOrder(id) {
  return request.post(`/order/${id}/pay`)
}

/**
 * 发货。只有店主能调，1 → 2。
 * data: { trackingNo, shippingCompany }
 * trackingNo 必填（最长 64），shippingCompany 可空（最长 32）。
 */
export function shipOrder(id, data) {
  return request.post(`/order/${id}/ship`, data)
}

/** 确认收货。只有买家能调，2 → 3 */
export function confirmOrder(id) {
  return request.post(`/order/${id}/confirm`)
}

/** 取消订单。只有买家、只有未付款能调，0 → 4，库存回补 */
export function cancelOrder(id) {
  return request.post(`/order/${id}/cancel`)
}

/**
 * 订单列表。
 * params: { role: 'buyer' | 'seller', status, pageNum, pageSize }
 *
 * - role 不传按 buyer 处理；传错也一样，所以别拼错
 * - role=seller 时后端按「我的店」查，没开店会 404「你还没有开店」
 * - status 传 0~4 筛状态；不传是全部。传个 99 不会报错，只会返回空列表
 * - pageSize 服务端最多 50，前端传 100 也只给 50
 */
export function listOrders(params) {
  return request.get('/order/list', { params })
}
