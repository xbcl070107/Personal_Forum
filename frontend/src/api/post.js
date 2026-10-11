import request from './request'

/**
 * 帖子相关。
 *
 * 每个函数的参数形状跟后端接口一一对应：
 * - params 是拼在 URL 上的（pageNum / pageSize / categoryId），用 { params } 传
 * - data 是请求体（发帖、审核），直接当第二个参数
 */

/** 帖子列表。游客也能看，只会返回已通过审核的。params: { pageNum, pageSize, categoryId } */
export function listPosts(params) {
  return request.get('/post/list', { params })
}

/** 帖子详情。要登录。返回 data 是一个 Post */
export function getPost(id) {
  return request.get(`/post/${id}`)
}

/** 我的帖子。四种审核状态都会返回，方便作者看自己的帖被驳回了没 */
export function myPosts(params) {
  return request.get('/post/my', { params })
}

/** 发帖。data: { title, content, categoryId }，返回新帖 id */
export function publishPost(data) {
  return request.post('/post/publish', data)
}

/**
 * 待审核列表（管理员）。
 *
 * ⚠️ 返回的 data 是 **Post[]**，不是别的列表那种 { records, total, pages } 分页对象 ——
 * 这个接口没有分页，传 pageNum/pageSize 也没用（后端根本不接这两个参数）。
 * 用的时候直接 res.data 当数组，别写 res.data.records。
 */
export function pendingPosts() {
  return request.get('/post/pending')
}

/**
 * 审核。pass=true 通过，pass=false 驳回。
 *
 * ⚠️ 这两个参数是 **query 参数，不是 JSON 请求体** ——
 * 后端签名是 audit(@RequestParam Long postId, @RequestParam Boolean pass)，
 * 所以请求长这样：POST /post/audit?postId=9&pass=true
 *
 * 写成 request.post('/post/audit', { postId, pass }) 是错的：
 * 第二个参数是「请求体」，参数会跑进 body，后端从 URL 里找不到，
 * 直接抛异常，前端收到一句没有信息量的「系统异常」。
 *
 * axios 的 post(url, data, config) 里，**第三个参数才是「怎么发这个请求」**，
 * params 放这儿。data 传 null 因为我们确实没有请求体。
 *
 * 怎么判断该用哪种：@RequestParam 用 params，@RequestBody 用第二个参数。
 * 拿不准就看浏览器 F12 的 Network —— 「Query String Parameters」和
 * 「Request Payload」是两个不同的区块，一眼能看出来发错了。
 */
export function auditPost(postId, pass) {
  return request.post('/post/audit', null, { params: { postId, pass } })
}

/**
 * 删帖。作者本人和 ADMIN 都能删。
 *
 * ⚠️ 是 **DELETE**，id 在路径里，没有请求体 —— 所以不是 request.post。
 * 后端做的是逻辑删除（UPDATE post SET is_deleted = 1），但那属于后端的事。
 *
 * 删不掉时返回 code 404「帖子不存在，或者不是你发的」：
 * 这条可能已经被删了，也可能压根不是你的 —— 两种情况后端故意不区分。
 */
export function deletePost(id) {
  return request.delete(`/post/${id}`)
}

