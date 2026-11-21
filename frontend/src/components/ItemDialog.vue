<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listItemCategories, publishItem } from '../api/item'
import { uploadImageFile } from '../utils/upload'
import { yuanToCents } from '../utils/format'

/**
 * 发布商品弹窗。
 *
 * 【为什么价格输入框用「元」，接口却传「分」】
 * 用户脑子里是 19.9 元，数据库里必须是 1990 分（整数）。
 * 输入框保留元，提交前用 yuanToCents 转一次；
 * 后端 ItemPublishDTO 的 priceCents 只收整数，传 19.9 会直接 400。
 *
 * 【为什么不做「编辑商品」】
 * 后端只有 POST /item（发布）和 PATCH /item/{id}/status（上下架），
 * 没有改标题/改价的接口。前端硬加一个编辑框，点保存也没地方发。
 * 要改价就先下架、重新发一件 —— 这是当前后端的边界，不是前端偷懒。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const fileInput = ref(null)
const categories = ref([])
const images = ref([])

const form = reactive({
  title: '',
  categoryId: null,
  priceYuan: null,
  stock: 0,
  description: '',
})

/**
 * 价格校验：必须能转成整数分，且在 1 ~ 99999999 分之间。
 * 99999999 分 = 999999.99 元，和后端 ItemPublishDTO 的上限一致。
 */
function validatePrice(rule, value, callback) {
  const cents = yuanToCents(value)
  if (cents == null || cents <= 0) {
    callback(new Error('填一个大于 0 的价格'))
    return
  }
  if (cents > 99999999) {
    callback(new Error('价格不能超过 999999.99 元'))
    return
  }
  callback()
}

const rules = {
  title: [
    { required: true, message: '给商品起个标题', trigger: 'blur' },
    { max: 80, message: '标题最长 80 个字', trigger: 'blur' },
  ],
  priceYuan: [{ validator: validatePrice, trigger: 'blur' }],
  description: [{ max: 5000, message: '详情最长 5000 个字', trigger: 'blur' }],
}

const canAddImage = computed(() => images.value.length < 9)

function reset() {
  form.title = ''
  form.categoryId = null
  form.priceYuan = null
  form.stock = 0
  form.description = ''
  images.value = []
  nextTick(() => formRef.value?.clearValidate())
}

async function loadCategories() {
  const res = await listItemCategories()
  if (res.code === 200) categories.value = res.data || []
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    reset()
    // 分类很少变，打开过一次就不用再查；失败也不影响发布（分类可空）
    if (!categories.value.length) loadCategories()
  },
)

function close() {
  emit('update:modelValue', false)
}

function pickImages() {
  fileInput.value?.click()
}

/**
 * 一次可以选多张。逐张上传而不是 Promise.all：
 * 九张图同时打上去，任何一张失败都会让「哪张成功了」变得难对；
 * 串行慢一点，但每张的结果都能立刻落到 images 数组里。
 */
async function handleFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return

  const room = 9 - images.value.length
  if (files.length > room) {
    ElMessage.warning(`最多 9 张图，这次只传前 ${room} 张`)
  }

  uploading.value = true
  try {
    for (const file of files.slice(0, room)) {
      const url = await uploadImageFile(file)
      if (url && images.value.length < 9) images.value.push(url)
    }
  } finally {
    uploading.value = false
  }
}

function removeImage(index) {
  images.value.splice(index, 1)
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const priceCents = yuanToCents(form.priceYuan)
  if (priceCents == null || priceCents <= 0) {
    ElMessage.error('价格不对')
    return
  }

  submitting.value = true
  try {
    const res = await publishItem({
      title: form.title.trim(),
      description: form.description.trim(),
      // 分类可以不选：后端 category_id 允许 NULL，集市里归到「全部」
      categoryId: form.categoryId || null,
      priceCents,
      stock: form.stock,
      images: images.value,
    })

    if (res.code !== 200) {
      ElMessage.error(res.message || '发布失败')
      return
    }

    ElMessage.success('商品已上架')
    emit('saved', res.data)
    close()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="发布商品"
    width="min(560px, 94vw)"
    append-to-body
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="item-form">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="例如：旧地图一卷（附渡口标注）" maxlength="80" />
      </el-form-item>

      <div class="row">
        <el-form-item label="分类（可不选）">
          <el-select v-model="form.categoryId" placeholder="不分类" clearable class="full">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="价格（元）" prop="priceYuan">
          <el-input-number
            v-model="form.priceYuan"
            :min="0.01"
            :max="999999.99"
            :precision="2"
            :step="1"
            controls-position="right"
            class="full"
          />
        </el-form-item>

        <el-form-item label="库存">
          <el-input-number
            v-model="form.stock"
            :min="0"
            :max="999999"
            :step="1"
            controls-position="right"
            class="full"
          />
        </el-form-item>
      </div>

      <el-form-item label="详情" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          placeholder="成色、尺寸、发货时间…纯文本，不支持富文本"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="`商品图（${images.length}/9）`">
        <div class="images">
          <div v-for="(url, i) in images" :key="url" class="image-tile">
            <img :src="url" alt="商品图" />
            <button type="button" class="remove" title="移除这张" @click="removeImage(i)">
              ×
            </button>
          </div>

          <button
            v-if="canAddImage"
            type="button"
            class="image-add"
            :disabled="uploading"
            @click="pickImages"
          >
            {{ uploading ? '上传中…' : '＋' }}
          </button>

          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            hidden
            @change="handleFiles"
          />
        </div>
        <p class="hint">最多 9 张，单张不超过 5MB。第一张会作为列表封面。</p>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">发布并上架</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 0.8fr;
  gap: 12px;
}

.full {
  width: 100%;
}

.item-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--c-ink-3);
}

/* el-input-number 的内容默认不撑满，这里让它跟旁边的 select 对齐 */
.item-form :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-tile,
.image-add {
  position: relative;
  display: grid;
  place-items: center;
  width: 74px;
  height: 74px;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.image-tile img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-add {
  font-size: 22px;
  color: var(--c-ink-4);
  border-style: dashed;
  cursor: pointer;
  transition: border-color 0.18s var(--ease), color 0.18s var(--ease);
}

.image-add:hover:not(:disabled) {
  color: var(--c-primary);
  border-color: var(--c-primary-line);
}

.image-add:disabled {
  cursor: wait;
}

.remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  padding: 0;
  font-size: 13px;
  line-height: 1;
  color: var(--c-surface-solid);
  background: rgba(21, 19, 15, 0.62);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.hint {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

@media (max-width: 560px) {
  .row {
    grid-template-columns: 1fr 1fr;
  }

  .row > :last-child {
    grid-column: 1 / -1;
  }
}
</style>
