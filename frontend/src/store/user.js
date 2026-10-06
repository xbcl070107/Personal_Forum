import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 当前登录用户。
 *
 * 要解决的问题：登录成功后拿到的 token，必须活到「用户关掉浏览器之前」。
 * 但 Pinia 存的是内存，**一刷新页面就没了**。所以真正的存放处是
 * localStorage，Pinia 只是内存里的一份镜像 —— 初始值从 localStorage 读，
 * 登录时两边一起写，退出时两边一起清。
 *
 * 为什么不全靠 localStorage、不用 Pinia：
 * localStorage 是字符串，改了不会触发页面更新。比如退出登录后，
 * 顶部导航栏要立刻从「用户名 + 退出」变成「登录 / 注册」，
 * 那需要响应式数据 —— 这就是 Pinia 的部分。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  // JSON.parse 遇到 null 会抛异常，所以先兜一个 'null' 字符串
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  /** 登录了没。模板里判断顶部导航显示什么，用它 */
  const isLogin = computed(() => !!token.value)

  /** 是不是管理员。审核页面要用 */
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /**
   * 登录成功后调用。
   * 参数就是后端 /auth/login 返回的那段 data：{ id, username, role, token }
   */
  function setLogin(data) {
    token.value = data.token
    user.value = { id: data.id, username: data.username, role: data.role }
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  /** 退出登录，或者 token 失效时清场 */
  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isLogin, isAdmin, setLogin, logout }
})
