<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import { listCategories } from '../api/category'
import { listPosts } from '../api/post'
import { useUserStore } from '../store/user'
import { authorOf, formatTime } from '../utils/format'

/**
 * 首页：委托板。
 *
 * 这个页面能拿到数据，说明整条链路是通的：
 * 浏览器 → vite 代理 → Spring Boot → MySQL → 原路返回。
 * 所以它是搭前端时第一个该跑通的页面，出问题也好定位是断在哪一段。
 */
const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const posts = ref([])
const total = ref(0)
const loading = ref(false)

const pageNum = ref(1)
const pageSize = ref(10)
const categoryId = ref(null)

/**
 * 板块 id → 板块名。
 *
 * 【为什么要这么一张表】
 * 帖子数据里只有 categoryId 这个数字，接口没把板块名一起返回。
 * 想在列表上显示「技术」而不是「2」，要么后端加字段，要么前端本地映射。
 * 板块列表本来就已经为了上面的筛选器查回来了，本地拼一张表最省事 ——
 * 不用为了显示两个字去改后端。
 *
 * computed 而不是普通对象：categories 是异步来的，刚进页面时是空数组。
 * computed 会在它到了之后自动重算；普通对象只在初始化时算一次，永远是空的。
 */
const categoryName = computed(() => {
  const map = {}
  for (const c of categories.value) map[c.id] = c.name
  return map
})

/**
 * 板块 → 徽记的样式。
 *
 * 【为什么按「名字」分色，而不是按 id】
 * id 是数据库自增的，换台机器重建库就可能变；名字是 init.sql 里写死的三个。
 * 按名字分色，换库之后颜色也不会错位。
 *
 * 三个板块各给一个字和一种颜色，列表扫下去一眼能分清是哪类委托。
 * 认不出来的板块（以后加了新的）退回中性色 + 名字首字，不会没样式。
 */
const CATEGORY_THEME = {
  日常: { glyph: '常', color: '#4a7c59', bg: '#e8f0e5', line: 'rgba(74,124,89,0.3)' },
  技术: { glyph: '技', color: '#2f5d8a', bg: '#e4ecf3', line: 'rgba(47,93,138,0.3)' },
  水贴: { glyph: '水', color: '#8a6d1f', bg: '#f7ecd0', line: 'rgba(184,137,43,0.34)' },
}

function themeOf(id) {
  const name = categoryName.value[id]
  return (
    CATEGORY_THEME[name] || {
      glyph: name ? name.charAt(0) : '帖',
      color: '#766b58',
      bg: '#f1ece1',
      line: 'rgba(118,107,88,0.28)',
    }
  )
}

/** 把 theme 的三个颜色拼成内联样式，模板里就不用写一长串三元 */
function sealStyle(id) {
  const t = themeOf(id)
  return { color: t.color, background: t.bg, boxShadow: `inset 0 0 0 1px ${t.line}` }
}

/**
 * 卡片左侧那条板块色边。
 *
 * 卡片本来铺满整行，扫列表时看不出「这条属于哪个板块」——
 * 左边压一道板块色，视线不用移到中间那枚徽记上就能分出来。
 * 颜色直接取 CATEGORY_THEME，跟徽记同源，不会两处对不上。
 */
function accentStyle(id) {
  return { background: themeOf(id).color }
}

async function load() {
  loading.value = true
  const res = await listPosts({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    // ⚠️ 这里是 ?? undefined，不是 ?? null。
    // axios 遇到 params 里值是 undefined 会**整个字段丢掉**，
    // 而 null 会被拼成 categoryId=null 发出去 —— 后端 Long 接不了 "null"，
    // 直接回 400。这个坑很安静：选「全部」时才触发。
    categoryId: categoryId.value ?? undefined,
  })
  loading.value = false

  if (res.code !== 200) {
    ElMessage.error(res.message)
    return
  }
  posts.value = res.data.records
  total.value = res.data.total
}

async function loadCategories() {
  const res = await listCategories()
  if (res.code === 200) {
    categories.value = res.data
  }
}

function handleCategoryChange() {
  // 换了板块就回第一页。不回的话你在第 3 页选了「技术」，
  // 而技术板块总共只有 1 页，页面会显示成空的。
  pageNum.value = 1
  load()
}

