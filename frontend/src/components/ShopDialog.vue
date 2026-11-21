<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createShop, updateShop } from '../api/shop'
import { uploadImageFile } from '../utils/upload'

/**
 * 开店 / 改店铺资料的弹窗。
 *
 * 【新建和编辑为什么共用一个组件】
 * 字段几乎一样，差别只有两个：编辑时 type 不能改（后端 ShopEditDTO 里没有它）、
 * 编辑时每个字段都是「可空 = 不改」。共用一份表单，校验规则就不会两处各写一份。
 *
 * 【编辑时为什么还是把三个字段都传过去】
 * 后端是局部更新，不传 = 不改；但表单里用户看到的就是完整的一份资料，
 * 只传「改过的」需要前端自己 diff，反而容易漏。这里全传，语义更直白。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传了就是编辑，不传就是开店 */
  shop: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const form = reactive({
  name: '',
  type: 1,
  description: '',
  avatarUrl: '',
})

const isEdit = computed(() => Boolean(props.shop?.id))
const title = computed(() => (isEdit.value ? '修改店铺资料' : '开一家店'))

const rules = {
  name: [
    { required: true, message: '给店起个名字', trigger: 'blur' },
    { max: 32, message: '店名最长 32 个字', trigger: 'blur' },
  ],
  description: [{ max: 255, message: '店铺简介最长 255 个字', trigger: 'blur' }],
}

const initial = computed(() => {
  const name = form.name.trim()
  return name ? name.charAt(0) : '店'
})

function fillForm() {
  form.name = props.shop?.name || ''
  form.type = props.shop?.type ?? 1
  form.description = props.shop?.description || ''
  form.avatarUrl = props.shop?.avatarUrl || ''
  nextTick(() => formRef.value?.clearValidate())
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fillForm()
  },
)

watch(
  () => props.shop,
  () => {
    if (props.modelValue) fillForm()
  },
)

function close() {
  emit('update:modelValue', false)
}

function pickAvatar() {
  fileInput.value?.click()
}

async function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  // 同一个文件选第二次时不会触发 change，所以每次选完都把 value 清掉
  event.target.value = ''
  if (!file) return

  uploading.value = true
  try {
    const url = await uploadImageFile(file)
    if (url) form.avatarUrl = url
  } finally {
    uploading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const common = {
      name: form.name.trim(),
      description: form.description.trim(),
      avatarUrl: form.avatarUrl.trim(),
    }

    const res = isEdit.value
      ? await updateShop(common) // 编辑不带 type：后端也不认
      : await createShop({ ...common, type: form.type })

    if (res.code !== 200) {
      ElMessage.error(res.message || '保存失败')
      return
    }

    ElMessage.success(isEdit.value ? '店铺资料已更新' : '店开好了，去上架第一件商品吧')
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
    :title="title"
    width="min(520px, 94vw)"
    append-to-body
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="shop-form">
      <!-- 头像 + 店名：视觉上像一张店铺登记表 -->
      <div class="head">
        <button type="button" class="avatar" :class="{ loading: uploading }" @click="pickAvatar">
          <img v-if="form.avatarUrl" :src="form.avatarUrl" alt="店铺头像" />
          <span v-else>{{ initial }}</span>
          <span class="avatar-mask">{{ uploading ? '上传中…' : '换头像' }}</span>
        </button>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="handleAvatarChange" />

        <div class="head-main">
          <el-form-item label="店名" prop="name">
            <el-input v-model="form.name" placeholder="例如：渡鸦杂货铺" maxlength="32" />
          </el-form-item>

          <el-form-item label="店铺类型">
            <el-radio-group v-model="form.type" :disabled="isEdit">
              <el-radio :value="1">个人店</el-radio>
              <el-radio :value="2">公会店</el-radio>
            </el-radio-group>
            <p v-if="isEdit" class="hint">店铺类型开店后不能自己改</p>
          </el-form-item>
        </div>
      </div>

      <el-form-item label="店铺简介" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="卖什么、发货时间、有什么规矩…"
          maxlength="255"
          show-word-limit
        />
      </el-form-item>

      <p class="hint">一人只能开一家店；店名和简介以后随时能改。</p>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '开店' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.avatar {
  position: relative;
  display: grid;
  place-items: center;
  flex: none;
  width: 76px;
  height: 76px;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 28px;
  color: var(--c-primary);
  background: var(--c-surface-solid);
  border: 1px dashed var(--c-line-strong);
  border-radius: var(--radius);
  cursor: pointer;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-mask {
  position: absolute;
  inset: auto 0 0;
  padding: 3px 0;
  font-family: var(--font-body);
  font-size: 10.5px;
  color: var(--c-surface-solid);
  text-align: center;
  background: rgba(21, 19, 15, 0.62);
}

.head-main {
  flex: 1;
  min-width: 0;
}

.shop-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--c-ink-3);
}

.hint {
  margin: 2px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--c-ink-4);
}

@media (max-width: 480px) {
  .head {
    gap: 12px;
  }

  .avatar {
    width: 62px;
    height: 62px;
    font-size: 22px;
  }
}
</style>
