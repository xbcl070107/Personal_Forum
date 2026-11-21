<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AddressDialog from '../components/AddressDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import { getItem } from '../api/item'
import { listAddresses } from '../api/address'
import { createOrder } from '../api/order'
import { moneyText } from '../utils/format'

/**
 * 结算页：确认数量、选收货地址、下单。
 *
 * 【clientToken 是干什么的】
 * 后端用 (user_id, client_token) 唯一键防重复下单：同一个 token 再提交一次，
 * 会命中唯一键并回 400「请勿重复提交」，而不是又生成一张订单。
 * 所以 token 必须在「进页面时」生成一次，点提交、失败重试都用同一个；
 * 如果每次点击都重新生成，防重就失效了。
 *
 * 【为什么下单成功后是 replace 而不是 push】
 * 结算页已经完成使命，退回上一页不该再回到「待提交」的结算页 —— 那会让人以为要下第二单。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const item = ref(null)
const addresses = ref([])
const addressId = ref(null)
const quantity = ref(1)
const addressDialog = ref(false)
const notFound = ref(false)
/** 页面生命周期内只生成一次，见上面的注释 */
const clientToken = ref('')

const maxQuantity = computed(() => Math.min(item.value?.stock ?? 0, 99))
const subtotalCents = computed(() => (item.value?.priceCents || 0) * quantity.value)
const selectedAddress = computed(() => addresses.value.find((a) => a.id === addressId.value) || null)
const cover = computed(() => item.value?.images?.[0] || '')
const initial = computed(() => (item.value?.title || '物').charAt(0))

