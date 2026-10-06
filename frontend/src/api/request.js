import axios from 'axios'

/**
 * 全项目唯一的 axios 实例。
 *
 * <p>所有接口调用都走它，不要在各个页面里自己 import axios 新建 ——
 * 那样 token 不会自动带上，错误也不会自动弹提示，
 * 每个页面都得把同样的十行代码抄一遍，改一处就得改十处。
 *
 * <p>baseURL 写 '/dev-api' 而不是 'http://localhost:9090/dev-api'：
 * 写全地址就是跨域请求，会被浏览器拦掉；写相对路径，
 * 浏览器认为这是同源请求，剩下的交给 vite.config.js 里的 proxy 去转发。
 */
const request = axios.create({
  baseURL: '/dev-api',
  timeout: 10000,
})

/**
 * 请求拦截器：每个请求出去之前先过这里。
 *
 * 唯一的职责是把 token 塞进请求头。
 *
 * 注意 'Bearer ' 这个前缀加空格 —— 后端拦截器 JwtInterceptor 是按它切字符串的，
 * 少了它一律按「没带 token」处理。现象是：登录明明成功了，
 * 下一个接口还是回 401请先登录 —— 新手会以为是登录没成功，其实是这里少写了。
 */
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：每个响应回来之后先过这里。
 *
 * 这里做了两件关键的事：
 *
 * 【一】把两条路并成一条。
 * axios 默认把 2xx 交给 then、4xx/5xx 扔进 catch。后端那边业务错误
 * 是真的返回 404 / 400 的（GlobalExceptionHandler 里改成 ResponseEntity 了），
 * 所以不处理的话，页面里每个调用点都要在 then 里判一次 code、
 * 在 catch 里再判一次 —— 迟早漏一个，然后出现「有的错误弹了提示，有的悄无声息」。
 * 这里统一成「不管成功失败，都返回后端那个 body」，页面只认 res.code。
 *
 * 【二】401 集中处理。
 * token 过期、被改过、没登录，后端都回 401。清掉本地 token 并跳登录页 ——
 * 这是全项目唯一需要处理「登录失效」的地方，写在页面里会到处漏。
 */
request.interceptors.response.use(
  // 成功：res.data 就是后端那个 { code, message, data }
  (res) => res.data,

  // 失败：后端返回了 4xx/5xx，或者压根没连上
  (err) => {
    // 没有 response = 请求根本到不了后端。最常见的原因就是后端没启动。
    if (!err.response) {
      return { code: -1, message: '连不上后端，检查一下 Spring Boot 起了没', data: null }
    }

    if (err.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 已经是登录页就别重复跳了，否则会刷屏
      if (!location.hash.startsWith('#/login')) {
        location.hash = '#/login'
      }
    }

    // 也返回 body，交给页面统一判断 code
    return err.response.data
  },
)

export default request
