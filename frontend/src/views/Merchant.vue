<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import ShopDialog from '../components/ShopDialog.vue'
import ItemDialog from '../components/ItemDialog.vue'
import { myShop } from '../api/shop'
import { changeItemStatus, myItems } from '../api/item'
import { formatTime, moneyText } from '../utils/format'

/**
 * 我的店铺：开店 / 改资料 / 发布商品 / 上下架。
 *
 * 【为什么商品列表用 /item/mine 而不是 /item/list】
 * 集市列表只返回上架商品；店主需要看到自己下架的货，才能重新上架。
 * /item/mine 是唯一包含下架商品的接口。
 *
 * 【上下架为什么改完要重拉】
 * 后端改的是 status，列表里的状态条、按钮文案都跟着它变；
 * 而且下架后如果正处在「只看上架」的筛选里，这件货应该消失。
 * 数据量很小，重拉一页比在前端改本地对象可靠。
 */
const router = useRouter()

const loading = ref(true)
const shop = ref(null)
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 8
const shopDialog = ref(false)
const itemDialog = ref(false)
const togglingId = ref(null)

const hasShop = computed(() => Boolean(shop.value?.id))
const typeText = computed(() => (shop.value?.type === 2 ? '公会店' : '个人店'))
const seal = computed(() => (shop.value?.name || '店').charAt(0))

async function loadShop() {
  const res = await myShop()
  if (res.code !== 200) {
    ElMessage.error(res.message || '店铺加载失败')
    shop.value = null
    return
  }
  shop.value = res.data
}

async function loadItems() {
  if (!shop.value?.id) {
    items.value = []
    total.value = 0
    return
  }

  const res = await myItems({ pageNum: pageNum.value, pageSize })
  if (res.code !== 200) {
    ElMessage.error(res.message || '商品加载失败')
    items.value = []
    total.value = 0
    return
  }
  items.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function load() {
  loading.value = true
  await loadShop()
  await loadItems()
  loading.value = false
}

onMounted(load)

function openCreateShop() {
  shopDialog.value = true
}

function openEditShop() {
  shopDialog.value = true
}

async function onShopSaved() {
  await loadShop()
  // 刚开完店时商品列表还是空的，顺手拉一次，页面不用等用户刷新
  if (shop.value?.id && !items.value.length) await loadItems()
}

async function onItemSaved() {
  // 发布成功后 itemCount 也变了，店铺头和商品列表一起刷新
  pageNum.value = 1
  await Promise.all([loadShop(), loadItems()])
}

function changePage(page) {
  pageNum.value = page
  loadItems()
}

async function toggleStatus(item) {
  if (togglingId.value) return

  const next = item.status === 1 ? 0 : 1
  const verb = next === 1 ? '上架' : '下架'

  togglingId.value = item.id
  try {
    const res = await changeItemStatus(item.id, next)
    if (res.code !== 200) {
      ElMessage.error(res.message || `${verb}失败`)
      return
    }
    ElMessage.success(`已${verb}`)
    await loadItems()
  } finally {
    togglingId.value = null
  }
}

function coverOf(item) {
  return item.images?.[0] || ''
}

function openItem(item) {
  router.push(`/item/${item.id}`)
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Merchant</p>
        <h1 class="page-title">我的店铺</h1>
      </div>
      <RouterLink to="/market" class="back-link">去集市看看 →</RouterLink>
    </header>

    <div v-if="loading" class="loading panel">正在开店门…</div>

    <!-- 未开店：不报错，直接把「开店」当成这个页面的第一件事 -->
    <section v-else-if="!hasShop" class="onboarding panel fade-up">
      <div class="seal">店</div>
      <h2 class="onboarding-title">你还没有自己的店</h2>
      <p class="onboarding-text">
        开一家店就能上架商品，集市里的冒险者会看到它。<br />
        一个人只能开一家，店名和简介以后随时能改。
      </p>
      <el-button type="primary" size="large" @click="openCreateShop">开一家店</el-button>
    </section>

    <template v-else>
      <!-- 店招 -->
      <section class="shop-head panel fade-up">
        <div class="avatar" :class="{ empty: !shop.avatarUrl }">
          <img v-if="shop.avatarUrl" :src="shop.avatarUrl" alt="店铺头像" />
          <span v-else>{{ seal }}</span>
        </div>

        <div class="shop-main">
          <div class="shop-name-line">
            <h2 class="shop-name">{{ shop.name }}</h2>
            <span class="type-tag">{{ typeText }}</span>
          </div>
          <p v-if="shop.description" class="shop-desc">{{ shop.description }}</p>
          <p v-else class="shop-desc muted">还没写店铺简介。</p>
          <p class="shop-meta">
            开店于 {{ formatTime(shop.createdAt) }}
            <span class="dot">·</span>
            {{ shop.itemCount }} 件商品
            <span class="dot">·</span>
            {{ shop.followerCount }} 人关注
          </p>
        </div>

        <div class="shop-actions">
          <el-button @click="openEditShop">编辑资料</el-button>
          <el-button @click="router.push(`/shop/${shop.id}`)">查看店铺</el-button>
        </div>
      </section>

      <!-- 商品工具栏 -->
      <div class="toolbar">
        <div class="toolbar-title">
          <h3>我的商品</h3>
          <span class="count">共 {{ total }} 件</span>
        </div>
        <el-button type="primary" @click="itemDialog = true">+ 发布商品</el-button>
      </div>

      <EmptyState
        v-if="!items.length"
        glyph="货"
        text="货架上还是空的，发布第一件商品吧"
      >
        <el-button type="primary" @click="itemDialog = true">发布商品</el-button>
      </EmptyState>

      <template v-else>
        <div class="item-grid">
          <article v-for="item in items" :key="item.id" class="item-card panel">
            <button type="button" class="thumb" :class="{ empty: !coverOf(item) }" @click="openItem(item)">
              <img v-if="coverOf(item)" :src="coverOf(item)" :alt="item.title" />
              <span v-else class="stamp">{{ item.title.charAt(0) }}</span>
              <span class="status" :class="item.status === 1 ? 'on' : 'off'">
                {{ item.status === 1 ? '在售' : '已下架' }}
              </span>
            </button>

            <div class="item-body">
              <h4 class="item-title">{{ item.title }}</h4>
              <p class="item-price">¥ {{ moneyText(item.priceCents) }}</p>
              <p class="item-stats">库存 {{ item.stock }} · 已售 {{ item.soldCount }} · 浏览 {{ item.viewCount }}</p>
            </div>

            <footer class="item-foot">
              <button type="button" class="text-btn" @click="openItem(item)">查看</button>
              <button
                type="button"
                class="text-btn"
                :class="item.status === 1 ? 'danger' : 'primary'"
                :disabled="togglingId === item.id"
                @click="toggleStatus(item)"
              >
                {{ item.status === 1 ? '下架' : '重新上架' }}
              </button>
            </footer>
          </article>
        </div>

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
      </template>
    </template>

    <ShopDialog v-model="shopDialog" :shop="hasShop ? shop : null" @saved="onShopSaved" />
    <ItemDialog v-model="itemDialog" @saved="onItemSaved" />
  </div>
</template>

<style scoped>
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

.onboarding {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 54px 24px 50px;
  text-align: center;
}

.seal {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  font-family: var(--font-display);
  font-size: 28px;
  color: var(--c-gold);
  border: 1px solid var(--c-gold-line);
  border-radius: var(--radius-sm);
  transform: rotate(-3deg);
}

.onboarding-title {
  margin: 18px 0 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--c-ink);
}

.onboarding-text {
  margin: 10px 0 22px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--c-ink-3);
}

