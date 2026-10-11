<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLike, toggleLike } from '../api/like'
import { getFavorite, toggleFavorite } from '../api/favorite'
import { useUserStore } from '../store/user'

/**
 * 点赞 / 收藏按钮组。挂在帖子详情页上。
 *
 * 【为什么这两个按钮写在同一个组件里】
 * 它们的形状、状态、失败处理完全一样，只有「叫什么名字」和「调哪个接口」不同。
 * 各写一个组件意味着那套加载 / 回滚 / 登录判断要抄两遍，改一处漏一处。
 * 所以这里用两张表（CONFIGS）描述差异，逻辑只留一份。
 *
 * 【为什么状态一律听后端的】
 * 后端是翻面接口 —— 它自己判断现在该赞还是该取消。前端如果自己
 * 「记住现在赞过没，然后翻一下」，记错一次（比如双击、或者多标签页）
 * 结果就整个反了。所以：
 *   - 进来先 GET 一次拿真实状态
 *   - 每次点击只上报「发生了点击」，状态取响应里的值
 *
 * 【为什么要乐观更新】
 * 等接口回来再改按钮，会有 100~300ms 的「点了没反应」。
 * 所以先自己翻一下让手感立刻跟上，失败了再翻回来。
 * 注意乐观更新只动「显示」，权威值永远来自后端响应。
 *
 * 【为什么待审 / 驳回的帖不显示这组按钮】
 * 后端 requireApproved 会直接回 404「帖子不存在」。按钮画出来又点不动，
 * 不如不画 —— 这个判断交给父组件（它知道 status），这里只提供 show 条件。
 */
const props = defineProps({
  /** 帖子 id */
  postId: { type: [Number, String], required: true },
  /**
   * 当前用户能不能操作这条帖：只有「已过审」的帖才为 true。
   * 待审 / 驳回的帖连作者自己都点不了（后端会 404），所以父组件要传 false。
   */
  enabled: { type: Boolean, default: true },
})

const router = useRouter()
const userStore = useUserStore()

/**
 * 两张表描述两个按钮的差异。
 * count 是后端数的总数，active 是「我赞过 / 收藏过没」。
 */
const state = ref({
  like: { active: false, count: 0, loading: false, pending: false },
  favorite: { active: false, count: 0, loading: false, pending: false },
})

const CONFIGS = {
  like: {
    get: getLike,
    toggle: toggleLike,
    field: 'liked',
    label: '赞同',
    activeLabel: '已赞同',
  },
  favorite: {
    get: getFavorite,
    toggle: toggleFavorite,
    field: 'favorited',
    label: '收藏',
    activeLabel: '已收藏',
  },
}

/**
 * 拉一次真实状态。
 *
 * ⚠️ 失败时**不弹提示**：点赞状态只是页面上的一个装饰，
 * 拉不到就保持「未点赞」，用户点一下会自动重试。
 * 为这个弹一个红条反而像页面出了问题。
 */
async function load(kind) {
  const cfg = CONFIGS[kind]
  const s = state.value[kind]
  s.loading = true
  const res = await cfg.get(props.postId)
  s.loading = false

  if (res.code !== 200) return // 静默，见上面的注释
  s.active = !!res.data[cfg.field]
  s.count = res.data.count
}

/**
 * 点一下。
 *
 * 顺序：乐观翻转 → 上报 → 用响应纠正 → 失败则回滚。
 * pending 是防止连点：翻面接口虽然能扛住双击（后端有唯一键兜底），
 * 但两个请求同时飞出去，回来的顺序不定，按钮会闪。
 */
async function toggle(kind) {
  // 没登录先去登录，并把当前页塞进 redirect，登录完跳回来
  if (!userStore.isLogin) {
    ElMessage.info('登录之后才能赞同和收藏')
    router.push({ path: '/login', query: { redirect: `/post/${props.postId}` } })
    return
  }

  const cfg = CONFIGS[kind]
  const s = state.value[kind]
  if (s.pending || s.loading) return

  const before = { active: s.active, count: s.count }
  // 乐观翻转：先让按钮动起来
  s.active = !s.active
  s.count += s.active ? 1 : -1
  s.pending = true

  const res = await cfg.toggle(props.postId)
  s.pending = false

  if (res.code !== 200) {
    // 回滚到点之前的样子，再告诉用户为什么
    s.active = before.active
    s.count = before.count
    ElMessage.error(res.message)
    // 帖子可能刚被删了 / 从待审变过来了，状态已经不成立，重新拉一次最稳
    load(kind)
    return
  }

  // 以后端返回的为准（并发下这个数才是对的）
  s.active = !!res.data[cfg.field]
  s.count = res.data.count
}

/**
 * 只在「能操作」时才去拉状态。
 * 待审帖的点赞接口会 404，白跑一次请求还会在控制台留红字。
 */
