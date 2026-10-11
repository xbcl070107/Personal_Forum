/**
 * 临时用的假后端 —— 只为了截图看样式，验证完就删。
 *
 * 后端的 context-path 是 /dev-api，vite 代理原样转发，所以这里也带这个前缀。
 * 端口 9090 和后端一致，一个端口就能顶替真后端，前端代码一行都不用改。
 */
import http from 'node:http'

const pad = (n) => String(n).padStart(2, '0')

/** 造一个 "2026-09-29T14:35:14" 这种本地时间串 */
function iso(daysAgo, h, m) {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  d.setHours(h, m, 0, 0)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

const categories = [
  { id: 1, name: '日常', sortOrder: 1, createdAt: iso(30, 9, 0) },
  { id: 2, name: '技术', sortOrder: 2, createdAt: iso(30, 9, 0) },
  { id: 3, name: '水贴', sortOrder: 3, createdAt: iso(30, 9, 0) },
]

const TITLES = [
  '第一次用 Spring Boot 写完整项目，踩过的坑都记在这了',
  '今天在图书馆待到闭馆，效率意外地高',
  'MyBatis-Plus 的逻辑删除字段名到底能不能改？',
  '有没有人和我一样，写代码前先写注释再写实现',
  '关于论坛「先审后发」这个设计的一点想法',
  'MySQL 联合索引 (status, created_at) 为什么能走上',
  '求助：vue-router 用 hash 模式会不会有 SEO 问题',
  '摸鱼时间：大家都在听什么歌写代码',
  'JWT 拦截器里那句话，我改了三次才写对',
  '周末打算把前端样式重做一遍',
]

const posts = TITLES.map((title, i) => ({
  id: 118 - i,
  userId: (i % 3) + 1,
  title,
  content:
    '最近在写这个论坛，顺手把遇到的一些问题记了下来。\n\n' +
    '第一件事是跨域。前端 5173、后端 9090，端口不同就是跨域，最后是靠 vite 的 proxy 解决的 —— 让浏览器以为始终在跟 5173 说话，转发那一步交给开发服务器做。\n\n' +
    '第二件事是路由守卫和后端校验的关系。守卫只是体验层的，真正拦人的是后端的 JwtInterceptor。前端能做的只是别让用户白填一整篇帖子。',
  categoryId: (i % 3) + 1,
  status: 1,
  isDeleted: 0,
  createdAt: iso(i, 20 - (i % 8), (i * 7) % 60),
  updatedAt: iso(i, 20 - (i % 8), (i * 7) % 60),
}))

/** 我的帖子：三种状态都要有，才能看出颜色区分 */
const myPosts = [
  { ...posts[0], id: 118, status: 1 },
  { ...posts[1], id: 117, status: 0 },
  { ...posts[2], id: 116, status: 2 },
  { ...posts[3], id: 115, status: 1 },
  { ...posts[4], id: 114, status: 0 },
]

const comments = [
  {
    id: 1,
    userId: 2,
    username: 'linlin',
    content: 'proxy 那段我也踩过，一开始还以为是后端的 CORS 没配好。',
    createdAt: iso(1, 10, 12),
  },
  {
    id: 2,
    userId: 3,
    username: 'zhou',
    content: '写得很清楚，收藏了。\n顺便问一下，hash 模式上线之后换成 history 麻烦吗？',
    createdAt: iso(1, 15, 40),
  },
  {
    id: 3,
    userId: 4,
    username: 'Adventurer_77',
    content: '逻辑删除那个字段名真的不能改，我改过一次，deleteById 直接变成真删，数据没了还不报错。',
    createdAt: iso(0, 9, 5),
  },
]

const ok = (data) => ({ code: 200, message: 'success', data })

const routes = {
  '/dev-api/category/list': () => ok(categories),
  '/dev-api/post/list': (url) => {
    const categoryId = url.searchParams.get('categoryId')
    const list = categoryId ? posts.filter((p) => String(p.categoryId) === categoryId) : posts
    return ok({ records: list, total: list.length, pages: 1, current: 1, size: 10 })
  },
  '/dev-api/post/my': () => ok({ records: myPosts, total: myPosts.length, pages: 1, current: 1, size: 10 }),
  '/dev-api/post/pending': () => ok([posts[1], posts[3], posts[5]]),
  '/dev-api/comment/list': () => ok(comments),
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost:9090')
  const path = url.pathname

  res.setHeader('Content-Type', 'application/json; charset=utf-8')

  if (routes[path]) {
    res.writeHead(200)
    res.end(JSON.stringify(routes[path](url)))
    return
  }

  if (path.startsWith('/dev-api/post/')) {
    const id = Number(path.split('/').pop())
    const post = posts.find((p) => p.id === id) || { ...posts[0], id, status: 0 }
    res.writeHead(200)
    res.end(JSON.stringify(ok(post)))
    return
  }

  if (path === '/dev-api/auth/login') {
    res.writeHead(200)
    res.end(
      JSON.stringify(
        ok({ id: 1, username: 'xbcl', role: 'ADMIN', token: 'fake-token-for-screenshot' }),
      ),
    )
    return
  }

  res.writeHead(200)
  res.end(JSON.stringify(ok(null)))
})

server.listen(9090, () => console.log('mock backend on http://localhost:9090'))
