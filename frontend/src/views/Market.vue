<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import { listItemCategories, listItems } from '../api/item'

/**
 * 集市：全站商品的总览板（W3 的列表 + W4 的按店过滤共用这一个接口）。
 *
 * 【这个页面跟首页的区别】
 * 首页是「委托板」—— 帖子，走 /post/list，带审核状态。
 * 这里是「集市」—— 商品，走 /item/list，**只有上架的才会出现**
 * （下架过滤写在后端 listMarket 里，前端不用管）。
 *
 * 【为什么分类单独查一次接口】
 * 商品分类是 category 表里 scope='ITEM' 的那 5 条，和板块（日常/技术/水贴）
 * 是同一张表的不同分区。后端专门开了 /item/category/list，
 * 免得前端拿到 8 条再自己按 scope 筛 —— 那种筛法一旦以后加了新 scope 就会漏。
 */
const router = useRouter()

const categories = ref([])
const items = ref([])
const total = ref(0)
const loading = ref(false)

const pageNum = ref(1)
const pageSize = ref(12)
const categoryId = ref(null)
const keyword = ref('')
const sort = ref('new')

/**
 * 排序选项。
 *
 * value 必须和后端 listMarket 里 switch 的 case 一模一样：
 * new / priceAsc / priceDesc / hot。写错不会报错，只是白排 ——
 * 后端 default 分支兜到 new，页面看起来「排序按钮没反应」。
 */
const SORT_OPTIONS = [
  { value: 'new', label: '最新上架' },
  { value: 'priceAsc', label: '价格从低到高' },
  { value: 'priceDesc', label: '价格从高到低' },
  { value: 'hot', label: '卖得最多' },
]

/** 分类标签：前面加一个「全部」，点它把 categoryId 清空 */
const categoryTabs = computed(() => [{ id: null, name: '全部' }, ...categories.value])

/**
 * 分 → 元。
 *
 * 后端存的是分（priceCents），全程整数，不用 double ——
 * 用浮点存钱，0.1 + 0.2 会算出 0.30000000000000004，
 * 对账时那一分钱会对不上。转成文字只在最后一步做。
 */
function priceText(cents) {
  if (cents == null) return '—'
  return (cents / 100).toFixed(2)
}

/** 商品没传图时，用作占位的那块「印」。取标题首字，跟全站的印章是同一套 */
function glyphOf(item) {
  return item.title ? item.title.charAt(0) : '物'
}

async function loadCategories() {
  const res = await listItemCategories()
  if (res.code === 200) {
    categories.value = res.data || []
  }
}

