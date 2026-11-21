<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AddressDialog from '../components/AddressDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import {
  deleteAddress,
  listAddresses,
  setDefaultAddress,
} from '../api/address'
import { formatTime } from '../utils/format'

/**
 * 收货地址管理。
 *
 * 【为什么设默认和删除之后都要重新拉列表】
 * 后端的排序和「默认地址」是联动的：
 * - 设默认：旧的默认会被清掉，列表顺序也变；
 * - 删默认：剩下的最新一条会自动顶上来。
 * 如果只在前端改本地那一张卡，看到的就会和后端不一致。
 * 地址最多 20 条、每条数据都小，重拉一次比在前端推演这些规则可靠。
 */
const addresses = ref([])
const loading = ref(true)
const dialogVisible = ref(false)
const editing = ref(null)
/** 正在设默认/删除的地址 id，用来只禁用那一张卡的按钮 */
const busyId = ref(null)

async function load() {
  loading.value = true
  const res = await listAddresses()
  if (res.code === 200) {
    addresses.value = res.data || []
  } else {
    ElMessage.error(res.message || '地址加载失败')
    addresses.value = []
  }
  loading.value = false
}

onMounted(load)

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(address) {
  editing.value = address
  dialogVisible.value = true
}

async function onSaved() {
  await load()
}

async function makeDefault(address) {
  if (address.isDefault === 1 || busyId.value) return

  busyId.value = address.id
  try {
    const res = await setDefaultAddress(address.id)
    if (res.code !== 200) {
      ElMessage.error(res.message || '设置失败')
      return
    }
    ElMessage.success('已设为默认地址')
    await load()
  } finally {
    busyId.value = null
  }
}

async function remove(address) {
  try {
    await ElMessageBox.confirm(
      `删除「${address.receiverName} ${address.region}」这个地址？`,
      '删除地址',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  busyId.value = address.id
  try {
    const res = await deleteAddress(address.id)
    if (res.code !== 200) {
      ElMessage.error(res.message || '删除失败')
      return
    }
    ElMessage.success('地址已删除')
    await load()
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <div class="page page--narrow">
    <header class="page-head">
      <div>
        <p class="eyebrow">Addresses</p>
        <h1 class="page-title">收货地址</h1>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增地址</el-button>
    </header>

    <p class="hint">
      最多 20 条。第一条会自动成为默认地址；下单时默认地址排在最前面。
    </p>

    <div v-if="loading" class="loading panel">正在取地址簿…</div>

    <EmptyState
      v-else-if="!addresses.length"
      glyph="址"
      text="还没有收货地址，填一个下单时就能直接选"
    >
      <el-button type="primary" @click="openCreate">新增收货地址</el-button>
    </EmptyState>

    <div v-else class="address-list">
      <article
        v-for="address in addresses"
        :key="address.id"
        class="address panel"
        :class="{ default: address.isDefault === 1 }"
      >
        <div class="address-top">
          <span class="name">{{ address.receiverName }}</span>
          <span class="phone">{{ address.phone }}</span>
          <span v-if="address.isDefault === 1" class="default-tag">默认</span>
        </div>

        <p class="region">{{ address.region }} {{ address.detail }}</p>
        <p class="created">添加于 {{ formatTime(address.createdAt) }}</p>

        <div class="actions">
          <button
            v-if="address.isDefault !== 1"
            type="button"
            class="text-btn"
            :disabled="busyId === address.id"
            @click="makeDefault(address)"
          >
            设为默认
          </button>
          <button type="button" class="text-btn" @click="openEdit(address)">编辑</button>
          <button
            type="button"
            class="text-btn danger"
            :disabled="busyId === address.id"
            @click="remove(address)"
          >
            删除
          </button>
        </div>
      </article>
    </div>

    <AddressDialog v-model="dialogVisible" :address="editing" @saved="onSaved" />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
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

.hint {
  margin: 0 0 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-ink-4);
}

.loading {
  padding: 60px 0;
  color: var(--c-ink-4);
  text-align: center;
}

.address-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.address {
  padding: 16px 18px 12px;
  border-left: 3px solid transparent;
}

/* 默认地址用一条金色左边，比整块变色安静 */
.address.default {
  border-left-color: var(--c-gold);
}

.address-top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-ink);
}

.phone {
  font-size: 12.5px;
  color: var(--c-ink-3);
}

.default-tag {
  padding: 0 6px;
  font-size: 10px;
  line-height: 17px;
  color: var(--c-gold);
  border: 1px solid var(--c-gold-line);
  border-radius: var(--radius-sm);
}

.region {
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--c-ink-2);
  overflow-wrap: anywhere;
}

.created {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--c-ink-4);
}

.actions {
  display: flex;
  gap: 14px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--c-line);
}

.text-btn {
  padding: 0;
  font-family: inherit;
  font-size: 12px;
  color: var(--c-ink-3);
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.18s var(--ease);
}

.text-btn:hover:not(:disabled) {
  color: var(--c-primary);
}

.text-btn.danger:hover:not(:disabled) {
  color: var(--c-danger);
}

.text-btn:disabled {
  color: var(--c-ink-4);
  cursor: wait;
}

@media (max-width: 560px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .address-list {
    grid-template-columns: 1fr;
  }
}
</style>
