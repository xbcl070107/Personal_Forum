<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import { auditPost, pendingPosts } from '../api/post'
import { formatTime, authorOf } from '../utils/format'

/**
 * 审核台。只有管理员进得来 —— 路由守卫拦一道，后端 checkAdmin 再拦一道。
 *
 * 【⚠️ 这个接口没有分页】
 * 后端 /post/pending 返回的是**裸数组** List<Post>，不是 /post/list 那种
 * { records, total, pages } 的分页对象。所以这边是 res.data 而不是 res.data.records ——
 * 写错了不会报 404，也不会报 500，只会让页面**一直转圈**，很难猜。
 * 想去核对的话看 PostService.listPending，它的注释里写了「数据量大了再换成分页」。
 * 哪天后端真换成分页了，这里三行要一起改。
 *
 * 【为什么写错会变成「一直转圈」】
 * res.data.records 是 undefined → posts 变成 undefined →
 * 模板里那句 posts.length 抛 TypeError → Vue 的渲染中断在半路 →
 * v-loading 那层遮罩是渲染的一部分，没走到「移除遮罩」那一步，就永远留在页面上。
 * 所以：**页面卡在转圈，八成是模板里出了异常**。
 * 按 F12 看 Console，那里有红色的报错和行号。
 */
const posts = ref([])
const loading = ref(false)

/**
 * 哪几份委托被展开了全文。key 是帖子 id，值是 true。
 *
 * 用 reactive 对象而不是数组：判断某条展开没展开是渲染里的高频操作，
 * 对象是 O(1)，数组还得 includes 扫一遍。
 */
const expanded = reactive({})

function toggle(id) {
  expanded[id] = !expanded[id]
}

/** 正文超过这个长度就先折起来。审核台一屏里通常要放好几份 */
const PREVIEW = 160

function isLong(text) {
  return (text || '').length > PREVIEW
}

async function load() {
  loading.value = true
  const res = await pendingPosts()
  loading.value = false

  if (res.code !== 200) {
    ElMessage.error(res.message)
    // 失败时给个空数组，别留 undefined。
    // 上面那段说过后果：posts 是 undefined 时模板会抛异常，页面就卡住了
    posts.value = []
    return
  }

  // ?? [] 是兜底：万一哪天后端改成返回 null，这里也不会把页面搞崩
  posts.value = res.data ?? []
}

