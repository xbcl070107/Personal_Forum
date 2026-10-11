<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import PostActions from '../components/PostActions.vue'
import { deletePost, getPost } from '../api/post'
import { listComments, publishComment } from '../api/comment'
import { useUserStore } from '../store/user'
import { authorOf, avatarColorOf, formatTime, statusOf } from '../utils/format'

/**
 * 帖子详情页：委托正文 + 评论区。
 *
 * 两个数据源，各管各的：
 * - 帖子本身只在进来时查一次
 * - 评论列表每次发完评论要**重新查**一遍
 *
 * 为什么发完评论是重新查、而不是把新评论 push 进数组：
 * 后端会填 createdAt、也要按时间正序排，前端自己拼出来的顺序
 * 很可能和后端不一致。少一次请求换一个「本地显示和后端不一致」的 bug，不值。
 * 评论量真大到重新查会卡了，再改成局部更新。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = route.params.id

const post = ref(null)
const comments = ref([])
const content = ref('')
const loading = ref(true)
const sending = ref(false)

/**
 * 评论输入框下面那行提示里的字数。
 * el-input 自带 show-word-limit，这个计数是为了在「快写满」时变色提醒。
 */
const contentLength = computed(() => content.value.length)
const nearLimit = computed(() => contentLength.value > 450)

/**
 * 评论头像配色。
 *
 * 算法本身收在 utils/format.js 的 avatarColorOf 里 —— 原来这里自己抄了一份
 * 「字符码求和取模」，和顶栏那份是重复实现。两处都改倒还好，改一处就会
 * 出现「同一个人顶栏一个色、评论区一个色」。
 */
function avatarStyle(username) {
  return { background: avatarColorOf(username) }
}

function initialOf(username) {
  return username ? username.charAt(0).toUpperCase() : '?'
}

async function loadPost() {
  const res = await getPost(postId)
  if (res.code !== 200) {
    ElMessage.error(res.message)
    // 帖子不存在（或没通过审核、又不是作者本人）—— 留在这页没意义，回首页
    router.push('/')
    return
  }
  post.value = res.data
}

async function loadComments() {
  const res = await listComments(postId)
  if (res.code === 200) {
    comments.value = res.data
  }
}

async function handlePublish() {
  // trim 之后再判：只敲了几个空格不算评论。
  // 后端的 @NotBlank 也拦这个，但那是一次白跑的请求
  if (!content.value.trim()) {
    ElMessage.warning('评论不能为空')
    return
  }

  sending.value = true
  const res = await publishComment({ postId: Number(postId), content: content.value })
  sending.value = false

  if (res.code !== 200) {
    ElMessage.error(res.message)
    return
  }

  content.value = ''
  ElMessage.success('评论成功')
  loadComments()
}

/**
 * 这条帖是不是「已过审」。
 *
 * 只有已过审的帖才能点赞 / 收藏 —— 后端 requireApproved 对其它状态直接回 404。
 * 待审 / 驳回的帖只有作者本人和管理员看得到，所以这里要跳过 status != 1 的情况。
 */
const approved = computed(() => post.value?.status === 1)

/**
 * 当前用户能不能删这条帖。
 *
 * 规则跟后端 PostService.delete 一致：作者本人，或者管理员。
 * ⚠️ 这只是「要不要画出按钮」—— 真正拦人的是后端那个 WHERE 条件。
 * 前端算错了顶多多显示一个按钮，点下去会被后端的 404 挡住。
 */
const canDelete = computed(() => {
  if (!post.value || !userStore.isLogin) return false
  if (userStore.isAdmin) return true
  // id 类型要对齐：路由参数是字符串，store 里是数字
  return Number(post.value.userId) === Number(userStore.user?.id)
})

/**
 * 删帖。
 *
 * 两步确认：这个动作不可逆（后端是逻辑删除，但页面上没有「恢复」入口），
 * 而且顶栏的按钮挨得近 —— 和退出登录同理，问一句比事后后悔便宜。
 */
