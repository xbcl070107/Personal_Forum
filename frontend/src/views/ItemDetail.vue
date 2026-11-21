<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import { getItem, listItemCategories } from '../api/item'
import { myShop } from '../api/shop'
import { formatTime, moneyText } from '../utils/format'

/**
 * 商品详情页。
 *
 * 【为什么这个页面必须登录才能看】
 * 后端 GET /item/{id} 要登录；而且下架商品只有店主本人能看。
 * 路由 meta.needLogin 已经挡住游客，这里只处理「登录了但商品不存在/已下架」。
 *
 * 【怎么判断「我是不是店主」】
 * ItemVO 里没有 ownerUserId，只有 shopId；所以拿 myShop().id 和 item.shopId 比。
 * 没开店时 myShop 的 data 是 null，不是报错。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const item = ref(null)
const shop = ref(null)
/** 商品分类表：ItemVO 只有 categoryId，名字要单独查一次 */
const categories = ref([])
const quantity = ref(1)
const activeImage = ref(0)
const notFound = ref(false)

const isOwner = computed(() => Boolean(shop.value?.id && item.value?.shopId === shop.value.id))
const images = computed(() => item.value?.images || [])
const maxQuantity = computed(() => Math.min(item.value?.stock ?? 0, 99))
const soldOut = computed(() => (item.value?.stock ?? 0) <= 0)
const cover = computed(() => images.value[activeImage.value] || images.value[0] || '')
const initial = computed(() => (item.value?.title || '物').charAt(0))

/** 分类 id → 名字。分类没选或查不到时返回空串，页面上就不显示这一项 */
const categoryName = computed(() => {
  if (!item.value?.categoryId) return ''
  return categories.value.find((c) => c.id === item.value.categoryId)?.name || ''
})

async function load() {
  loading.value = true
  notFound.value = false
  activeImage.value = 0
  quantity.value = 1

  const [itemRes, shopRes, catRes] = await Promise.all([
    getItem(route.params.id),
    myShop(),
    listItemCategories(),
  ])
  if (shopRes.code === 200) shop.value = shopRes.data
  if (catRes.code === 200) categories.value = catRes.data || []

  if (itemRes.code !== 200 || !itemRes.data) {
    notFound.value = true
    item.value = null
  } else {
    item.value = itemRes.data
  }
  loading.value = false
}

onMounted(load)

// 从「店里另一件商品」点过来时组件会复用，必须跟着 id 重新拉
watch(() => route.params.id, load)

function buy() {
  if (!item.value || soldOut.value || isOwner.value) return

  if (quantity.value < 1 || quantity.value > maxQuantity.value) {
    ElMessage.warning('数量不对')
    return
  }
  router.push({ name: 'checkout', params: { id: item.value.id }, query: { quantity: quantity.value } })
}

function goMerchant() {
  router.push({ name: 'merchant' })
}
</script>

<template>
  <div class="page">
    <div class="crumb">
      <RouterLink to="/market">集市</RouterLink>
      <span class="sep">/</span>
      <RouterLink v-if="item" :to="`/shop/${item.shopId}`">{{ item.shopName }}</RouterLink>
      <span v-else class="muted">商品</span>
    </div>

    <div v-if="loading" class="loading panel">正在翻找这件东西…</div>

    <EmptyState v-else-if="notFound" glyph="寻" text="这件东西不在了，可能刚被下架">
      <el-button type="primary" @click="router.push('/market')">回集市看看</el-button>
    </EmptyState>

    <template v-else-if="item">
      <div class="detail fade-up">
        <!-- 左：图。没有图就放一个印章占位，比空白好看，也说明了「没传图」 -->
        <section class="gallery">
          <div class="main-image" :class="{ empty: !cover }">
            <img v-if="cover" :src="cover" :alt="item.title" />
            <span v-else class="stamp">{{ initial }}</span>
          </div>

          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(url, i) in images"
              :key="url"
              type="button"
              class="thumb"
              :class="{ active: i === activeImage }"
              @click="activeImage = i"
            >
              <img :src="url" alt="" />
            </button>
          </div>
        </section>

        <!-- 右：价格、库存、购买 -->
        <section class="info">
          <div class="shop-line">
            <RouterLink :to="`/shop/${item.shopId}`" class="shop-name">{{ item.shopName }}</RouterLink>
            <span class="type-tag" :class="{ off: item.status !== 1 }">
              {{ item.status === 1 ? '在售' : '已下架' }}
            </span>
          </div>

          <h1 class="title">{{ item.title }}</h1>

          <div class="price">
            <span class="yuan">¥</span>
            <span class="amount">{{ moneyText(item.priceCents) }}</span>
          </div>

          <div class="stats">
            <span>库存 <b>{{ item.stock }}</b></span>
            <span>已售 <b>{{ item.soldCount }}</b></span>
            <span>浏览 <b>{{ item.viewCount }}</b></span>
          </div>

          <div class="divider"></div>

          <div v-if="!isOwner" class="buy-row">
            <label class="qty-label">数量</label>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.max(maxQuantity, 1)"
              :disabled="soldOut"
              controls-position="right"
            />
            <el-button type="primary" size="large" class="buy-btn" :disabled="soldOut" @click="buy">
              {{ soldOut ? '已售罄' : '立即购买' }}
            </el-button>
          </div>

          <div v-else class="owner-row">
            <p class="owner-hint">这是你自己店里的商品。</p>
            <el-button type="primary" @click="goMerchant">去店铺管理</el-button>
          </div>

          <p class="note">
            下单后库存会先扣掉；15 分钟内不付款，订单会自动关闭并把库存还回来。
          </p>

          <div class="meta">
            <span>上架于 {{ formatTime(item.createdAt) }}</span>
            <span v-if="categoryName" class="dot">·</span>
            <span v-if="categoryName">{{ categoryName }}</span>
          </div>
        </section>
      </div>

      <section class="panel description">
        <p class="section-eyebrow">商品详情</p>
        <p v-if="item.description" class="desc-text">{{ item.description }}</p>
        <p v-else class="desc-empty">店主没有写详情。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
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

