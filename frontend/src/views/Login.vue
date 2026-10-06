<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useUserStore } from '../store/user'

/**
 * 登录页。
 *
 * 登录成功的动作只有三步，顺序不能换：
 * 1. 把后端返回的 token 和用户信息存起来（userStore.setLogin）
 * 2. 提示一下
 * 3. 跳走 —— 跳到哪看地址栏的 ?redirect=，没有就回首页
 *
 * 第 1 步必须在前，因为 request.js 的请求拦截器是**每次请求现读
 * localStorage**。只要 token 存进去了，后面所有请求自动带上。
 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleSubmit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('用户名和密码都要填')
    return
  }

  loading.value = true
  const res = await login(form.value)
  loading.value = false

  if (res.code !== 200) {
    // 后端返回的 message 就是给用户看的（比如「用户名或密码错误」），
    // 直接用它，别在前端自己编一句
    ElMessage.error(res.message)
    return
  }

  userStore.setLogin(res.data)
  ElMessage.success('登录成功')

  // route.query.redirect 是路由守卫塞进来的原目标，没有就回首页。
  // 类型上它可能是数组（同名参数出现两次），这里只取一个字符串
  const target = route.query.redirect
  router.push(typeof target === 'string' && target ? target : '/')
}
</script>

<template>
  <div class="auth">
    <div class="auth-card fade-up">
      <!-- 顶部招牌：墨蓝底 + 金内框，像挂在门口的一块牌子 -->
      <div class="auth-banner">
        <span class="auth-mark">协</span>
        <h1>欢迎回来</h1>
        <p class="auth-sub">ADVENTURERS GUILD</p>
      </div>

      <div class="auth-body">
        <!--
          @keyup.enter 挂在 el-form 上而不是每个 el-input 上：
          原生 keyup 事件会冒泡到表单这一层，写一次所有输入框都管用，
          以后加字段也不会漏掉那个忘了绑回车的
        -->
        <el-form label-position="top" @submit.prevent @keyup.enter="handleSubmit">
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="handleSubmit"
          >
            进入协会
          </el-button>
        </el-form>

        <div class="tip">
          还没有身份牌？
          <el-link type="primary" :underline="false" @click="router.push('/register')">
            登记入会
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 登录页不用 .page 那套左右贴边的布局，整块居中，上下留出呼吸空间 */
.auth {
  display: flex;
  justify-content: center;
  padding: 52px 20px 76px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  border-radius: 18px;
  overflow: hidden;
  background: var(--c-surface-solid);
  border: 1px solid var(--c-line-strong);
  box-shadow: var(--shadow-lg);
}

/* ---- 招牌 ---- */
.auth-banner {
  position: relative;
  overflow: hidden;
  padding: 28px 28px 24px;
  text-align: center;
  color: #fdf6e6;
  /* 墨蓝渐变的皮革封面 */
  background: linear-gradient(150deg, #35648f, #1e3a55);
}

/*
 * 两道装饰：
 * 1. 右上角一团高光，让这块扁平的渐变有受光感
 * 2. 中间一圈金色内框 —— 公会牌子的做法
 */
.auth-banner::after {
  content: '';
  position: absolute;
  inset: -45% -12% auto auto;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(217, 178, 94, 0.35), transparent 70%);
  pointer-events: none;
}

.auth-banner::before {
  content: '';
  position: absolute;
  inset: 9px;
  border: 1px solid rgba(217, 178, 94, 0.38);
  border-radius: 12px;
  pointer-events: none;
}

.auth-mark {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin: 0 auto 12px;
  border-radius: 14px;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #d9b25e, #b8892b);
  color: #2b2110;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.45),
    0 6px 16px -6px rgba(0, 0, 0, 0.5);
}

.auth-banner h1 {
  position: relative;
  z-index: 1;
  margin: 0 0 6px;
  font-size: 21px;
  letter-spacing: 0.08em;
  color: #fdf6e6;
}

/* 宽字距的英文副题，像铭牌上的刻字 */
.auth-sub {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 9.5px;
  letter-spacing: 0.28em;
  color: rgba(217, 178, 94, 0.9);
  font-weight: 600;
}

/* ---- 表单区 ---- */
.auth-body {
  padding: 24px 28px 26px;
}

.auth-body :deep(.el-form-item) {
  margin-bottom: 18px;
}

.submit {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 0.14em;
}

.tip {
  margin-top: 18px;
  text-align: center;
  color: var(--c-ink-3);
  font-size: 13px;
}

@media (max-width: 640px) {
  .auth {
    padding: 26px 14px 52px;
  }

  .auth-body {
    padding: 20px 20px 22px;
  }
}
</style>
