<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCategories } from '../api/category'
import { publishPost } from '../api/post'

/**
 * 发帖页（发布委托）。
 *
 * 发出去之后帖子的 status 是 0（待审核），不会出现在首页列表里。
 * 所以成功之后直接跳到「我的委托」—— 首页上看不到自己刚发的东西，
 * 用户会以为没发成功，然后重复点。
 */
const router = useRouter()

const categories = ref([])
const form = ref({ title: '', content: '', categoryId: null })
const loading = ref(false)

/** 标题写完后按回车直接跳正文，省得用鼠标点一下 */
const contentRef = ref(null)

async function loadCategories() {
  const res = await listCategories()
  if (res.code === 200) {
    categories.value = res.data
  }
}

function focusContent() {
  // el-input 的 focus() 挂在组件实例上，不是原生 DOM
  contentRef.value?.focus()
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('标题不能空')
    return
  }
  if (!form.value.categoryId) {
    ElMessage.warning('选个板块')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('正文不能空')
    return
  }

  loading.value = true
  const res = await publishPost({
    title: form.value.title,
    content: form.value.content,
    categoryId: form.value.categoryId,
  })
  loading.value = false

  if (res.code !== 200) {
    ElMessage.error(res.message)
    return
  }

  ElMessage.success('已提交，等管理员审核通过就会挂上委托板')
  router.push('/my')
}

onMounted(loadCategories)
</script>

<template>
  <div class="page page--narrow">
    <div class="page-head">
      <div>
        <span class="eyebrow">New Commission</span>
        <h2>发布委托</h2>
      </div>
      <el-button text @click="router.push('/my')">我的委托 →</el-button>
    </div>

    <div class="panel fade-up">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="投到哪个板块">
          <el-select v-model="form.categoryId" placeholder="选一个板块" class="cat-select">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="委托标题">
          <!-- maxlength 和后端 PostPublishDTO 上的 @Size(max = 100) 对齐。
               前端限住是为了别让人写完 300 字才被拒；后端那道仍然必须在，
               因为请求可以绕过页面直接发 -->
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            size="large"
            placeholder="一句话说清你想聊什么"
            @keyup.enter="focusContent"
          />
        </el-form-item>

        <el-form-item label="委托内容">
          <el-input
            ref="contentRef"
            v-model="form.content"
            type="textarea"
            :rows="12"
            placeholder="展开说说……换行会原样保留"
          />
        </el-form-item>

        <!-- 审核规则提前讲清楚，别等提交完才发现首页看不到自己的委托 -->
        <div class="notice">
          <span class="notice-seal">告</span>
          <span>
            提交后进入待审核，管理员核过才会挂上委托板。可以在「我的委托」里查看进度。
          </span>
        </div>

        <div class="actions">
          <el-button text @click="router.push('/')">取消</el-button>
          <el-button type="primary" round :loading="loading" @click="handleSubmit">
            提交审核
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.cat-select {
  width: 220px;
}

/* 审核说明：羊皮纸色块 + 一枚小印，不抢表单的注意力但一眼能看见 */
.notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 4px 0 20px;
  padding: 12px 14px;
  border-radius: var(--radius);
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--c-ink-2);
  background: var(--c-gold-soft);
  border: 1px solid rgba(184, 137, 43, 0.28);
}

.notice-seal {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  color: #7d5a14;
  background: rgba(255, 253, 248, 0.85);
  box-shadow: inset 0 0 0 1px rgba(184, 137, 43, 0.4);
}

.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--c-line-soft);
}

@media (max-width: 640px) {
  .cat-select {
    width: 100%;
  }
}
</style>
