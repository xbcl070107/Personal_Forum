import request from './request'

/**
 * 登录、注册。
 *
 * 为什么要单独一个文件而不是在页面里直接写 request.post(...)：
 * 路径字符串散落在各个页面里，后端哪天改路径（比如 /auth/login 改成 /user/login），
 * 你得全项目搜字符串。收在这一个文件里，改一行。
 */

/** 登录。成功时 data 是 { id, username, role, token } */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 注册。成功时 data 是 null，看 code 判断成败 */
export function register(data) {
  return request.post('/auth/register', data)
}