.muted {
  color: var(--c-ink-4);
}

.loading {
  padding: 60px 0;
  color: var(--c-ink-4);
  text-align: center;
}

.detail {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 34px;
  align-items: start;
}

.main-image {
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius-lg);
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.main-image.empty {
  background:
    radial-gradient(circle at 50% 40%, var(--c-gold-soft), transparent 62%),
    var(--c-parchment-2);
}

.stamp {
  display: grid;
  place-items: center;
  width: 86px;
  height: 86px;
  font-family: var(--font-display);
  font-size: 38px;
  color: var(--c-gold);
  border: 2px solid var(--c-gold-line);
  border-radius: var(--radius);
  transform: rotate(-2deg);
}

.thumbs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  overflow-x: auto;
}

.thumb {
  flex: none;
  width: 62px;
  height: 62px;
  padding: 0;
  overflow: hidden;
  background: var(--c-parchment-2);
  border: 1px solid var(--c-line);
  border-radius: var(--radius);
  cursor: pointer;
  opacity: 0.72;
  transition: opacity 0.18s var(--ease), border-color 0.18s var(--ease);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb:hover,
.thumb.active {
  opacity: 1;
  border-color: var(--c-primary-line);
}

.shop-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.shop-name {
  font-size: 13px;
  color: var(--c-ink-3);
  text-decoration: none;
  border-bottom: 1px solid transparent;
}

.shop-name:hover {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary-line);
}

.type-tag {
  padding: 1px 7px;
  font-size: 11px;
  color: var(--c-success);
  border: 1px solid currentColor;
  border-radius: var(--radius-full);
}

/* 店主能看到自己已下架的货，标签别还写「在售」 */
.type-tag.off {
  color: var(--c-ink-4);
}

.title {
  margin: 12px 0 14px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--c-ink);
  overflow-wrap: anywhere;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--c-primary);
}

.yuan {
  font-size: 16px;
}

.amount {
  font-family: var(--font-display);
  font-size: 36px;
  line-height: 1;
}

.stats {
  display: flex;
  gap: 18px;
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--c-ink-4);
}

.stats b {
  font-weight: 500;
  color: var(--c-ink-2);
}

.divider {
  height: 1px;
  margin: 22px 0;
  background: var(--c-line-soft);
}

.buy-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.qty-label {
  font-size: 12.5px;
  color: var(--c-ink-3);
}

.buy-btn {
  flex: 1;
  min-width: 0;
}

.owner-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  background: var(--c-primary-soft);
  border: 1px solid var(--c-primary-line);
  border-radius: var(--radius);
}

.owner-hint {
  margin: 0;
  font-size: 13px;
  color: var(--c-primary);
}

.note {
  margin: 18px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--c-ink-4);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 18px;
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.dot {
  color: var(--c-line-strong);
}

.description {
  margin-top: 34px;
  padding: 24px 26px 26px;
}

.section-eyebrow {
  margin: 0 0 12px;
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--c-gold);
  text-transform: uppercase;
}

.desc-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--c-ink-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.desc-empty {
  margin: 0;
  font-size: 13px;
  color: var(--c-ink-4);
}

@media (max-width: 760px) {
  .detail {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .title {
    font-size: 23px;
  }

  .amount {
    font-size: 30px;
  }

  .description {
    padding: 20px;
  }
}
</style>
