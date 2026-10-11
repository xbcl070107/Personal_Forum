<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

/**
 * 注册页（登记入会）。
 *
 * 注册成功**不自动登录**，跳回登录页让用户登一次。
 * 后端 /auth/register 返回的 data 是 null，也没给 token，
 * 想自动登录就得前端再拿刚填的账号密码去调一次 /auth/login —— 能做，
 * 但会让「注册」这个动作背后偷偷藏一次登录，排查问题时看不见。
 * 让用户多点一下，换一个清楚的流程。
 */
const router = useRouter()

const form = ref({ username: '', nickname: '', password: '', confirm: '' })
const loading = ref(false)

/**
 * 两次密码是否一致，只用来给「确认密码」那一栏加个即时提示。
 * 提交时的校验还是要留一份 —— 这里只是提前告诉用户，
 * 不能拿它当校验，因为用户完全可以在不一致的状态下按回车。
 */
const mismatch = computed(
  () => !!form.value.confirm && form.value.password !== form.value.confirm,
)

async function handleSubmit() {
  if (!form.value.username || !form.value.nickname || !form.value.password) {
    ElMessage.warning('用户名、昵称和密码都要填')
    return
  }
  if (form.value.password !== form.value.confirm) {
    ElMessage.warning('两次输入的密码不一样')
    return
  }

  loading.value = true
  // 只把后端要的三个字段发过去。confirm 是前端自己用的，
  // 发过去后端也没有这个字段，多余的数据只是噪音
  const res = await register({
    username: form.value.username,
    nickname: form.value.nickname,
    password: form.value.password,
  })
  loading.value = false

  if (res.code !== 200) {
    // 用户名重复、密码太短这些校验都在后端，它返回什么就显示什么
    ElMessage.error(res.message)
    return
  }

  ElMessage.success('登记成功，去登录吧')
  router.push('/login')
}
</script>

<template>
  <div class="auth">
    <div class="auth-card fade-up">
      <!-- 招牌用公会金，和登录页的墨蓝区分开：一眼能看出这是哪一步 -->
      <div class="auth-banner">
        <span class="auth-mark">新</span>
        <h1>登记入会</h1>
        <p class="auth-sub">JOIN THE GUILD</p>
      </div>

      <div class="auth-body">
        <el-form label-position="top" @submit.prevent @keyup.enter="handleSubmit">
          <!--
            用户名和昵称是两件事，所以在表单上也分开、并且各自写清用途：
              username —— 登录凭证，全站唯一，不能重
              nickname —— 展示名，会出现在帖子和评论上，允许重名
            只写「用户名」两个字的话，用户会以为这就是他显示出来的名字，
            等发现顶栏显示的是另一个才回头改。
          -->
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              size="large"
              maxlength="32"
              placeholder="登录用的账号，不能重复"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="昵称">
            <el-input
              v-model="form.nickname"
              size="large"
              maxlength="32"
              placeholder="别人看到的名字，可以重名"
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              show-password
              placeholder="设置一个密码"
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item label="确认密码">
            <el-input
              v-model="form.confirm"
              type="password"
              size="large"
              show-password
              placeholder="再输一遍"
              autocomplete="new-password"
            />
            <!-- 不一致时立刻说，不用等点提交 -->
            <div v-if="mismatch" class="hint-error">两次输入的密码不一样</div>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="handleSubmit"
          >
            提交登记
          </el-button>
        </el-form>

        <div class="tip">
          已经有身份牌了？
          <el-link type="primary" :underline="false" @click="router.push('/login')">
            去登录
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth {
  display: flex;
  justify-content: center;
  padding: 44px 20px 76px;
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

/* ---- 招牌：羊皮纸压金 ---- */
.auth-banner {
  position: relative;
  overflow: hidden;
  padding: 26px 28px 22px;
  text-align: center;
  color: #4a3410;
  background: linear-gradient(150deg, #f0d89a, #d9b25e);
}

.auth-banner::after {
  content: '';
  position: absolute;
  inset: -45% -12% auto auto;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.5), transparent 70%);
  pointer-events: none;
}

/* 一圈内框，公会牌子的做法 */
.auth-banner::before {
  content: '';
  position: absolute;
  inset: 9px;
  border: 1px solid rgba(122, 88, 20, 0.35);
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
  background: linear-gradient(135deg, #4a7c9c, #24476b);
  color: #fdf6e6;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.4),
    0 6px 16px -6px rgba(74, 52, 16, 0.55);
}

.auth-banner h1 {
  position: relative;
  z-index: 1;
  margin: 0 0 6px;
  font-size: 21px;
  letter-spacing: 0.08em;
  color: #3d2b0d;
}

.auth-sub {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 9.5px;
  letter-spacing: 0.28em;
  font-weight: 600;
  color: rgba(61, 43, 13, 0.72);
}

/* ---- 表单区 ---- */
.auth-body {
  padding: 22px 28px 26px;
}

.auth-body :deep(.el-form-item) {
  margin-bottom: 16px;
}

.hint-error {
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--c-danger);
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
    padding: 22px 14px 52px;
  }

  .auth-body {
    padding: 20px 20px 22px;
  }
}
</style>