async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除《${post.value.title}》？删除后列表里就看不到了。`,
      '删除委托',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return // 点了取消
  }

  const res = await deletePost(postId)

  if (res.code !== 200) {
    // 常见的是 404「帖子不存在，或者不是你发的」—— 可能别的标签页已经删过了
    ElMessage.error(res.message)
    return
  }

  ElMessage.success('已删除')
  // 留在详情页没意义了（这条再也查不到），回列表
  router.push('/my')
}

onMounted(async () => {
  await Promise.all([loadPost(), loadComments()])
  loading.value = false
})
</script>

<template>
  <div v-loading="loading" class="page page--narrow">
    <!-- 返回入口。详情页是从列表点进来的，给一条辙比让人按浏览器后退友好 -->
    <div class="crumb">
      <el-button text size="small" @click="router.push('/')">← 返回委托板</el-button>
    </div>

    <template v-if="post">
      <article class="panel post-panel ribbon fade-up">
        <!-- 卷宗角上的分类标签 + 编号 -->
        <div class="post-top">
          <span class="eyebrow">Commission</span>
          <span class="post-no">NO.{{ post.id }}</span>
        </div>

        <h1 class="post-title">{{ post.title }}</h1>

        <div class="meta">
          <!--
            作者身份牌。显示 nickname（昵称，可重复），username（唯一）只用来算头像色 ——
            两个都来自后端 PostVO。
            以前这里只能按 userId 推名字，同一个人发了帖又评论时，
            帖头显示「抄书人」、评论区显示「林间旅人」，一条帖子里两个名字。
          -->
          <span class="author">
            <span
              class="avatar"
              :style="{ background: authorOf(post.userId, post.nickname, post.username).color }"
            >
              {{ authorOf(post.userId, post.nickname, post.username).initial }}
            </span>
            <span>{{ authorOf(post.userId, post.nickname, post.username).name }}</span>
          </span>

          <span class="sep">·</span>
          <span>{{ formatTime(post.createdAt) }}</span>

          <!--
            状态标签：普通用户只可能看到「已通过」的帖（其余的详情接口直接回 404）。
            能看到「待审核」的只有作者本人和管理员，所以这个标签对他们是有用的提示。
          -->
          <el-tag :type="statusOf(post.status).type" size="small" class="status">
            {{ statusOf(post.status).text }}
          </el-tag>
        </div>

        <!-- 装饰分隔：一条渐隐线 + 金色菱形，代替 el-divider -->
        <div class="ornament"><i></i></div>

        <div class="pre-wrap content">{{ post.content }}</div>

        <!--
          互动栏：点赞 / 收藏 + （有权限时）删除。
          最上面那条分隔线由 .post-foot 自己画，不用 ornament ——
          那两个装饰线挨太近会显得吵。
        -->
        <footer class="post-foot">
          <PostActions :post-id="post.id" :enabled="approved" />

          <el-button
            v-if="canDelete"
            text
            size="small"
            class="del"
            @click="handleDelete"
          >
            删除这份委托
          </el-button>
        </footer>
      </article>

      <section class="panel comment-panel card fade-up" style="animation-delay: 60ms">
        <header class="section-head">
          <h2>委托留言</h2>
          <span class="count">{{ comments.length }}</span>
        </header>

        <EmptyState
          v-if="comments.length === 0"
          glyph="静"
          text="还没有人留言，来坐个沙发"
          compact
        />

        <ul v-else class="comments">
          <li v-for="c in comments" :key="c.id" class="comment">
            <!--
              头像色用 c.username（唯一）算，显示的名字用 c.nickname（可重复）。
              反过来做的话，两个撞昵称的人会连颜色和首字都一样，评论区分不出谁是谁。
            -->
            <span class="avatar cm-avatar" :style="avatarStyle(c.username)">
              {{ initialOf(c.nickname || c.username) }}
            </span>

            <div class="comment-main">
              <div class="comment-head">
                <span class="comment-user">{{ c.nickname || c.username }}</span>
                <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
              </div>
              <div class="pre-wrap comment-text">{{ c.content }}</div>
            </div>
          </li>
        </ul>
      </section>
    </template>

    <section class="panel card fade-up" style="animation-delay: 120ms">
      <template v-if="userStore.isLogin">
        <header class="section-head">
          <h2>留个话</h2>
          <!-- 快写满了变个色，比被 maxlength 突然卡住才知道友好 -->
          <span class="counter" :class="{ warn: nearLimit }">{{ contentLength }} / 500</span>
        </header>

        <el-input
          v-model="content"
          type="textarea"
          :rows="4"
          maxlength="500"
          placeholder="友善发言，说清楚就好……"
        />

        <div class="send">
          <el-button type="primary" round :loading="sending" @click="handlePublish">
            发表留言
          </el-button>
        </div>
      </template>

      <!-- 没登录就不显示输入框，给个入口。后端也会拦（401），这里只是别让人白打字 -->
      <div v-else class="need-login">
        <span class="seal lock">封</span>
        <p>登录之后才能在委托下留言</p>
        <el-button type="primary" round @click="router.push('/login')">去登录</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.crumb {
  margin-bottom: 10px;
  margin-left: -8px;
}

/* ---- 正文 ---- */
.post-panel {
  padding: 26px 30px 32px;
}

/* 顶上那一行：左边的眉题和右边的编号撑到两端 */
.post-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.post-no {
  font-size: 10.5px;
  letter-spacing: 0.1em;
  color: var(--c-ink-4);
  font-variant-numeric: tabular-nums;
}

.post-title {
  margin: 0 0 14px;
  font-size: 27px;
  line-height: 1.35;
  letter-spacing: 0.01em;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--c-ink-4);
}

.author {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--c-ink-2);
  font-weight: 500;
}

/* 正文署名徽记。底色由模板内联给（作者色），这里只管形状和字 */
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 600;
  color: #fdf6e6;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.3);
}

.sep {
  opacity: 0.6;
}

/* 状态标签推到这一行最右边 */
.status {
  margin-left: auto;
}

.content {
  font-size: 15.5px;
  line-height: 1.95;
  color: var(--c-ink-2);
  letter-spacing: 0.012em;
}

/* ---- 互动栏 ----
   正文和评论区之间的一条。删除推到最右边，和互动按钮分开 ——
   删除是破坏性动作，不该挨着点赞，手滑一下代价太大。 */
.post-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--c-line-soft);
}

.del {
  margin-left: auto;
  color: var(--c-ink-4);
}

/* 悬停才变红：平时不抢注意力，指上去才提示「这个动作是危险的」 */
.del:hover {
  color: var(--c-danger);
  background: var(--c-danger-soft);
}

/* ---- 评论 ---- */
.comment-panel {
  padding: 20px 26px 8px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h2 {
  margin: 0;
  font-size: 17px;
}

/* 留言条数做成一枚小印 */
.count {
  min-width: 26px;
  padding: 1px 9px;
  border-radius: var(--radius-full);
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #7d5a14;
  background: var(--c-gold-soft);
  border: 1px solid rgba(184, 137, 43, 0.24);
}

.comments {
  list-style: none;
  margin: 0;
  padding: 0;
}

.comment {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid var(--c-line-soft);
}

.comment:first-child {
  border-top: none;
  padding-top: 4px;
}

.cm-avatar {
  /* 评论头像比正文署名那个大一点，压住左边这一列 */
  width: 32px;
  height: 32px;
  font-size: 13px;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-user {
  font-family: var(--font-display);
  color: var(--c-primary);
  font-weight: 600;
  font-size: 14px;
}

.comment-time {
  color: var(--c-ink-4);
  font-size: 12px;
}

.comment-text {
  font-size: 14.5px;
  line-height: 1.78;
  color: var(--c-ink-2);
}

/* ---- 发表留言 ---- */
.counter {
  font-size: 12px;
  color: var(--c-ink-4);
  font-variant-numeric: tabular-nums;
}

.counter.warn {
  color: var(--c-warning);
  font-weight: 600;
}

.send {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

/* ---- 未登录 ---- */
.need-login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 0 18px;
  color: var(--c-ink-3);
}

/* 一枚「封」字小印，代替原来的锁 emoji —— 和全站的金色印章是一套 */
.need-login .lock {
  width: 34px;
  height: 34px;
  font-size: 15px;
  color: #a4462f;
  background: var(--c-danger-soft);
  box-shadow: inset 0 0 0 1px rgba(164, 70, 47, 0.3);
}

.need-login p {
  margin: 4px 0 8px;
  font-size: 13.5px;
}

@media (max-width: 640px) {
  .post-panel {
    padding: 20px 18px 24px;
  }

  .post-title {
    font-size: 21px;
  }

  .content {
    font-size: 15px;
  }

  .comment-panel {
    padding: 18px 16px 6px;
  }

  .status {
    margin-left: 0;
  }
}
</style>
