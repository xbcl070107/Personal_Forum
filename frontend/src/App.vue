<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from './store/user'
import { avatarColorOf } from './utils/format'

/**
 * 根组件：顶部导航 + 下面那块会变的区域。
 *
 * <router-view /> 是「占位符」—— 当前网址对应哪个页面组件，
 * 就把那个组件渲染在这个位置。所以每个页面自己只写页面内容，
 * 不用管导航栏。
 *
 * 这一层还负责给整站定调：顶栏是「公会办事处的门楣」，
 * 页脚是落款。两者都在这里，页面里不用重复。
 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/**
 * 当前在哪个栏目。用来给顶栏那个链接加「选中」样式。
 *
 * 判断依据是路由的 name（写在 router/index.js 里），不是网址字符串 ——
 * 帖子的网址长这样 /post/12，按前缀匹配也能写，但每加一个栏目就得多一条判断。
 * 用 name 的话栏目和路由一一对应，不会漏。
 */
const activeNav = computed(() => route.name)

/** 顶栏那组导航项。条件写在数据里，省得模板里嵌三层判断 */
const navItems = computed(() => {
  const items = []
  if (userStore.isLogin) {
    items.push({ name: 'postEdit', label: '发布委托', to: '/publish' })
    items.push({ name: 'userCenter', label: '我的委托', to: '/my' })
    if (userStore.isAdmin) {
      items.push({ name: 'audit', label: '审核台', to: '/audit' })
    }
  }
  return items
})

/**
 * 徽记里那个字：取**昵称**的首字，不是用户名的。
 *
 * 页面上显示的是昵称，徽记却显示用户名的首字，就会出现
 * 「圈里是 x、旁边写着禤布出来」这种对不上的情况。
 */
const initial = computed(() => {
  const name = userStore.displayName
  return name ? name.charAt(0).toUpperCase() : '?'
})

/**
 * 徽记配色：由**用户名**算出来（唯一），不用昵称。
 *
 * 昵称允许重复，两个人撞昵称时颜色会一模一样，列表里就分不出来了。
 * 用户名唯一，拿它做键才能保证「同一个人永远同色、不同的人不撞色」。
 *
 * 算法收在 utils/format.js 的 avatarColorOf —— 顶栏 / 帖子作者 / 评论作者
 * 全站共用这一个函数。
 */
const avatarStyle = computed(() => ({
  background: avatarColorOf(userStore.user?.username || userStore.displayName),
}))

/**
 * 退出前问一句。
 *
 * 之前点一下直接退，误点一次就得重新登 —— 而顶栏那排按钮挨得很近。
 * ElMessageBox.confirm 点「取消」时会 reject，所以必须 try/catch，
 * 不接的话每次取消控制台都多一个红错。
 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要离开协会吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '再想想',
    })
  } catch {
    return // 点了取消，什么都不做
  }

  userStore.logout()
  ElMessage.success('已退出')
  router.push('/')
}

/**
 * 切页面时回到顶部。
 *
 * 【为什么需要这一行】
 * vue-router 只管换组件，不管滚动条。在首页翻到第 5 条委托点进去，
 * 详情页会**从同样的滚动位置**开始显示 —— 也就是直接落在正文中间，
 * 看着像页面没加载好。列表和详情高度又不一样，所以每次都要归零。
 *
 * 用 watch(route.path) 而不是 scrollBehavior：我们用的是 hash 模式，
 * scrollBehavior 在「同路径不同 query」这类情况下行为不好把握，
 * 显式归零更好预测。
 */
watch(
  () => route.path,
  () => window.scrollTo({ top: 0, behavior: 'auto' }),
)
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="topbar-inner">
        <!-- 品牌区：一枚火漆徽记 + 公会名。点哪儿都能回首页 -->
        <div class="brand" @click="router.push('/')">
          <span class="brand-mark">协</span>
          <span class="brand-text">
            <strong>冒险者协会</strong>
            <small>ADVENTURERS GUILD</small>
          </span>
        </div>

        <nav class="nav">
          <!--
            集市是**游客也能进**的地方，所以它写在下面前后分支的外面，
            不走 navItems 那套「登录后才显示」的逻辑。
            想加其他公开栏目（比如以后的活动页），照这个位置放。
          -->
          <router-link
            to="/market"
            class="nav-link"
            :class="{ active: activeNav === 'market' || activeNav === 'shop' }"
          >
            集市
          </router-link>

          <!--
            v-if / v-else 决定登录前后顶栏长什么样。
            userStore.isLogin 是 Pinia 里的 computed，登录 / 退出时它会自动变，
            这个模板跟着自动重渲染 —— 这就是用 Pinia 而不是直接读 localStorage 的原因。
          -->
          <template v-if="userStore.isLogin">
            <router-link
              v-for="item in navItems"
              :key="item.name"
              :to="item.to"
              class="nav-link"
              :class="{ active: activeNav === item.name }"
            >
              {{ item.label }}
            </router-link>

            <!-- 会员木牌：徽记 + 名字。管理员多一枚金色小印 -->
            <!--
              会员木牌：徽记 + 昵称。管理员多一枚金色小印。
              显示的是 nickname（昵称，可重复），悬停才露出 username（登录名，唯一）——
              昵称可能会撞，想确认「到底是哪个账号」时用它。
            -->
            <span class="who" :title="`@${userStore.user?.username || ''}`">
              <span class="avatar" :style="avatarStyle">{{ initial }}</span>
              <span class="who-name">{{ userStore.displayName }}</span>
              <span v-if="userStore.isAdmin" class="badge-admin">管理员</span>
            </span>

            <el-button text @click="handleLogout">退出</el-button>
          </template>

          <template v-else>
            <el-button text @click="router.push('/login')">登录</el-button>
            <el-button type="primary" round size="small" @click="router.push('/register')">
              登记入会
            </el-button>
          </template>
        </nav>
      </div>
    </header>

    <main class="main">
      <router-view />
    </main>

    <footer class="footer">
      <span class="footer-ornament"><i></i></span>
      <p class="footer-text">
        冒险者协会 · 记录每一次出发
      </p>
    </footer>
  </div>
