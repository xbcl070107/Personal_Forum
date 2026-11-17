<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import { deletePost, myPosts } from '../api/post'
import { formatTime, statusOf } from '../utils/format'

/**
 * 我的委托（我的帖子）。
 *
 * 和首页的区别：这里**不筛 status** —— 待审核、已驳回都要显示。
 * 作者得知道自己发的东西现在什么情况，不然发完就石沉大海。
 * 这也是后端 /post/my 故意不筛状态的原因。
 */
const router = useRouter()

const posts = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/**
 * 当前这一页里各状态各有几条。
 *
 * ⚠️ 统计的是**本页**，不是全部 —— 后端没给「各状态总数」这个接口，
 * 前端手上只有这一页的数据。所以数字是这一页的，右边「共 N 份」用的才是
 * 后端返回的 total。以后要把统计做准，得后端加接口，不是前端能修的。
 */
const counts = computed(() => {
  const c = { 0: 0, 1: 0, 2: 0 }
  for (const p of posts.value) {
    if (c[p.status] !== undefined) c[p.status] += 1
  }
  return c
})

/**
 * 状态 → 卡片左上角那条小布幡的颜色。
 * 和标签颜色是同一套语义，所以直接映射到 style.css 里的 .ribbon-* 类。
 */
const RIBBON = { 0: 'ribbon-warning', 1: 'ribbon-success', 2: 'ribbon-danger' }

async function load() {
  loading.value = true
  const res = await myPosts({ pageNum: pageNum.value, pageSize: pageSize.value })
  loading.value = false

  if (res.code !== 200) {
    ElMessage.error(res.message)
    return
  }
  posts.value = res.data.records
  total.value = res.data.total
}

function handlePageChange(p) {
  pageNum.value = p
  load()
}

/**
 * 在这一页里删掉一条委托。
 *
 * 【为什么这里不用判「是不是我发的」】
 * 这个列表的数据来自 /post/my —— 后端只返回当前登录用户自己的帖子，
 * 所以这一页里的每一条都归你，删除按钮永远可以显示。
 * （详情页不一样：那里可能是别人的帖，所以要算 canDelete。）
 *
 * 【为什么不直接把这条从数组里 splice 掉】
 * 删完这一页可能空出来一个位置，却还有下一页的数据 ——
 * 于是列表底部会出现「空位 + 分页上还有下一页」的怪状态。
 * 重新查一次这一页，让后端决定这一页还剩什么。
 * 多一次请求换掉一类「看着像丢数据」的显示问题，值。
 */
async function handleDelete(post) {
  try {
    await ElMessageBox.confirm(`确定删除《${post.title}》？删除后列表里就看不到了。`, '删除委托', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return // 点了取消
  }

  const res = await deletePost(post.id)

  if (res.code !== 200) {
    // 一般是 404：这条可能已经在别的标签页删掉了
    ElMessage.error(res.message)
    return
  }

  ElMessage.success('已删除')
  // 等一下再重查：不然确认弹窗还在淡出，成功提示就叠上来了
  setTimeout(load, 220)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <span class="eyebrow">My Commissions</span>
        <h2>我的委托</h2>
      </div>
      <el-button type="primary" round @click="router.push('/publish')">＋ 发布委托</el-button>
    </div>

    <!-- 三个状态的计数。待审核和已驳回只有在「我的委托」才看得到，所以放在最上面 -->
    <div class="summary fade-up">
      <div class="sum-item">
        <strong class="num-pending">{{ counts[0] }}</strong>
        <span>待审核</span>
      </div>
      <div class="sum-item">
        <strong class="num-approved">{{ counts[1] }}</strong>
        <span>已通过</span>
      </div>
      <div class="sum-item">
        <strong class="num-rejected">{{ counts[2] }}</strong>
        <span>已驳回</span>
      </div>
      <div class="sum-total">共 <strong>{{ total }}</strong> 份</div>
    </div>

    <div v-loading="loading">
      <EmptyState
        v-if="!loading && posts.length === 0"
        glyph="新"
        text="还没发过委托，写第一份吧"
      >
        <el-button type="primary" round @click="router.push('/publish')">去发布</el-button>
      </EmptyState>

      <article
        v-for="(post, i) in posts"
        :key="post.id"
        class="post ribbon fade-up"
        :class="RIBBON[post.status]"
        :style="{ animationDelay: `${Math.min(i, 8) * 45}ms` }"
        @click="router.push(`/post/${post.id}`)"
      >
        <div class="post-body">
          <div class="row">
            <h3 class="title">{{ post.title }}</h3>
            <el-tag :type="statusOf(post.status).type" size="small" effect="light">
              {{ statusOf(post.status).text }}
            </el-tag>
          </div>
          <div class="meta">
            <span class="no">NO.{{ post.id }}</span>
            <span>{{ formatTime(post.createdAt) }}</span>
          </div>
        </div>

        <!--
          删除。@click.stop 是必须的：卡片整块是可点的（点哪儿都进详情），
          不拦一下，点删除会先跳走再弹确认框。
        -->
        <button
          type="button"
          class="del"
          title="删除这份委托"
          @click.stop="handleDelete(post)"
        >
          删除
        </button>

        <span class="arrow">→</span>
      </article>
    </div>

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
</template>

<style scoped>
/* ---- 进度条 ---- */
.summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin-bottom: 14px;
  border-radius: var(--radius-lg);
  background: var(--c-surface);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--c-line);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.7),
    var(--shadow-sm);
}