function handlePageChange(p) {
  pageNum.value = p
  load()
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<template>
  <div class="page">
    <!--
      页头 = 公会告示牌。写清楚这是干什么的地方，
      未登录的人进来也不至于一脸茫然地对着一个光秃秃的列表。
    -->
    <section class="hero fade-up">
      <div class="hero-main">
        <span class="hero-badge">公会委托板</span>
        <h1>今日<em>委托</em></h1>
        <p>写下你想说的 —— 求助、闲聊、见闻，都算一次出发。</p>
      </div>

      <div class="hero-side">
        <div class="stat">
          <strong>{{ total }}</strong>
          <span>份委托</span>
        </div>
        <div class="stat">
          <strong>{{ categories.length }}</strong>
          <span>个板块</span>
        </div>
      </div>
    </section>

    <!-- 板块筛选：一排木牌 -->
    <div class="filter">
      <span class="filter-label">板块</span>
      <el-radio-group v-model="categoryId" @change="handleCategoryChange">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button v-for="c in categories" :key="c.id" :value="c.id">
          {{ c.name }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!--
      桌面端分成两栏：左边委托列表，右边一块说明栏。
      单栏在 1280 宽的屏上会空掉一大片，看着像没加载完。
      窄屏（<900px）下面那段 media 会把侧栏收到列表下方，不会挤成两列。
    -->
    <div class="board">
      <div v-loading="loading" class="list">
        <EmptyState
          v-if="!loading && posts.length === 0"
          glyph="空"
          text="这个板块还没有委托，来挂第一份"
        />

        <!--
          逐条淡入：animationDelay 按下标递增，错开 45ms 一条。
          整页同时闪一下会比较吵，错开之后像是一条条挂上板子。
          上限压到 8 条 —— 第 10 条要等 450ms 才出现，就开始显得卡了。
        -->
        <article
          v-for="(post, i) in posts"
          :key="post.id"
          class="post fade-up"
          :style="{ animationDelay: `${Math.min(i, 8) * 45}ms` }"
          @click="router.push(`/post/${post.id}`)"
        >
          <!-- 左侧板块色边 -->
          <span class="accent" :style="accentStyle(post.categoryId)"></span>

          <!-- 板块徽记：一个字 + 一种颜色，扫列表时最好认 -->
          <span class="seal post-seal" :style="sealStyle(post.categoryId)">
            {{ themeOf(post.categoryId).glyph }}
          </span>

          <div class="post-body">
            <h3 class="post-title">{{ post.title }}</h3>
            <div class="post-meta">
              <!--
                作者身份牌。显示 nickname（昵称，可重复）；username（唯一）只用来算头像色，
                免得两个撞昵称的人长得一模一样。
                两个名字都来自后端 PostVO（Service 批量查 user 表回填）。
              -->
              <span class="who">
                <i
                  class="avatar"
                  :style="{ background: authorOf(post.userId, post.nickname, post.username).color }"
                >
                  {{ authorOf(post.userId, post.nickname, post.username).initial }}
                </i>
                {{ authorOf(post.userId, post.nickname, post.username).name }}
              </span>
              <span v-if="categoryName[post.categoryId]" class="chip">
                {{ categoryName[post.categoryId] }}
              </span>
              <span>{{ formatTime(post.createdAt) }}</span>
              <span class="no">NO.{{ post.id }}</span>
            </div>
          </div>

          <span class="arrow">→</span>
        </article>

        <div class="pager">
          <el-pagination
            v-if="total > pageSize"
            layout="prev, pager, next"
            :total="total"
            :current-page="pageNum"
            :page-size="pageSize"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <aside class="side">
        <div class="panel side-card">
          <span class="eps">布告</span>
          <p class="side-text">
            协会记录每一次出发。发帖、回帖、看别人的见闻 —— 都算。
          </p>
          <el-button
            v-if="!userStore.isLogin"
            type="primary"
            round
            class="side-btn"
            @click="router.push('/register')"
          >
            登记入会
          </el-button>
          <el-button v-else type="primary" round class="side-btn" @click="router.push('/publish')">
            写一份新委托
          </el-button>
        </div>

        <div class="panel side-card">
          <span class="eps">规矩</span>
          <ul class="rules">
            <li>先审后发：新委托要管理员核过才上板</li>
            <li>一人一帖一留言，友善发言</li>
            <li>正文里的换行会原样保留</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* ---- 告示牌 ----
   暖纸底 + 右上角一圈金光，右边缘压一道金线，像一块镶边挂起来的木牌 */
.hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 40px 28px;
  margin-bottom: 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 253, 248, 0.97), rgba(245, 237, 220, 0.92));
  border: 1px solid var(--c-line-strong);
  /* 右边一道金色竖边 + 上面一条受光高光：像一块挂起来、镶了铜边的木牌 */
  box-shadow:
    inset -4px 0 0 rgba(184, 137, 43, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    var(--shadow-md);
}

/* 左侧一道墨蓝竖条，和金边呼应，整块告示牌不至于只有暖色 */
.hero::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, var(--c-primary-light), var(--c-primary-dark));
}

/* 右上角的光晕 */
.hero::after {
  content: '';
  position: absolute;
  right: -70px;
  top: -90px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(184, 137, 43, 0.24), transparent 68%);
  pointer-events: none;
}

.hero-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.16em;
  color: #7d5a14;
  background: var(--c-gold-soft);
  border: 1px solid var(--c-gold-line);
  padding: 3px 11px;
  border-radius: var(--radius-full);
}

.hero h1 {
  margin: 13px 0 7px;
  font-size: 34px;
  letter-spacing: 0.04em;
}

/* em 用墨蓝 + 一条金色记号笔，在标题里挑一个词强调 */
.hero h1 em {
  font-style: normal;
  color: var(--c-primary);
  background: linear-gradient(transparent 62%, rgba(217, 178, 94, 0.55) 62%);
  padding: 0 2px;
}

.hero p {
  margin: 0;
  color: var(--c-ink-3);
  font-size: 13px;
}