.shop-head {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  padding: 22px 24px;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 72px;
  height: 72px;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 28px;
  color: var(--c-primary);
  background: var(--c-surface-solid);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
}

.avatar.empty {
  color: var(--c-gold);
  background:
    radial-gradient(circle at 50% 40%, var(--c-gold-soft), transparent 65%),
    var(--c-parchment-2);
  border-color: var(--c-gold-line);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-main {
  flex: 1;
  min-width: 0;
}

.shop-name-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.shop-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--c-ink);
  overflow-wrap: anywhere;
}

.type-tag {
  padding: 1px 8px;
  font-size: 11px;
  color: var(--c-gold);
  border: 1px solid var(--c-gold-line);
  border-radius: var(--radius-full);
}

.shop-desc {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--c-ink-3);
  overflow-wrap: anywhere;
}

.shop-desc.muted {
  color: var(--c-ink-4);
}

.shop-meta {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.dot {
  margin: 0 5px;
  color: var(--c-line-strong);
}

.shop-actions {
  display: flex;
  gap: 8px;
  flex: none;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 22px 0 14px;
}

.toolbar-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.toolbar-title h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--c-ink);
}

.count {
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(214px, 1fr));
  gap: 14px;
}

.item-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.thumb {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  padding: 0;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: none;
  border-bottom: 1px solid var(--c-line);
  cursor: pointer;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s var(--ease);
}

.thumb:hover img {
  transform: scale(1.03);
}

.stamp {
  font-family: var(--font-display);
  font-size: 30px;
  color: var(--c-gold);
}

.status {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 7px;
  font-size: 10.5px;
  border: 1px solid currentColor;
  border-radius: var(--radius-sm);
  background: rgba(253, 252, 248, 0.9);
}

.status.on {
  color: var(--c-success);
}

.status.off {
  color: var(--c-ink-4);
}

.item-body {
  flex: 1;
  padding: 11px 12px 8px;
}

.item-title {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--c-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-price {
  margin: 7px 0 0;
  font-family: var(--font-display);
  font-size: 19px;
  color: var(--c-primary);
}

.item-stats {
  margin: 4px 0 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--c-ink-4);
}

.item-foot {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid var(--c-line-soft);
}

.text-btn {
  padding: 0;
  font-family: inherit;
  font-size: 11.5px;
  color: var(--c-ink-3);
  background: none;
  border: none;
  cursor: pointer;
}

.text-btn:hover:not(:disabled) {
  color: var(--c-primary);
}

.text-btn.danger:hover:not(:disabled) {
  color: var(--c-danger);
}

.text-btn.primary {
  color: var(--c-primary);
}

.text-btn:disabled {
  color: var(--c-ink-4);
  cursor: wait;
}

.pager {
  display: flex;
  justify-content: center;
  padding: 20px 0 4px;
}

@media (max-width: 640px) {
  .shop-head {
    flex-wrap: wrap;
  }

  .shop-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
