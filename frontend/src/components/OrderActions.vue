<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, payOrder, shipOrder } from '../api/order'
import { ORDER_STATUS } from '../utils/format'

/**
 * 订单卡片 / 订单详情里那排动作按钮。
 *
 * 【为什么抽成组件】
 * 列表页和详情页的按钮完全一样，但状态判断很容易写歪：
 * 详情页忘了判角色，卖家看到「确认收货」；列表页忘了判状态，
 * 已完成的单还能点「取消」。规则只写这一份，两个页面就不会各有各的错法。
 *
 * 【为什么不让父组件传 canPay / canCancel 进来】
 * 那样「什么状态能做什么」又散到两个页面里了。这里只收 order + role，
 * 自己算 —— 数据只有一个来源，改规则只改这里。
 */
const props = defineProps({
  order: { type: Object, required: true },
  /** 'buyer' 买家视角 / 'seller' 卖家视角 */
  role: { type: String, default: 'buyer' },
})

const emit = defineEmits(['updated'])

const loading = reactive({ pay: false, cancel: false, confirm: false, ship: false })

const isBuyer = computed(() => props.role === 'buyer')
const isSeller = computed(() => props.role === 'seller')

const canPay = computed(() => isBuyer.value && props.order.status === ORDER_STATUS.UNPAID)
const canCancel = computed(() => isBuyer.value && props.order.status === ORDER_STATUS.UNPAID)
const canConfirm = computed(() => isBuyer.value && props.order.status === ORDER_STATUS.SHIPPED)
const canShip = computed(() => isSeller.value && props.order.status === ORDER_STATUS.PAID)

const hasAction = computed(() => canPay.value || canCancel.value || canConfirm.value || canShip.value)

/**
 * 统一的「调接口 → 判 code → 提示 → 通知父组件重查」流程。
 *
 * 成功后不在这里改 order.status：订单状态以服务端为准，
 * 前端自己 +1 只会在「并发下别人先改了」时显示错。重查一次最稳。
 */
async function run(key, action, successText) {
  loading[key] = true
  try {
    const res = await action()

    if (res.code !== 200) {
      ElMessage.error(res.message || '操作失败')
      return false
    }

    ElMessage.success(successText)
    emit('updated')
    return true
  } finally {
    loading[key] = false
  }
}

async function handlePay() {
  try {
    await ElMessageBox.confirm(
      '这是模拟支付，没有真实扣款。确认后订单会进入「待发货」，等卖家发货。',
      '支付订单',
      { type: 'info', confirmButtonText: '确认支付', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  await run('pay', () => payOrder(props.order.id), '支付成功，等卖家发货')
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      '取消后这笔订单会关闭，商品库存会回补。已付款的订单不能在这里取消。',
      '取消订单',
      { type: 'warning', confirmButtonText: '取消订单', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  await run('cancel', () => cancelOrder(props.order.id), '订单已取消')
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认已经收到货了吗？确认后订单就完成了。', '确认收货', {
      type: 'info',
      confirmButtonText: '确认收货',
      cancelButtonText: '还没收到',
    })
  } catch {
    return
  }

  await run('confirm', () => confirmOrder(props.order.id), '已确认收货')
}

/* ---------------- 发货弹窗 ---------------- */

const shipVisible = ref(false)
const shipFormRef = ref(null)
const shipForm = reactive({ trackingNo: '', shippingCompany: '' })

/** 和后端 OrderShipDTO 对齐：单号必填 ≤64，快递公司可空 ≤32 */
const shipRules = {
  trackingNo: [
    { required: true, message: '填一下快递单号，买家要靠它查物流', trigger: 'blur' },
    { max: 64, message: '快递单号最长 64 个字符', trigger: 'blur' },
  ],
  shippingCompany: [{ max: 32, message: '快递公司最长 32 个字符', trigger: 'blur' }],
}

function openShip() {
  shipForm.trackingNo = ''
  shipForm.shippingCompany = ''
  shipVisible.value = true
}

async function submitShip() {
  if (!shipFormRef.value) return

  try {
    await shipFormRef.value.validate()
  } catch {
    return
  }

  const ok = await run(
    'ship',
    () =>
      shipOrder(props.order.id, {
        trackingNo: shipForm.trackingNo.trim(),
        shippingCompany: shipForm.shippingCompany.trim() || undefined,
      }),
    '已发货',
  )

  if (ok) shipVisible.value = false
}
</script>

<template>
  <div v-if="hasAction" class="order-actions">
    <el-button
      v-if="canShip"
      type="primary"
      size="small"
      :loading="loading.ship"
      @click.stop="openShip"
    >
      发货
    </el-button>

    <el-button
      v-if="canPay"
      type="primary"
      size="small"
      :loading="loading.pay"
      @click.stop="handlePay"
    >
      去支付
    </el-button>

    <el-button
      v-if="canConfirm"
      type="primary"
      size="small"
      :loading="loading.confirm"
      @click.stop="handleConfirm"
    >
      确认收货
    </el-button>

    <el-button
      v-if="canCancel"
      text
      size="small"
      :loading="loading.cancel"
      @click.stop="handleCancel"
    >
      取消订单
    </el-button>

    <!-- 发货表单放在这里而不是父页面：它只在「能发货」时才有意义 -->
    <el-dialog
      v-model="shipVisible"
      title="填写物流信息"
      width="min(440px, 92vw)"
      append-to-body
    >
      <el-form
        ref="shipFormRef"
        :model="shipForm"
        :rules="shipRules"
        label-position="top"
        class="ship-form"
      >
        <el-form-item label="快递单号" prop="trackingNo">
          <el-input
            v-model="shipForm.trackingNo"
            placeholder="例如 SF1234567890"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="快递公司（可不填）" prop="shippingCompany">
          <el-input
            v-model="shipForm.shippingCompany"
            placeholder="顺丰 / 中通 / 自己送…"
            maxlength="32"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.ship" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

/* 按钮之间的默认 margin 会让换行后左右不齐，统一交给 gap */
.order-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ship-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--c-ink-3);
}
</style>