/* ---- 右下角两个数字 ---- */
.hero-side {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 78px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: rgba(255, 253, 248, 0.9);
  border: 1px solid var(--c-line);
  box-shadow: var(--shadow-sm);
}

.stat strong {
  font-family: var(--font-display);
  font-size: 24px;
  line-height: 1.1;
  color: var(--c-primary);
  font-variant-numeric: tabular-nums;
}

.stat span {
  font-size: 11px;
  color: var(--c-ink-4);
  margin-top: 2px;
}

/* ---- 筛选条 ---- */
.filter {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 14px;
  border-radius: var(--radius-lg);
  background: var(--c-surface);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--c-line);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.7),
    var(--shadow-sm);
  overflow-x: auto;
}

/* 「板块」两个字：小号、宽字距，像登记表上的字段名，和右边的按钮串区分开 */
.filter-label {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--c-gold);
}

/* 窄屏时板块多，允许横滑但别露出那条灰滚动条 */
.filter::-webkit-scrollbar {
  display: none;
}

/* ---- 两栏 ---- */
.board {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.list {
  flex: 1;
  min-width: 0;
  min-height: 120px;
}

/* ---- 右侧说明栏 ---- */
.side {
  flex: 0 0 250px;
  position: sticky;
  top: calc(var(--topbar-h) + 16px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-card {
  padding: 16px 18px;
}

/* 侧栏小标题：金色小字，和 .eyebrow 同一套语气 */
.eps {
  display: inline-block;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--c-gold);
}

.side-text {
  margin: 9px 0 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--c-ink-3);
}

.side-btn {
  width: 100%;
  margin-top: 13px;
}

.rules {
  margin: 9px 0 0;
  padding-left: 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--c-ink-3);
}

.rules li + li {
  margin-top: 5px;
}

/* ---- 委托列表 ---- */
.post {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px 16px 22px;
  margin-bottom: 10px;
  border-radius: var(--radius-lg);
  background: var(--c-surface);
  backdrop-filter: saturate(140%) blur(10px);
  -webkit-backdrop-filter: saturate(140%) blur(10px);
  border: 1px solid var(--c-line);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.7),
    var(--shadow-sm);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform var(--t) var(--ease),
    box-shadow var(--t) var(--ease),
    border-color var(--t) var(--ease);
}

/* 左侧板块色边。颜色由模板里的 accentStyle 内联给，这里只定位置和尺寸 */
.accent {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
}

/* 悬停：整条抬起 2px，边框镀金，右侧箭头滑出来 */
.post:hover {
  transform: translateY(-2px);
  border-color: var(--c-gold-line);
  box-shadow:
    var(--shadow-lg),
    inset 0 0 0 1px var(--c-gold-ring);
}

.post:hover .post-title {
  color: var(--c-primary);
}

.post:hover .arrow {
  opacity: 1;
  transform: translateX(0);
}

.post-seal {
  width: 42px;
  height: 42px;
  font-size: 18px;
}

.post-body {
  flex: 1;
  min-width: 0;
}

.post-title {
  margin: 0 0 5px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--c-ink);
  transition: color var(--t) var(--ease);
  /* 标题太长就省略。不截断的话长标题会撑成两行，一列卡片就不齐了 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--c-ink-4);
}

/* ---- 作者身份牌 ----
   小圆徽记 + 名字。和顶栏那个会员木牌是同一套做法，
   同一作者在列表里每次都长一样（颜色由 userId 推出来）。 */
.who {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 500;
  color: var(--c-ink-3);
}

.avatar {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  flex-shrink: 0;
  font-family: var(--font-display);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  color: #fdf6e6;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35);
}

/* 板块小印 */
.chip {
  padding: 1px 9px;
  border-radius: var(--radius-full);
  background: var(--c-gold-soft);
  color: #7d5a14;
  border: 1px solid rgba(184, 137, 43, 0.24);
  font-weight: 500;
  font-size: 11.5px;
}

/* 委托编号：像档案上的钢印编号 */
.no {
  margin-left: auto;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--c-ink-4);
  font-variant-numeric: tabular-nums;
}

.arrow {
  flex-shrink: 0;
  color: var(--c-gold);
  font-size: 16px;
  opacity: 0;
  transform: translateX(-6px);
  transition:
    opacity var(--t) var(--ease),
    transform var(--t) var(--ease);
}

/* ---- 分页 ---- */
.pager {
  display: flex;
  justify-content: center;
  margin: 24px 0 0;
}

/* ---- 中屏：两栏并排时每列都太窄，侧栏收到列表下方 ---- */
@media (max-width: 900px) {
  .board {
    display: block;
  }

  .side {
    position: static;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    margin-top: 4px;
  }
}

/* ---- 窄屏：数字块换行，卡片收一收内边距 ---- */
@media (max-width: 640px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px 20px 20px 26px;
    gap: 16px;
  }

  .hero h1 {
    font-size: 26px;
  }

  .post {
    padding: 14px 14px 14px 18px;
    gap: 12px;
  }

  .post-seal {
    width: 36px;
    height: 36px;
    font-size: 16px;
  }

  .no {
    display: none;
  }
}
</style>
