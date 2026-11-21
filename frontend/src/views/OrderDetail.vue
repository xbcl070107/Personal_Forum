<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import OrderActions from '../components/OrderActions.vue'
import { getOrder } from '../api/order'
import { myShop } from '../api/shop'
import { formatTime, moneyText, orderStatusOf } from '../utils/format'

/**
 * 订单详情。
 *
 * 【角色是猜出来的，不是后端告诉的】
 * OrderVO 里没有 userId，也没有「我是买家还是卖家」的字段。
 * 能拿来判断的只有 shopId：如果这张订单的店就是我开的店，那我就是卖家。
 * 后端又禁止买自己店里的东西，所以这个判断不会两头都成立。
 *
 * 【状态和步骤条】
 * status：0 待付款 → 1 待发货 → 2 待收货 → 3 已完成，4 是取消。
 * 取消不是流程里的一步，所以不画进步骤条，单独用一条提示说明。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const order = ref(null)
const shop = ref(null)
const notFound = ref(false)

const isSeller = computed(() => Boolean(shop.value?.id && order.value?.shopId === shop.value.id))
const role = computed(() => (isSeller.value ? 'seller' : 'buyer'))
const statusMeta = computed(() => orderStatusOf(order.value?.status))

const stepActive = computed(() => {
  if (!order.value) return 0
  // 提交订单=1、已付款=2、已发货=3、已完成=4
  return { 0: 1, 1: 2, 2: 3, 3: 4 }[order.value.status] ?? 0
})

const canceled = computed(() => order.value?.status === 4)

const expireText = computed(() => {
  if (!order.value || order.value.status !== 0) return ''
  return `${formatTime(order.value.expireAt)} 前付款有效`
})

const items = computed(() => order.value?.items || [])

async function load() {
  loading.value = true
  notFound.value = false

  const [orderRes, shopRes] = await Promise.all([getOrder(route.params.id), myShop()])
  if (shopRes.code === 200) shop.value = shopRes.data

  if (orderRes.code !== 200 || !orderRes.data) {
    order.value = null
    notFound.value = true
  } else {
    order.value = orderRes.data
  }
  loading.value = false
}

onMounted(load)
watch(() => route.params.id, load)

function onUpdated() {
  // 状态变了：重新拉一次，步骤条、按钮、时间都会跟着变
  load()
}

function coverOf(item) {
  return item.itemCoverUrl || ''
}
</script>

<template>
  <div class="page page--mid">
    <div class="crumb">
      <RouterLink to="/orders">我的订单</RouterLink>
      <span class="sep">/</span>
      <span class="muted">订单详情</span>
    </div>

    <div v-if="loading" class="loading panel">正在核对订单…</div>

    <EmptyState v-else-if="notFound" glyph="无" text="订单不存在，或者不属于你">
      <el-button type="primary" @click="router.push('/orders')">回订单列表</el-button>
    </EmptyState>

    <template v-else-if="order">
      <header class="detail-head panel">
        <div class="head-main">
          <p class="eyebrow">{{ isSeller ? '卖出的订单' : '买到的订单' }}</p>
          <h1 class="order-no">{{ order.orderNo }}</h1>
          <p class="created">下单于 {{ formatTime(order.createdAt) }}</p>
        </div>
        <div class="head-side">
          <span class="status" :class="`tone-${statusMeta.tone}`">{{ statusMeta.text }}</span>
          <OrderActions :order="order" :role="role" @updated="onUpdated" />
        </div>
      </header>

      <!-- 取消单：不画步骤条，直接说清楚为什么停了 -->
      <div v-if="canceled" class="canceled panel">
        <p class="canceled-title">订单已取消</p>
        <p class="canceled-time">
          关闭于 {{ formatTime(order.closedAt) }}
          <span v-if="order.closeReason"> · 原因：{{ order.closeReason }}</span>
        </p>
        <p class="canceled-note">这件商品的库存已经退回店里，可以再下一次单。</p>
      </div>

      <section v-else class="panel steps-panel">
        <el-steps :active="stepActive" finish-status="success" align-center>
          <el-step title="提交订单" />
          <el-step title="付款" />
          <el-step title="发货" />
          <el-step title="收货完成" />
        </el-steps>

        <p v-if="order.status === 0" class="expire">{{ expireText }}</p>
        <p v-else-if="order.status === 1 && !isSeller" class="expire">
          已付款，等卖家发货。
        </p>
        <p v-else-if="order.status === 1 && isSeller" class="expire">
          买家已付款，填好快递单号就可以发货。
        </p>
        <p v-else-if="order.status === 2" class="expire">
          包裹在路上，收到后点「确认收货」。
        </p>
      </section>

      <div class="grid">
        <div class="left">
          <section class="panel block">
            <h2 class="block-title">商品</h2>
            <div class="items">
              <div v-for="it in items" :key="it.itemId" class="item">
                <div class="item-cover" :class="{ empty: !coverOf(it) }">
                  <img v-if="coverOf(it)" :src="coverOf(it)" alt="" />
                  <span v-else class="stamp">{{ it.itemTitle.charAt(0) }}</span>
                </div>

                <div class="item-main">
                  <RouterLink :to="`/item/${it.itemId}`" class="item-title">{{ it.itemTitle }}</RouterLink>
                  <p class="item-unit">
                    ¥ {{ moneyText(it.priceCents) }}
                    <span class="times">× {{ it.quantity }}</span>
                  </p>
                </div>

                <p class="item-subtotal">¥ {{ moneyText(it.subtotalCents) }}</p>
              </div>
            </div>
          </section>

          <section class="panel block">
            <h2 class="block-title">收货信息</h2>
            <dl class="info-list">
              <div>
                <dt>收货人</dt>
                <dd>{{ order.receiverName }}</dd>
              </div>
              <div>
                <dt>手机号</dt>
                <dd>{{ order.receiverPhone }}</dd>
              </div>
              <div>
                <dt>地址</dt>
                <dd>{{ order.receiverRegion }} {{ order.receiverDetail }}</dd>
              </div>
            </dl>
          </section>

          <section v-if="order.trackingNo || order.shippingCompany" class="panel block">
            <h2 class="block-title">物流</h2>
            <dl class="info-list">
              <div v-if="order.shippingCompany">
                <dt>快递公司</dt>
                <dd>{{ order.shippingCompany }}</dd>
              </div>
              <div v-if="order.trackingNo">
                <dt>运单号</dt>
                <dd class="mono">{{ order.trackingNo }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <aside class="right">
          <section class="panel block amounts-block">
            <h2 class="block-title">金额</h2>
            <dl class="amounts">
              <div>
                <dt>商品合计</dt>
                <dd>¥ {{ moneyText(order.totalCents) }}</dd>
              </div>
              <div v-if="order.discountCents > 0">
                <dt>优惠</dt>
                <dd class="discount">- ¥ {{ moneyText(order.discountCents) }}</dd>
              </div>
              <div class="total">
                <dt>实付</dt>
                <dd>¥ {{ moneyText(order.payCents) }}</dd>
              </div>
            </dl>
          </section>

          <section class="panel block">
            <h2 class="block-title">时间线</h2>
            <dl class="info-list compact">
              <div>
                <dt>下单</dt>
                <dd>{{ formatTime(order.createdAt) }}</dd>
              </div>
              <div v-if="order.paidAt">
                <dt>付款</dt>
                <dd>{{ formatTime(order.paidAt) }}</dd>
              </div>
              <div v-if="order.shippedAt">
                <dt>发货</dt>
                <dd>{{ formatTime(order.shippedAt) }}</dd>
              </div>
              <div v-if="order.finishedAt">
                <dt>完成</dt>
                <dd>{{ formatTime(order.finishedAt) }}</dd>
              </div>
              <div v-if="order.closedAt">
                <dt>关闭</dt>
                <dd>{{ formatTime(order.closedAt) }}</dd>
              </div>
            </dl>
          </section>

          <RouterLink :to="`/shop/${order.shopId}`" class="shop-card panel">
            <span class="shop-label">店铺</span>
            <span class="shop-name">{{ order.shopName }}</span>
            <span class="shop-arrow">→</span>
          </RouterLink>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page--mid {
  max-width: 920px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 12.5px;
  color: var(--c-ink-4);
}

.crumb a {
  color: var(--c-ink-3);
  text-decoration: none;
}

.crumb a:hover {
  color: var(--c-primary);
}

.sep {
  color: var(--c-line-strong);
}

.loading {
  padding: 60px 0;
  color: var(--c-ink-4);
  text-align: center;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  margin-bottom: 14px;
}

.eyebrow {
  margin: 0 0 5px;
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: var(--c-gold);
  text-transform: uppercase;
}

.order-no {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--c-ink);
  overflow-wrap: anywhere;
}

.created {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--c-ink-4);
}

.head-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.status {
  padding: 3px 11px;
  font-size: 12px;
  border: 1px solid currentColor;
  border-radius: var(--radius-full);
}

.tone-warning {
  color: var(--c-warning);
}

.tone-primary {
  color: var(--c-primary);
}

.tone-success {
  color: var(--c-success);
}

.tone-info {
  color: var(--c-ink-4);
}

.canceled {
  padding: 20px 24px;
  margin-bottom: 14px;
  background: var(--c-danger-soft);
  border-left: 3px solid var(--c-danger);
}

.canceled-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 17px;
  color: var(--c-danger);
}

.canceled-time,
.canceled-note {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-ink-3);
}

.steps-panel {
  padding: 22px 26px 18px;
  margin-bottom: 14px;
}

.steps-panel :deep(.el-step__title) {
  font-size: 12.5px;
}

.expire {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--c-warning);
  text-align: center;
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 14px;
  align-items: start;
}

.left,
.right {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.block {
  padding: 18px 20px 20px;
}

.block-title {
  margin: 0 0 14px;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--c-ink);
}

.items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-cover {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.item-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stamp {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--c-gold);
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  display: block;
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--c-ink);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-title:hover {
  color: var(--c-primary);
}

.item-unit {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--c-ink-4);
}

.times {
  margin-left: 4px;
}

.item-subtotal {
  flex: none;
  margin: 0;
  font-size: 13px;
  color: var(--c-ink-2);
}

.info-list {
  margin: 0;
}

.info-list > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  padding: 6px 0;
  font-size: 12.5px;
  line-height: 1.65;
}

.info-list.compact > div {
  grid-template-columns: 52px minmax(0, 1fr);
  padding: 4px 0;
  font-size: 12px;
}

.info-list dt {
  color: var(--c-ink-4);
}

.info-list dd {
  margin: 0;
  color: var(--c-ink-2);
  overflow-wrap: anywhere;
}

.mono {
  font-family: var(--font-body);
  letter-spacing: 0.03em;
}

.amounts {
  margin: 0;
}

.amounts > div {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 12.5px;
}

.amounts dt {
  color: var(--c-ink-3);
}

.amounts dd {
  margin: 0;
  color: var(--c-ink-2);
}

.discount {
  color: var(--c-success);
}

.amounts .total {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--c-line);
}

.amounts .total dt {
  color: var(--c-ink-2);
}

.amounts .total dd {
  font-family: var(--font-display);
  font-size: 22px;
  color: var(--c-primary);
}

.shop-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  text-decoration: none;
  transition: border-color 0.18s var(--ease);
}

.shop-card:hover {
  border-color: var(--c-primary-line);
}

.shop-label {
  font-size: 11px;
  color: var(--c-ink-4);
}

.shop-name {
  flex: 1;
  font-size: 13px;
  color: var(--c-ink);
}

.shop-arrow {
  color: var(--c-ink-4);
}

@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .detail-head {
    flex-direction: column;
    gap: 14px;
  }

  .head-side {
    align-items: flex-start;
  }

  .steps-panel {
    padding: 18px 12px 14px;
  }
}
</style>