function makeClientToken() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 老浏览器兜底：时间戳 + 随机串，长度控制在 64 以内（后端 client_token VARCHAR(64)）
  return `co-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

function clampQuantity() {
  const max = Math.max(maxQuantity.value, 1)
  const n = Number(quantity.value) || 1
  quantity.value = Math.min(Math.max(n, 1), max)
}

async function loadAddresses() {
  const res = await listAddresses()
  if (res.code !== 200) {
    ElMessage.error(res.message || '地址加载失败')
    addresses.value = []
    return
  }
  addresses.value = res.data || []

  // 默认地址排在最前（后端已排序），没有选中或选中的地址被删了就落到第一条
  const stillThere = addresses.value.some((a) => a.id === addressId.value)
  if (!stillThere) {
    addressId.value = addresses.value.find((a) => a.isDefault === 1)?.id ?? addresses.value[0]?.id ?? null
  }
}

async function load() {
  loading.value = true
  notFound.value = false
  clientToken.value = makeClientToken()

  const fromQuery = Number(route.query.quantity) || 1
  quantity.value = fromQuery

  const [itemRes] = await Promise.all([getItem(route.params.id), loadAddresses()])
  if (itemRes.code !== 200 || !itemRes.data) {
    notFound.value = true
    item.value = null
    loading.value = false
    return
  }

  item.value = itemRes.data
  clampQuantity()
  loading.value = false
}

onMounted(load)

watch(() => route.params.id, load)

function onAddressSaved() {
  // 新增地址后重新拉一次：新地址可能是「第一条自动默认」，排序会变
  loadAddresses().then(() => {
    if (!addressId.value && addresses.value.length) {
      addressId.value = addresses.value[0].id
    }
  })
}

async function submit() {
  if (!item.value || submitting.value) return

  if (!addresses.value.length) {
    ElMessage.warning('先填一个收货地址')
    addressDialog.value = true
    return
  }
  if (!addressId.value) {
    ElMessage.warning('选一个收货地址')
    return
  }

  clampQuantity()
  submitting.value = true
  try {
    const res = await createOrder({
      itemId: item.value.id,
      quantity: quantity.value,
      addressId: addressId.value,
      clientToken: clientToken.value,
    })

    if (res.code !== 200) {
      // 重复提交说明这一单其实已经建好了，直接把用户送去订单列表，别让他再点一次
      if (res.message?.includes('重复提交')) {
        ElMessage.warning('这一单已经提交过了，去订单里看看')
        router.replace('/orders')
        return
      }
      ElMessage.error(res.message || '下单失败')
      // 库存不足时把最新库存拉回来，输入框上限跟着变
      if (res.message?.includes('库存')) load()
      return
    }

    ElMessage.success('订单已创建，15 分钟内付款有效')
    router.replace(`/order/${res.data.id}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Checkout</p>
        <h1 class="page-title">结算</h1>
      </div>
      <RouterLink to="/market" class="back-link">← 回集市</RouterLink>
    </header>

    <div v-if="loading" class="loading panel">正在核对库存…</div>

    <EmptyState v-else-if="notFound" glyph="空" text="这件商品不在了">
      <el-button type="primary" @click="router.push('/market')">回集市</el-button>
    </EmptyState>

    <div v-else-if="item" class="layout">
      <div class="main">
        <!-- 收货地址：卡片式单选，比下拉框多看到「谁、在哪」 -->
        <section class="panel block">
          <div class="block-head">
            <h2 class="block-title">收货地址</h2>
            <div class="head-tools">
              <RouterLink to="/addresses" class="manage-addr">管理地址</RouterLink>
              <el-button text type="primary" @click="addressDialog = true">+ 新增地址</el-button>
            </div>
          </div>

          <div v-if="!addresses.length" class="no-address">
            <p>还没有收货地址，先填一个。</p>
            <el-button type="primary" @click="addressDialog = true">新增收货地址</el-button>
          </div>

          <div v-else class="address-grid">
            <button
              v-for="a in addresses"
              :key="a.id"
              type="button"
              class="address-card"
              :class="{ active: a.id === addressId }"
              @click="addressId = a.id"
            >
              <span class="addr-top">
                <b>{{ a.receiverName }}</b>
                <span class="phone">{{ a.phone }}</span>
                <span v-if="a.isDefault === 1" class="default-tag">默认</span>
              </span>
              <span class="addr-text">{{ a.region }} {{ a.detail }}</span>
              <span class="check">{{ a.id === addressId ? '✓' : '' }}</span>
            </button>
          </div>
        </section>

        <!-- 商品：只买一件，所以这里没有购物车，只有数量 -->
        <section class="panel block">
          <div class="block-head">
            <h2 class="block-title">商品</h2>
            <RouterLink :to="`/shop/${item.shopId}`" class="shop-link">{{ item.shopName }}</RouterLink>
          </div>

          <div class="goods">
            <div class="goods-cover" :class="{ empty: !cover }">
              <img v-if="cover" :src="cover" :alt="item.title" />
              <span v-else class="stamp">{{ initial }}</span>
            </div>

            <div class="goods-main">
              <RouterLink :to="`/item/${item.id}`" class="goods-title">{{ item.title }}</RouterLink>
              <p class="goods-price">¥ {{ moneyText(item.priceCents) }}</p>
              <p class="goods-stock">库存 {{ item.stock }}</p>
            </div>

            <div class="goods-qty">
              <span class="qty-label">数量</span>
              <el-input-number
                v-model="quantity"
                :min="1"
                :max="Math.max(maxQuantity, 1)"
                controls-position="right"
                @change="clampQuantity"
              />
            </div>
          </div>
        </section>
      </div>

      <!-- 右侧金额栏：小计、运费、应付 -->
      <aside class="panel summary">
        <h2 class="block-title">订单金额</h2>

        <dl class="amounts">
          <div>
            <dt>商品小计</dt>
            <dd>¥ {{ moneyText(subtotalCents) }}</dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd class="free">包邮</dd>
          </div>
        </dl>

        <div class="total">
          <span>应付</span>
          <b>¥ {{ moneyText(subtotalCents) }}</b>
        </div>

        <el-button type="primary" size="large" class="pay-btn" :loading="submitting" @click="submit">
          提交订单
        </el-button>

        <p class="tip">
          提交后 15 分钟内付款有效，超时订单自动关闭、库存退回。
        </p>
      </aside>
    </div>

    <AddressDialog v-model="addressDialog" :address="null" @saved="onAddressSaved" />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-line-strong);
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: var(--c-gold);
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 600;
  color: var(--c-ink);
}

