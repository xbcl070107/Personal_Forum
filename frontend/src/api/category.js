import request from './request'

/** 板块列表。写死的三个：日常 / 技术 / 水贴 */

export function listCategories() {
  return request.get('/category/list')
}
