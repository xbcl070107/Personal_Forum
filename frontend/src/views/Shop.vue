<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import { getShop, myShop } from '../api/shop'
import { listItems } from '../api/item'
import { formatTime } from '../utils/format'
import { useUserStore } from '../store/user'

/**
 * 店铺主页（W4）。
 *
 * 一个页面两份数据：
 * 1. /shop/{id}   —— 店本身：名字、店主昵称、简介、在售数
 * 2. /item/list?shopId={id} —— 店里卖的东西
 *
 * 【为什么商品不跟着店铺接口一起返回】
 * 商品要分页（一家店可能 200 件），店铺信息就一行。
 * 硬塞进同一个响应里，以后商品列表加筛选条件（价格区间、排序）
 * 就得把店铺那部分再重复传一遍。拆成两个接口，各自只管一件事。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shop = ref(null)
const shopLoading = ref(true)
const shopError = ref('')
/** 当前登录用户自己的店 id；没登录/没开店就是 null */
const myShopId = ref(null)

const items = ref([])
const total = ref(0)
const itemsLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)

const shopId = computed(() => route.params.id)

/** 店主的徽记：有昵称用昵称首字，不然用店名首字 —— 空店也要有个像样的块 */
const ownerInitial = computed(() => {
  const name = shop.value?.ownerNickname || shop.value?.name || ''
  return name ? name.charAt(0) : '店'
})

const typeText = computed(() => (shop.value?.type === 2 ? '公会店' : '个人店'))

/**
 * 这个店铺是不是我自己开的。
 *
 * 只有登录后才去问 /shop/mine：这个接口要 token，
 * 游客在公开的店铺页里调它会被 401 拦截器直接踢去登录页 —— 看店不该被踢。
 */
const isOwner = computed(() => Boolean(myShopId.value && shop.value?.id === myShopId.value))

async function loadMyShop() {
  if (!userStore.isLogin) return
  const res = await myShop()
  if (res.code === 200) myShopId.value = res.data?.id ?? null
}

/** 点商品卡进详情。详情要登录，游客会被路由守卫带去登录页，登录完再回来 */
function goItem(itemId) {
  if (!itemId) return
  router.push(`/item/${itemId}`)
}

function priceText(cents) {
  if (cents == null) return '—'
  return (cents / 100).toFixed(2)
}

function glyphOf(item) {
  return item.title ? item.title.charAt(0) : '物'
}

async function loadShop() {
  shopLoading.value = true
  shopError.value = ''
  shop.value = null

  const res = await getShop(shopId.value)
  shopLoading.value = false

  if (res.code === 200) {
    shop.value = res.data
  } else {
    // 店铺不存在（或者被封了）后端给 404，这里不跳走，就地放一个空状态
    shopError.value = res.message || '店铺不存在'
  }
}

async function loadItems() {
  itemsLoading.value = true
  try {
    const res = await listItems({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      shopId: shopId.value,
      sort: 'new',
    })

    if (res.code === 200 && res.data) {
      items.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      items.value = []
      total.value = 0
    }
  } finally {
    itemsLoading.value = false
  }
}

function handlePage(num) {
  pageNum.value = num
  loadItems()
}

/**
 * 从一家店跳到另一家店（同一个组件、不同 id）时，onMounted 不会重跑。
 * 所以监听 id，变了就把页码归零再拉一遍，否则会看到上一家店的商品。
 */
watch(shopId, () => {
  pageNum.value = 1
  loadShop()
  loadItems()
})

onMounted(() => {
  loadShop()
  loadItems()
  loadMyShop()
})
</script>

