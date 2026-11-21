<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import OrderActions from '../components/OrderActions.vue'
import { listOrders } from '../api/order'
import { formatTime, moneyText, ORDER_STATUS_MAP, orderStatusOf } from '../utils/format'

/**
 * 订单列表：买家 / 卖家两个视角共用一页。
 *
 * 【为什么用 role 参数而不是拆两个页面】
 * 两边看到的字段、卡片、分页完全一样，只有「我是谁」不同；
 * 后台用同一个接口，前端也用一个页面，改样式时不会改漏一边。
 *
 * 【卖家视角没有店】
 * GET /order/list?role=seller 在后端会回 404「你还没有开店」。
 * 这不是错误，是一个正常的空状态 —— 引导去开店，而不是弹一个红色报错。
 */
const route = useRoute()
const router = useRouter()

const role = ref(route.query.role === 'seller' ? 'seller' : 'buyer')
const status = ref(null)
const orders = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const noShop = ref(false)

const statusTabs = computed(() => [
  { value: null, label: '全部' },
  ...Object.entries(ORDER_STATUS_MAP).map(([value, meta]) => ({
    value: Number(value),
    label: meta.text,
  })),
])

const isSeller = computed(() => role.value === 'seller')

async function load() {
  loading.value = true
  noShop.value = false

  const params = { role: role.value, pageNum: pageNum.value, pageSize }
  // 不选「全部」才带 status：后端把 undefined 当「不过滤」
  if (status.value !== null) params.status = status.value

  const res = await listOrders(params)

  if (res.code === 200) {
    orders.value = res.data?.records || []
    total.value = res.data?.total || 0
  } else if (isSeller.value && res.code === 404) {
    noShop.value = true
    orders.value = []
    total.value = 0
  } else {
    ElMessage.error(res.message || '订单加载失败')
    orders.value = []
    total.value = 0
  }

  loading.value = false
}

onMounted(load)

function switchRole(next) {
  if (role.value === next) return
  role.value = next
  status.value = null
  pageNum.value = 1
  orders.value = []
  load()
}

function switchStatus(value) {
  if (status.value === value) return
  status.value = value
  pageNum.value = 1
  load()
}

function changePage(page) {
  pageNum.value = page
  load()
}

function openOrder(id) {
  router.push(`/order/${id}`)
}

/** 订单状态被某个动作改了：重新拉当前页，卡片会跟着换状态或移出当前筛选 */
function onUpdated() {
  load()
}

function coverOf(order) {
  return order.items?.[0]?.itemCoverUrl || ''
}

function firstTitle(order) {
  const first = order.items?.[0]
  if (!first) return '（商品信息缺失）'
  return order.itemCount > 1 ? `${first.itemTitle} 等 ${order.itemCount} 件` : first.itemTitle
}
</script>

