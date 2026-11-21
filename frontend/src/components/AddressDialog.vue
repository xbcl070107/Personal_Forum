<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createAddress, updateAddress } from '../api/address'

/**
 * 新增 / 编辑收货地址的弹窗。
 *
 * 【为什么要单独一个组件】
 * 「我的地址」页和「结算」页都要能加地址 —— 结算页发现没地址时，
 * 如果只能跳去地址页再跳回来，购物车里的东西就丢了。
 * 同一张表单写两份，校验规则迟早只改一处。
 *
 * 【isDefault 为什么是 0 / 1 而不是 true / false】
 * 后端 AddressSaveDTO 就是 0/1，直接对齐，省掉一层来回转换。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传了就是编辑，不传是新增 */
  address: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  receiverName: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: 0,
})

const isEdit = computed(() => Boolean(props.address?.id))

const title = computed(() => (isEdit.value ? '编辑收货地址' : '新增收货地址'))

/**
 * 校验规则和后端 AddressSaveDTO 一字不差：
 * 收货人 32、手机号 ^1[3-9]\d{9}$、地区 64、详细地址 128。
 * 前端先拦一遍是为了少一次往返，后端仍然会再校验一次（别只信前端）。
 */
const rules = {
  receiverName: [
    { required: true, message: '填一下收货人', trigger: 'blur' },
    { max: 32, message: '收货人最长 32 个字', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '填一下手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '手机号格式不对（11 位，1 开头）',
      trigger: 'blur',
    },
  ],
  region: [
    { required: true, message: '填一下省市区', trigger: 'blur' },
    { max: 64, message: '省市区最长 64 个字', trigger: 'blur' },
  ],
  detail: [
    { required: true, message: '填一下详细地址', trigger: 'blur' },
    { max: 128, message: '详细地址最长 128 个字', trigger: 'blur' },
  ],
}

/**
 * 每次打开时把表单重置成「当前要编辑的那条」。
 *
 * 不能只在 mounted 时初始化：这个弹窗实例是复用的，
 * 先编辑 A 再点新增，表单里还留着 A 的内容，用户会以为没清干净。
 */
function fillForm() {
  form.receiverName = props.address?.receiverName || ''
  form.phone = props.address?.phone || ''
  form.region = props.address?.region || ''
  form.detail = props.address?.detail || ''
  form.isDefault = props.address?.isDefault ?? 0
  nextTick(() => formRef.value?.clearValidate())
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fillForm()
  },
)

watch(
  () => props.address,
  () => {
    if (props.modelValue) fillForm()
  },
)

function close() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return // 校验没通过，el-form 已经把红字显示出来了
  }

  submitting.value = true
  try {
    const payload = {
      receiverName: form.receiverName.trim(),
      phone: form.phone.trim(),
      region: form.region.trim(),
      detail: form.detail.trim(),
      isDefault: form.isDefault,
    }

    const res = isEdit.value
      ? await updateAddress(props.address.id, payload)
      : await createAddress(payload)

    if (res.code !== 200) {
      ElMessage.error(res.message || '保存失败')
      return
    }

    ElMessage.success(isEdit.value ? '地址已更新' : '地址已添加')
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
    width="min(480px, 92vw)"
    append-to-body
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="addr-form">
      <div class="row">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" placeholder="收件人姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
      </div>

      <el-form-item label="省市区" prop="region">
        <el-input v-model="form.region" placeholder="例如：浙江省 杭州市 西湖区" maxlength="64" />
      </el-form-item>

      <el-form-item label="详细地址" prop="detail">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="2"
          placeholder="街道、门牌号、楼栋房间号"
          maxlength="128"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="设为默认地址">
        <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* 弹窗里的 label 比页面正文小一号，不然 el-form 默认字重偏重 */
.addr-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--c-ink-3);
}

@media (max-width: 520px) {
  .row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