<template>
  <div class="page">
    <div class="back">
      <el-button text size="small" @click="router.push('/market')">‹ 回集市</el-button>
    </div>

    <div v-if="shopLoading" class="loading">正在找这家店…</div>

    <template v-else-if="shop">
      <!-- 店招：左边徽记，右边字号与账目 -->
      <header class="shop-head">
        <div class="shop-mark">{{ ownerInitial }}</div>

        <div class="shop-main">
          <div class="shop-title-row">
            <h1 class="shop-name">{{ shop.name }}</h1>
            <span class="shop-type">{{ typeText }}</span>
          </div>
          <p class="shop-owner">
            店主 · {{ shop.ownerNickname || '未知' }}
            <span class="dot">·</span>
            {{ formatTime(shop.createdAt) }} 开店
          </p>
          <p v-if="shop.description" class="shop-desc">{{ shop.description }}</p>
          <p v-else class="shop-desc shop-desc--empty">这家店还没写简介</p>
        </div>

        <div class="shop-side">
          <dl class="shop-stats">
            <div>
              <dt>在售</dt>
              <dd>{{ shop.itemCount ?? 0 }}</dd>
            </div>
            <div>
              <dt>关注</dt>
              <dd>{{ shop.followerCount ?? 0 }}</dd>
            </div>
          </dl>
          <el-button
            v-if="isOwner"
            size="small"
            class="manage-btn"
            @click="router.push('/merchant')"
          >
            管理店铺
          </el-button>
        </div>
      </header>

      <section class="shop-body">
        <h2 class="section-title">店里的东西</h2>

        <div v-if="itemsLoading" class="loading">正在搬货…</div>

        <div v-else-if="items.length" class="grid">
          <article
            v-for="item in items"
            :key="item.id"
            class="card"
            role="link"
            tabindex="0"
            @click="goItem(item.id)"
            @keydown.enter="goItem(item.id)"
          >
            <div class="thumb">
              <img
                v-if="item.images && item.images.length"
                :src="item.images[0]"
                :alt="item.title"
              />
              <span v-else class="thumb-mark">{{ glyphOf(item) }}</span>
            </div>
            <div class="card-body">
              <h3 class="card-title" :title="item.title">{{ item.title }}</h3>
              <p class="price"><span class="price-symbol">¥</span>{{ priceText(item.priceCents) }}</p>
              <div class="meta">
                <span>库存 {{ item.stock }}</span>
                <span>已售 {{ item.soldCount }}</span>
              </div>
            </div>
          </article>
        </div>

        <EmptyState v-else glyph="空" text="这家店还没摆出东西" compact />
      </section>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          background
          @current-change="handlePage"
        />
      </div>
    </template>

    <EmptyState v-else glyph="无" :text="shopError || '店铺不存在'">
      <el-button size="small" @click="router.push('/market')">去集市逛逛</el-button>
    </EmptyState>
  </div>
</template>

<style scoped>
.page {
  max-width: var(--page-w);
  margin: 0 auto;
  padding: 20px 20px 64px;
}

.back {
  margin-bottom: 8px;
}

.loading {
  padding: 40px 0;
  font-size: 13px;
  color: var(--c-ink-4);
  text-align: center;
}

/* ---- 店招 ---- */
.shop-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 20px 22px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
}

.shop-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--c-primary);
  background: var(--c-surface-solid);
  border-radius: var(--radius);
  box-shadow: inset 0 0 0 1px var(--c-primary-line);
}

.shop-main {
  flex: 1;
  min-width: 0;
}

.shop-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.shop-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 600;
  color: var(--c-ink);
}

.shop-type {
  padding: 1px 7px;
  font-size: 11.5px;
  color: var(--c-gold);
  border: 1px solid var(--c-gold-line);
  border-radius: var(--radius-full);
}

.shop-owner {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--c-ink-4);
}

.dot {
  margin: 0 5px;
}

.shop-desc {
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--c-ink-3);
}

.shop-desc--empty {
  color: var(--c-ink-4);
}

.shop-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.shop-stats {
  display: flex;
  gap: 22px;
  margin: 0;
  padding-left: 20px;
  border-left: 1px solid var(--c-line);
}

.manage-btn {
  margin-right: 2px;
}

.shop-stats dt {
  font-size: 12px;
  color: var(--c-ink-4);
}

.shop-stats dd {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: 22px;
  color: var(--c-ink-2);
}

/* ---- 商品区 ---- */
.shop-body {
  padding-top: 22px;
}

.section-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--c-ink-2);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(214px, 1fr));
  gap: 14px;
  padding-top: 12px;
}

.card {
  display: flex;
  flex-direction: column;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.18s var(--ease), transform 0.18s var(--ease);
}

.card:hover {
  border-color: var(--c-line-strong);
  transform: translateY(-1px);
}

.card:focus-visible {
  outline: 2px solid var(--c-primary-line);
  outline-offset: 2px;
}

.thumb {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--c-parchment-2);
  border-bottom: 1px solid var(--c-line-soft);
}

.thumb img {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-mark {
  font-family: var(--font-display);
  font-size: 34px;
  color: var(--c-gold);
  opacity: 0.6;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 13px 13px;
}

.card-title {
  margin: 0;
  font-size: 14.5px;
  font-weight: 500;
  line-height: 1.45;
  color: var(--c-ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price {
  margin: 0;
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--c-primary);
}

.price-symbol {
  font-size: 13px;
  margin-right: 2px;
}

.meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--c-ink-4);
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 26px;
}
</style>