<template>
  <div class="page page--wide">
    <header class="page-head">
      <div>
        <p class="eyebrow">Orders</p>
        <h1 class="page-title">我的订单</h1>
      </div>
      <div class="head-side">
        <RouterLink to="/addresses" class="addr-link">收货地址</RouterLink>
        <p class="head-note">待付款的订单 15 分钟内有效，超时自动关闭。</p>
      </div>
    </header>

    <!-- 买家/卖家 -->
    <div class="role-switch">
      <button
        type="button"
        class="role-btn"
        :class="{ active: role === 'buyer' }"
        @click="switchRole('buyer')"
      >
        我买到的
      </button>
      <button
        type="button"
        class="role-btn"
        :class="{ active: role === 'seller' }"
        @click="switchRole('seller')"
      >
        我卖出的
      </button>
    </div>

    <!-- 状态筛选：和角色分开一层，切角色时状态重置为全部 -->
    <div class="status-tabs">
      <button
        v-for="tab in statusTabs"
        :key="String(tab.value)"
        type="button"
        class="status-btn"
        :class="{ active: status === tab.value }"
        @click="switchStatus(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loading" class="loading panel">正在翻账本…</div>

    <EmptyState
      v-else-if="noShop"
      glyph="店"
      text="你还没有开店，卖出的订单会出现在这里"
    >
      <el-button type="primary" @click="router.push('/merchant')">去开一家店</el-button>
    </EmptyState>

    <EmptyState
      v-else-if="!orders.length"
      glyph="单"
      :text="isSeller ? '还没有人下过单' : '还没有订单，去集市逛逛'"
    >
      <el-button v-if="!isSeller" type="primary" @click="router.push('/market')">去集市</el-button>
    </EmptyState>

    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="order panel">
        <header class="order-head">
          <div class="order-no">
            <span class="no">{{ order.orderNo }}</span>
            <span class="time">{{ formatTime(order.createdAt) }}</span>
          </div>
          <span class="status" :class="`tone-${orderStatusOf(order.status).tone}`">
            {{ orderStatusOf(order.status).text }}
          </span>
        </header>

        <div class="order-body" @click="openOrder(order.id)">
          <div class="order-cover" :class="{ empty: !coverOf(order) }">
            <img v-if="coverOf(order)" :src="coverOf(order)" alt="" />
            <span v-else class="stamp">{{ firstTitle(order).charAt(0) }}</span>
          </div>

          <div class="order-main">
            <p class="order-title">{{ firstTitle(order) }}</p>
            <p class="order-shop">{{ order.shopName }}</p>
            <p class="order-receiver">
              {{ order.receiverName }} · {{ order.receiverRegion }}
            </p>
          </div>

          <div class="order-money">
            <p class="pay">¥ {{ moneyText(order.payCents) }}</p>
            <p class="count">共 {{ order.itemCount }} 件</p>
          </div>
        </div>

        <footer class="order-foot">
          <RouterLink :to="`/order/${order.id}`" class="detail-link">查看详情 →</RouterLink>
          <OrderActions :order="order" :role="role" @updated="onUpdated" />
        </footer>
      </article>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          background
          @current-change="changePage"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page--wide {
  max-width: var(--page-w);
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
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

.head-note {
  margin: 0;
  font-size: 12px;
  color: var(--c-ink-4);
}

.head-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.addr-link {
  font-size: 12px;
  color: var(--c-ink-3);
  text-decoration: none;
  border-bottom: 1px solid var(--c-line);
}

.addr-link:hover {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary-line);
}

.role-switch {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.role-btn {
  padding: 7px 18px;
  font-family: inherit;
  font-size: 13px;
  color: var(--c-ink-3);
  background: transparent;
  border: 1px solid var(--c-line);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all 0.18s var(--ease);
}

.role-btn:hover {
  color: var(--c-primary);
  border-color: var(--c-primary-line);
}

.role-btn.active {
  color: var(--c-surface-solid);
  background: var(--c-primary);
  border-color: var(--c-primary);
}

.status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--c-line);
}

.status-btn {
  padding: 8px 12px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--c-ink-3);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.18s var(--ease), border-color 0.18s var(--ease);
}

.status-btn:hover {
  color: var(--c-ink);
}

.status-btn.active {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary);
}

.loading {
  padding: 60px 0;
  color: var(--c-ink-4);
  text-align: center;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order {
  overflow: hidden;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px;
  background: var(--c-parchment-2);
  border-bottom: 1px solid var(--c-line-soft);
}

.order-no {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.no {
  font-family: var(--font-body);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--c-ink-2);
}

.time {
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.status {
  flex: none;
  padding: 2px 9px;
  font-size: 11.5px;
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

.order-body {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
}

.order-cover {
  display: grid;
  place-items: center;
  flex: none;
  width: 66px;
  height: 66px;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.order-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stamp {
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--c-gold);
}

.order-main {
  flex: 1;
  min-width: 0;
}

.order-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--c-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-shop {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-ink-3);
}

.order-receiver {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.order-money {
  flex: none;
  text-align: right;
}

.pay {
  margin: 0;
  font-family: var(--font-display);
  font-size: 19px;
  color: var(--c-primary);
}

.count {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 20px;
  border-top: 1px solid var(--c-line-soft);
}

.detail-link {
  font-size: 12px;
  color: var(--c-ink-3);
  text-decoration: none;
}

.detail-link:hover {
  color: var(--c-primary);
}

.pager {
  display: flex;
  justify-content: center;
  padding: 18px 0 4px;
}

@media (max-width: 560px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .head-note {
    display: none;
  }

  .order-body {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .order-money {
    margin-left: 80px;
  }

  .time {
    display: none;
  }
}
</style>