</template>

<style scoped>
/* 撑满整屏的竖排布局：main 用 flex:1 顶开，页脚始终贴底 */
.app {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/*
 * ---- 门楣 ----
 * 平涂纸底 + 一条底线。没有光晕、没有金线、没有模糊：
 * 顶栏是「页眉」，不是招牌，越安静越好。
 */
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--c-bg);
  border-bottom: 1px solid var(--c-line-strong);
}

.topbar-inner {
  max-width: var(--page-w);
  margin: 0 auto;
  padding: 0 20px;
  height: var(--topbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

/* ---- 徽记：一方描边的印章。悬停才填实 ---- */
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.brand-mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--c-primary);
  border-radius: var(--radius-sm);
  color: var(--c-primary);
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 16px;
  line-height: 1;
  transition:
    background-color var(--t) var(--ease),
    color var(--t) var(--ease);
}

.brand:hover .brand-mark {
  background: var(--c-primary);
  color: var(--c-surface-solid);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-text strong {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--c-ink);
}

/* 英文小字做副题，字距拉开，像出版物的落款 */
.brand-text small {
  font-size: 8.5px;
  letter-spacing: 0.22em;
  color: var(--c-ink-4);
  font-weight: 600;
}

/* ---- 导航 ---- */
.nav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-link {
  position: relative;
  padding: 8px 11px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  color: var(--c-ink-3);
  transition:
    background var(--t) var(--ease),
    color var(--t) var(--ease);
}

.nav-link:hover {
  background: var(--c-primary-soft);
  color: var(--c-primary);
  text-decoration: none;
}

/* 当前栏目：一行字 + 一条压在底边的实线 */
.nav-link.active {
  color: var(--c-ink);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  left: 11px;
  right: 11px;
  bottom: -1px;
  height: 1px;
  background: var(--c-ink);
}

/* ---- 会员牌 ---- */
.who {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 6px 0 12px;
  padding: 2px 8px 2px 2px;
  border-radius: var(--radius-full);
  background: transparent;
  border: 1px solid var(--c-line);
}

.avatar {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-full);
  display: grid;
  place-items: center;
  color: #fdfcf8;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.who-name {
  font-size: 13px;
  color: var(--c-ink-2);
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 管理员标记：只有一行小字，不再做成金色小印 */
.badge-admin {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  padding: 0 5px;
  border-radius: var(--radius-sm);
  color: var(--c-gold);
  background: var(--c-gold-soft);
}

/* ---- 主体 ---- */
.main {
  flex: 1;
}

/* ---- 落款 ---- */
.footer {
  padding: 10px 20px 32px;
  text-align: center;
}

.footer-ornament {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 0 auto 14px;
  max-width: 420px;
}

.footer-ornament::before,
.footer-ornament::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--c-line);
}

.footer-ornament i {
  width: 5px;
  height: 5px;
  margin: 0 10px;
  transform: rotate(45deg);
  background: var(--c-line-strong);
  flex-shrink: 0;
}

.footer-text {
  margin: 0;
  font-size: 12px;
  color: var(--c-ink-4);
}

.footer-sub {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  letter-spacing: 0.12em;
  color: var(--c-ink-4);
  opacity: 0.8;
}

/* ---- 窄屏：门楣上那排东西一行放不下 ---- */
@media (max-width: 640px) {
  .topbar-inner {
    padding: 0 14px;
    gap: 8px;
  }

  .brand-text small,
  .who-name,
  .badge-admin {
    display: none;
  }

  .who {
    margin: 0 2px 0 4px;
    padding: 2px;
    border: none;
  }

  .nav-link {
    padding: 8px 8px;
    font-size: 13px;
  }

  .nav-link.active::after {
    left: 8px;
    right: 8px;
  }
}
</style>