.sum-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding-right: 14px;
  border-right: 1px solid var(--c-line-soft);
}

.sum-item strong {
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* 三个数字各自对上标签色，扫一眼就知道哪类多 */
.num-pending {
  color: var(--c-warning);
}

.num-approved {
  color: var(--c-success);
}

.num-rejected {
  color: var(--c-danger);
}

.sum-item span {
  font-size: 12px;
  color: var(--c-ink-3);
}

.sum-total {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--c-ink-4);
}

.sum-total strong {
  color: var(--c-ink);
  font-size: 15px;
  font-family: var(--font-display);
}

/* ---- 委托卡片 ---- */
.post {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
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

/* 左侧一条状态色边。比只在右边放个标签更容易扫 ——
   一列委托滚下来，哪几份被驳回了是一眼的事 */
.post::after {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--c-ink-4);
}

.post.ribbon-warning::after {
  background: var(--c-warning);
}

.post.ribbon-success::after {
  background: var(--c-success);
}

.post.ribbon-danger::after {
  background: var(--c-danger);
}

.post:hover {
  transform: translateY(-2px);
  border-color: var(--c-gold-line);
  box-shadow:
    var(--shadow-lg),
    inset 0 0 0 1px var(--c-gold-ring);
}

.post:hover .title {
  color: var(--c-primary);
}

.post:hover .arrow {
  opacity: 1;
  transform: translateX(0);
}

.post-body {
  flex: 1;
  min-width: 0;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--t) var(--ease);
}

.meta {
  display: flex;
  gap: 14px;
  color: var(--c-ink-4);
  font-size: 12px;
}

/* 委托编号：档案上的钢印编号 */
.no {
  font-size: 10.5px;
  letter-spacing: 0.08em;
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

/*
 * 行内删除按钮。
 * 平时只是一行浅灰小字（一列卡片里放十个红按钮会很吵），
 * 鼠标移到卡片上才浮出来 —— 和右边那个箭头同一套逻辑。
 *
 * ⚠️ 用 visibility 控制显隐，不用 opacity：
 * opacity 是渐变，移入 / 移出的那 0.24 秒里按钮处于半透明状态 ——
 * 看得见摸不着，还能点。visibility 是跳变的，要么完整显示、要么完全不存在，
 * 不会出现那种「灰蒙蒙但是能按」的中间态，也就不会有误击。
 */
.del {
  flex-shrink: 0;
  padding: 5px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  font-family: inherit;
  font-size: 12px;
  color: var(--c-ink-4);
  cursor: pointer;
  visibility: hidden;
  transition:
    color var(--t) var(--ease),
    background var(--t) var(--ease),
    border-color var(--t) var(--ease);
}

.post:hover .del,
.del:focus-visible {
  visibility: visible;
}

.del:hover {
  color: var(--c-danger);
  background: var(--c-danger-soft);
  border-color: rgba(164, 70, 47, 0.28);
}

/* 触屏没有 hover，隐藏按钮等于没有这个功能，所以窄屏一律显示 */
@media (hover: none) {
  .del {
    visibility: visible;
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin: 22px 0 0;
}

@media (max-width: 640px) {
  .summary {
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px 14px;
  }

  .sum-item {
    padding-right: 10px;
  }

  .sum-total {
    width: 100%;
    margin-left: 0;
  }

  .post {
    padding: 14px;
  }
}
</style>
