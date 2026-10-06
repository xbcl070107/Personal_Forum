import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './style.css'

/**
 * 前端入口。
 *
 * 三件事：
 * 1. createPinia() —— 状态管理。我们的用法很简单：存 token 和当前用户。
 * 2. router —— 页面跳转。谁是指向哪个 .vue 的，写在 src/router/index.js。
 * 3. ElementPlus —— 组件库。分页、表单、弹消息框都用它，不用自己写。
 *
 * 这三行必须**在这里**执行，因为 main.js 是整个应用的第一个文件，
 * 任何页面被渲染之前它先跑完。所以注册要放在这儿，页面里才用得上。
 *
 * locale: zhCn 是让 Element Plus 自带的文案显示中文 ——
 * 分页组件默认写的是 "Total 20"，传了它才是「共 20 条」。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