onMounted(() => {
  if (!props.enabled) return
  load('like')
  load('favorite')
})

/**
 * 详情页是同一条路由换 id（/post/10 → /post/11）时组件会被复用，
 * onMounted 不会再跑。不 watch 的话，翻到下一篇看到的还是上一篇的赞数。
 */
watch(
  () => [props.postId, props.enabled],
  () => {
    state.value.like = { active: false, count: 0, loading: false, pending: false }
    state.value.favorite = { active: false, count: 0, loading: false, pending: false }
    if (props.enabled) {
      load('like')
      load('favorite')
    }
  },
)
</script>

<template>
  <div v-if="enabled" class="actions">
    <!-- 赞同 -->
    <button
      type="button"
      class="act"
      :class="{ on: state.like.active, busy: state.like.pending }"
      :disabled="state.like.loading"
      :title="state.like.active ? '取消赞同' : '赞同这份委托'"
      @click="toggle('like')"
    >
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M7 22V10l5-8c1.2 0 2.2 1 2.2 2.2V9h4.3c1.4 0 2.4 1.3 2.1 2.6l-1.6 8A2.2 2.2 0 0 1 16.8 22H7Zm0 0H3.5A1.5 1.5 0 0 1 2 20.5v-9A1.5 1.5 0 0 1 3.5 10H7"
        />
      </svg>
      <span class="label">{{ state.like.active ? '已赞同' : '赞同' }}</span>
      <span class="count">{{ state.like.count }}</span>
    </button>

    <!-- 收藏 -->
    <button
      type="button"
      class="act"
      :class="{ on: state.favorite.active, busy: state.favorite.pending }"
      :disabled="state.favorite.loading"
      :title="state.favorite.active ? '取消收藏' : '收藏这份委托'"
      @click="toggle('favorite')"
    >
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 3h12a1 1 0 0 1 1 1v17l-7-4.2L5 21V4a1 1 0 0 1 1-1Z" />
      </svg>
      <span class="label">{{ state.favorite.active ? '已收藏' : '收藏' }}</span>
      <span class="count">{{ state.favorite.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/*
 * 做成一枚「木牌按钮」：描边、浅底，跟筛选那排胶囊同一套语言。
 * 选中时换成墨蓝实心 + 金边，和主按钮一致 ——
 * 一眼能看出「这个状态是开着的」。
 */
.act {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px 7px 12px;
  border-radius: var(--radius-full);
  border: 1px solid var(--c-line);
  background: rgba(255, 253, 248, 0.72);
  color: var(--c-ink-3);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--t) var(--ease),
    border-color var(--t) var(--ease),
    color var(--t) var(--ease),
    box-shadow var(--t) var(--ease),
    transform var(--t-fast) var(--ease);
}

.act:hover:not(:disabled) {
  color: var(--c-primary);
  border-color: var(--c-primary-line);
  background: var(--c-primary-soft);
}

/* 按下时轻微下沉，给一点真实的按键手感 */
.act:active:not(:disabled) {
  transform: translateY(1px);
}

.act:disabled {
  cursor: default;
  opacity: 0.6;
}

/* 选中态：墨蓝渐变 + 金边 */
.act.on {
  color: #fdf8ec;
  border-color: var(--c-gold-line);
  background: linear-gradient(135deg, var(--c-primary-light), var(--c-primary-dark));
  box-shadow: var(--shadow-primary);
}

.act.on:hover {
  color: #fdf8ec;
  border-color: var(--c-gold-light);
  background: linear-gradient(135deg, #4d82a8, var(--c-primary));
}

/* 请求在飞的时候让图标轻轻呼吸，表示「收到了，在等回应」 */
.act.busy .icon {
  animation: pulse 0.9s var(--ease) infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 选中时图标填实，形状本身也多一层区分（不只靠颜色） */
.act.on .icon {
  fill: currentColor;
  stroke: currentColor;
}

.label {
  letter-spacing: 0.02em;
}

/* 数字用等宽数字，从 9 变 10 时按钮宽度不会跳 */
.count {
  min-width: 1ch;
  padding-left: 2px;
  font-variant-numeric: tabular-nums;
  color: var(--c-ink-4);
  font-size: 12px;
}

.act.on .count {
  color: rgba(253, 248, 236, 0.78);
}

.act.on:hover .count {
  color: rgba(253, 248, 236, 0.9);
}

/* 窄屏：只留图标和数字，去掉文字，两个按钮并排不挤 */
@media (max-width: 640px) {
  .act {
    padding: 7px 11px;
  }

  .label {
    display: none;
  }
}

/* 尊重系统的减少动态效果 */
@media (prefers-reduced-motion: reduce) {
  .act.busy .icon {
    animation: none;
  }
}
</style>