async function loadItems() {
  loading.value = true
  try {
    const res = await listItems({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      // 空字符串不要传给后端：后端用 StringUtils.hasText 判断，
      // 传空串虽然也没事，但 URL 上挂一个 keyword= 看着像没生效
      keyword: keyword.value.trim() || undefined,
      categoryId: categoryId.value ?? undefined,
      sort: sort.value,
    })

    if (res.code === 200 && res.data) {
      items.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      items.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

/** 换分类 / 换排序 / 搜索，都要回到第 1 页 —— 否则可能停在一个不存在的页码上 */
function reload() {
  pageNum.value = 1
  loadItems()
}

function changeCategory(id) {
  categoryId.value = id
  reload()
}

function handlePage(num) {
  pageNum.value = num
  loadItems()
}

/** 点卖家那一行进店。卡片本身也可点，所以这里必须 stop，不然会一起跳商品详情 */
function goShop(shopId) {
  if (!shopId) return
  router.push(`/shop/${shopId}`)
}

/** 整张卡片进商品详情 */
function goItem(itemId) {
  if (!itemId) return
  router.push(`/item/${itemId}`)
}

onMounted(() => {
  loadCategories()
  loadItems()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">集市</p>
        <h1 class="page-title">冒险者集市</h1>
        <p class="page-sub">装备、书卷、杂货 —— 谁开什么店，这里就卖什么</p>
      </div>
    </header>

    <!-- 筛选条：分类在左，搜索和排序在右 -->
    <section class="filters">
      <div class="tabs">
        <button
          v-for="c in categoryTabs"
          :key="c.id ?? 'all'"
          class="tab"
          :class="{ active: categoryId === c.id }"
          type="button"
          @click="changeCategory(c.id)"
        >
          {{ c.name }}
        </button>
      </div>

      <div class="tools">
        <el-input
          v-model="keyword"
          placeholder="搜商品标题"
          clearable
          size="default"
          class="search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="sort" size="default" class="sort" @change="reload">
          <el-option v-for="o in SORT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" size="default" @click="reload">搜索</el-button>
      </div>
    </section>

    <!-- 商品网格 -->
    <section v-if="loading" class="loading">正在翻账本…</section>

    <section v-else-if="items.length" class="grid">
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
          <img v-if="item.images && item.images.length" :src="item.images[0]" :alt="item.title" />
          <span v-else class="thumb-mark">{{ glyphOf(item) }}</span>
        </div>

        <div class="card-body">
          <h2 class="card-title" :title="item.title">{{ item.title }}</h2>

          <p class="price">
            <span class="price-symbol">¥</span>{{ priceText(item.priceCents) }}
          </p>

          <div class="meta">
            <span class="stock">库存 {{ item.stock }}</span>
            <span class="sold">已售 {{ item.soldCount }}</span>
          </div>

          <!-- 卖家那一行：点它进店。stop 掉冒泡，否则会连商品一起跳 -->
          <button class="seller" type="button" @click.stop="goShop(item.shopId)">
            <span class="seller-mark">{{ item.shopName ? item.shopName.charAt(0) : '店' }}</span>
            <span class="seller-name">{{ item.shopName || '无名小店' }}</span>
            <span class="seller-go">进店 ›</span>
          </button>
        </div>
      </article>
    </section>

    <EmptyState v-else glyph="集" text="这个条件下还没有人摆摊，换个词试试" />

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
  </div>
</template>

<style scoped>
/*
 * 布局跟首页一脉：一条细线分区的平涂纸面，卡片只靠留白和 1px 线分层，
 * 不用阴影 —— 阴影一多，整页就变成后台模板。
 */
.page {
  max-width: var(--page-w);
  margin: 0 auto;
  padding: 34px 20px 64px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--c-line-strong);
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.22em;
  color: var(--c-gold);
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 600;
  color: var(--c-ink);
}

.page-sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--c-ink-3);
}

/* ---- 筛选 ---- */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--c-line);
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tab {
  padding: 6px 12px;
  font-size: 13px;
  color: var(--c-ink-3);
  background: transparent;
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  cursor: pointer;
  transition: color 0.18s var(--ease), border-color 0.18s var(--ease);
}

.tab:hover {
  color: var(--c-ink);
  border-color: var(--c-line-strong);
}

.tab.active {
  color: var(--c-primary);
  border-color: var(--c-primary-line);
  background: var(--c-primary-soft);
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search {
  width: 200px;
}

.sort {
  width: 132px;
}

/* ---- 网格 ---- */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(214px, 1fr));
  gap: 14px;
  padding: 20px 0 4px;
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
  /* 标题最多两行，再多就把卡片撑得高低不齐了 */
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

.seller {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 2px;
  padding: 7px 8px;
  font-size: 12.5px;
  color: var(--c-ink-3);
  text-align: left;
  background: var(--c-parchment-2);
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  transition: border-color 0.18s var(--ease), color 0.18s var(--ease);
}

.seller:hover {
  color: var(--c-primary);
  border-color: var(--c-primary-line);
}

.seller-mark {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  color: var(--c-primary);
  background: var(--c-surface-solid);
  border-radius: var(--radius-sm);
  box-shadow: inset 0 0 0 1px var(--c-primary-line);
}

.seller-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.seller-go {
  color: var(--c-ink-4);
}

.loading {
  padding: 46px 0;
  font-size: 13px;
  color: var(--c-ink-4);
  text-align: center;
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 26px;
}
</style>
