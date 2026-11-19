import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 路由表：哪个网址显示哪个 .vue。
 *
 * 【为什么用 hash 模式（网址里那个 #）】
 * createWebHashHistory 生成的网址长这样：http://localhost:5173/#/post/1
 * createWebHistory 的话是：http://localhost:5173/post/1
 *
 * 后者好看，但它要求「服务器对任何路径都返回 index.html」。
 * 用户直接在地址栏敲 /post/1 回车时，服务器得知道这不是来找文件、
 * 而是来找前端页面的。开发期的 Vite 和以后的 Nginx 都得专门配。
 * 前端还没上线、Nginx 还没搭，先用 hash —— 零配置，不会踩这个坑。
 *
 * 【component 为什么写成箭头函数】
 * () => import('../views/Home.vue') 是「用到这个页面时才去下载它」。
 * 写成 import Home from '../views/Home.vue' 的话，所有页面会被打进
 * 同一个 js 文件，用户想看首页却得先下载「发帖页 + 个人中心」。
 * 这叫路由懒加载，页面多了以后差别很明显。
 */
const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  // 集市（商品列表）。游客也能看，所以不加 meta.needLogin
  { path: '/market', name: 'market', component: () => import('../views/Market.vue') },
  // 店铺主页。:id 是数字店铺 id，后端 /shop/{id} 在白名单里，游客可看
  { path: '/shop/:id', name: 'shop', component: () => import('../views/Shop.vue') },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  { path: '/post/:id', name: 'postDetail', component: () => import('../views/PostDetail.vue') },
  {
    path: '/publish',
    name: 'postEdit',
    component: () => import('../views/PostEdit.vue'),
    meta: { needLogin: true },
  },
  {
    path: '/my',
    name: 'userCenter',
    component: () => import('../views/UserCenter.vue'),
    meta: { needLogin: true },
  },
  {
    path: '/audit',
    name: 'audit',
    component: () => import('../views/Audit.vue'),
    meta: { needLogin: true, needAdmin: true },
  },
  // 兜底：敲了不存在的网址，回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

/**
 * 全局前置守卫：每次跳转之前跑一遍。
 *
 * 这里是**前端的第一道门，不是安全措施**。
 * 真正拦人的是后端的 JwtInterceptor —— 用户完全可以手改 localStorage
 * 里的 token 绕过这里，然后照样被后端打回 401。
 * 这道守卫的价值是「体验」：没登录的人点到 /publish，
 * 直接把他送去登录页，而不是让他填完一整篇帖子才被告知没权限。
 */
router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.needLogin && !token) {
    // 把原目标塞进 query，登录成功后跳回去 —— 不然用户得自己重新点一遍
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.needAdmin) {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    if (user?.role !== 'ADMIN') {
      return { path: '/' }
    }
  }
})

export default router