async function handleAudit(post, status) {
  if (status === 2) {
    try {
      await ElMessageBox.confirm(`确定驳回《${post.title}》？`, '驳回', { type: 'warning' })
    } catch {
      return // 用户点了取消，什么都不做
    }
  }

  // 后端要的是 (postId, pass) 两个参数：
  // 页面上按钮传进来的 status 是 1（通过）/ 2（驳回），
  // 而接口要的是 boolean —— status === 1 就是 true
  const res = await auditPost(post.id, status === 1)
  if (res.code !== 200) {
    ElMessage.error(res.message)
    return
  }

  ElMessage.success(status === 1 ? '已通过，挂上委托板了' : '已驳回')
  // 本地先移掉，不用等重新查 —— 审完这一条就不该留在这页了
  posts.value = posts.value.filter((p) => p.id !== post.id)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <span class="eyebrow">Review Desk</span>
        <h2>审核台</h2>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <!-- 队列说明 + 剩余条数。管理员最想知道的就是「还剩几份」 -->
    <div class="queue-bar fade-up">
      <span class="queue-seal">核</span>
      <template v-if="posts.length > 0">
        还有 <strong>{{ posts.length }}</strong> 份委托等待核对
      </template>
      <template v-else> 队列已清空 </template>
      <span class="queue-tip">通过后立即挂上委托板</span>
    </div>

    <div v-loading="loading" class="list">
      <EmptyState
        v-if="!loading && posts.length === 0"
        glyph="清"
        text="没有待审核的委托，队列已清空"
      />

      <article
        v-for="(post, i) in posts"
        :key="post.id"
        class="panel audit ribbon ribbon-warning fade-up"
        :style="{ animationDelay: `${Math.min(i, 6) * 50}ms` }"
      >
        <header class="audit-head">
          <h3 class="title">{{ post.title }}</h3>
          <el-tag type="warning" size="small" effect="light">待审核</el-tag>
        </header>

        <div class="meta">
          <!--
            提交人。审核台最该看的就是这条 —— 同一个人反复提交垃圾内容，
            不显示作者的话你根本发现不了，只能一份一份看正文。
            username 来自后端 PostVO。
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
          <span class="no">NO.{{ post.id }}</span>
          <span>{{ formatTime(post.createdAt) }}</span>
        </div>

        <!--
          长正文默认折起来：审核台一屏里要塞好几份，
          全展开的话每份都占满屏，来回滚很累。
          短正文（不到 PREVIEW 字）就不给折叠按钮 —— 点一下没变化反而让人困惑。
        -->
        <div
          class="content pre-wrap"
          :class="{ clamped: isLong(post.content) && !expanded[post.id] }"
        >
          {{ post.content }}
        </div>

        <button v-if="isLong(post.content)" type="button" class="more" @click="toggle(post.id)">
          {{ expanded[post.id] ? '收起全文 ↑' : '展开全文 ↓' }}
        </button>

        <footer class="actions">
          <el-button type="danger" plain round @click="handleAudit(post, 2)">驳回</el-button>
          <el-button type="success" round @click="handleAudit(post, 1)">通过</el-button>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
/* ---- 队列条：羊皮纸 + 金边，和「等待核对」的语气一致 ---- */
.queue-bar {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
  padding: 13px 18px;
  margin-bottom: 14px;
  border-radius: var(--radius-lg);
  font-size: 13.5px;
  color: var(--c-ink-2);
  background: var(--c-warning-soft);
  border: 1px solid rgba(184, 137, 43, 0.3);
  box-shadow: var(--shadow-sm);
}

/* 「核」字小印，代替原来的沙漏 emoji */
.queue-seal {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: #7d5a14;
  background: rgba(255, 253, 248, 0.9);
  box-shadow: inset 0 0 0 1px rgba(184, 137, 43, 0.45);
}

.queue-bar strong {
  font-family: var(--font-display);
  color: #7d5a14;
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

/* 右侧那句说明推到最右，跟左边的计数分开 */
.queue-tip {
  margin-left: auto;
  font-size: 12px;
  color: var(--c-ink-4);
}

/* ---- 审核卡片 ---- */
.list {
  min-height: 120px;
}

.audit {
  margin-bottom: 12px;
}

.audit-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.title {
  margin: 0;
  font-size: 17px;
  line-height: 1.4;
  /* 标题可能很长，让标签始终靠右不被挤走 */
  flex: 1;
  min-width: 0;
}

.meta {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--c-ink-4);
  font-size: 12px;
}

/* ---- 提交人 ----
   和首页列表里那个作者牌同一套做法，整站保持一致 */
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

.no {
  font-size: 10.5px;
  letter-spacing: 0.08em;
  font-variant-numeric: tabular-nums;
}

/* 正文放在一块略深的纸上，和卡片的底分开 */
.content {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: var(--radius);
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--c-ink-2);
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line-soft);
}

/* 折叠：按行数截断。
   ⚠️ 不用 max-height —— 中文字号一改，写死的像素就不准了，
   要么截掉半行要么留一条空白。行数才跟着字号走 */
.clamped {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.more {
  margin-top: 8px;
  padding: 2px 0;
  border: none;
  background: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--c-primary);
}

.more:hover {
  text-decoration: underline;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--c-line-soft);
}

@media (max-width: 640px) {
  .audit {
    padding: 16px;
  }

  .queue-tip {
    margin-left: 0;
    width: 100%;
  }

  .actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