.back-link {
  font-size: 12.5px;
  color: var(--c-ink-3);
  text-decoration: none;
}

.back-link:hover {
  color: var(--c-primary);
}

.loading {
  padding: 60px 0;
  color: var(--c-ink-4);
  text-align: center;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 22px;
  align-items: start;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.block {
  padding: 20px 22px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.block-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
  color: var(--c-ink);
}

.shop-link {
  font-size: 12px;
  color: var(--c-ink-3);
  text-decoration: none;
}

.shop-link:hover {
  color: var(--c-primary);
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.manage-addr {
  font-size: 12px;
  color: var(--c-ink-3);
  text-decoration: none;
  border-bottom: 1px solid var(--c-line);
}

.manage-addr:hover {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary-line);
}

/* 地址卡：选中用墨蓝细边 + 左上角勾，避免整块深色太吵 */
.address-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 10px;
}

.address-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px 14px;
  font-family: inherit;
  text-align: left;
  background: var(--c-surface-solid);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  cursor: pointer;
  transition: border-color 0.18s var(--ease), background 0.18s var(--ease);
}

.address-card:hover {
  border-color: var(--c-line-strong);
}

.address-card.active {
  background: var(--c-primary-soft);
  border-color: var(--c-primary-line);
}

.addr-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--c-ink);
}

.phone {
  font-size: 12px;
  color: var(--c-ink-3);
}

.default-tag {
  padding: 0 5px;
  font-size: 10px;
  line-height: 16px;
  color: var(--c-gold);
  border: 1px solid var(--c-gold-line);
  border-radius: var(--radius-sm);
}

.addr-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-ink-3);
  overflow-wrap: anywhere;
}

.check {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 12px;
  color: var(--c-primary);
}

.no-address {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 18px;
  background: var(--c-parchment-2);
  border: 1px dashed var(--c-line-strong);
  border-radius: var(--radius);
}

.no-address p {
  margin: 0;
  font-size: 13px;
  color: var(--c-ink-3);
}

.goods {
  display: flex;
  gap: 14px;
  align-items: center;
}

.goods-cover {
  display: grid;
  place-items: center;
  flex: none;
  width: 88px;
  height: 88px;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.goods-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stamp {
  font-family: var(--font-display);
  font-size: 26px;
  color: var(--c-gold);
}

.goods-main {
  flex: 1;
  min-width: 0;
}

.goods-title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--c-ink);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.goods-title:hover {
  color: var(--c-primary);
}

.goods-price {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--c-primary);
}

.goods-stock {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.goods-qty {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
}

.qty-label {
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.summary {
  position: sticky;
  top: calc(var(--topbar-h) + 16px);
  padding: 20px 20px 22px;
}

.amounts {
  margin: 16px 0 0;
}

.amounts > div {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 13px;
}

.amounts dt {
  color: var(--c-ink-3);
}

.amounts dd {
  margin: 0;
  color: var(--c-ink-2);
}

.free {
  color: var(--c-success);
}

.total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--c-line);
  font-size: 13px;
  color: var(--c-ink-3);
}

.total b {
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 600;
  color: var(--c-primary);
}

.pay-btn {
  width: 100%;
  margin-top: 18px;
}

.tip {
  margin: 12px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--c-ink-4);
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .summary {
    position: static;
  }
}

@media (max-width: 560px) {
  .goods {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .goods-qty {
    align-items: flex-start;
    width: 100%;
  }
}
</style>
