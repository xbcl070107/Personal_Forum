<script setup>
/**
 * 空状态。
 *
 * 【为什么不直接用 el-empty】
 * el-empty 默认那张插画是冷调 3D 蓝白盒子，压在羊皮纸底上非常突兀 ——
 * 全站是暖纸 + 墨蓝 + 公会金，中间突然出现一块冰蓝色，一眼就出戏。
 * 它的 image 属性可以换图，但还要额外提供一张图；这里干脆自己拼：
 * 一枚「印」字小印章 + 说明 + 可选的行动按钮，和全站的印章是一套。
 *
 * 【为什么不做成全局组件到处直接用】
 * 只有四个页面用到，而且每处的文案和按钮都不一样。
 * 放在 components/ 里是为了不把这段标记在四个 .vue 里各抄一遍。
 */
defineProps({
  /** 「印」字印章里的那个字，各页面自己定（空 / 静 / 清 之类） */
  glyph: { type: String, default: '空' },
  /** 说明文字 */
  text: { type: String, default: '这里还什么都没有' },
  /** 字号小一点，用于卡片内的次级空状态 */
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="empty" :class="{ compact }">
    <span class="empty-seal">{{ glyph }}</span>
    <p class="empty-text">{{ text }}</p>
    <!-- 有按钮才渲染这一层，避免留一截空白 -->
    <div v-if="$slots.default" class="empty-action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 46px 20px 50px;
  text-align: center;
}

.compact {
  padding: 26px 16px 28px;
  gap: 8px;
}

/*
 * 印章：双描边 + 极淡的金底。
 * 用 inset box-shadow 做内圈，而不是 border —— 一圈 border 加一圈 outline
 * 会各自带自己的圆角，叠起来边角容易错位。
 */
.empty-seal {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--c-gold);
  background: rgba(253, 250, 243, 0.9);
  box-shadow:
    inset 0 0 0 1px var(--c-gold-line),
    inset 0 0 0 5px rgba(253, 250, 243, 0.9),
    inset 0 0 0 6px rgba(184, 137, 43, 0.2),
    var(--shadow-sm);
}

.compact .empty-seal {
  width: 38px;
  height: 38px;
  font-size: 16px;
  border-radius: 12px;
}

.empty-text {
  margin: 0;
  font-size: 13.5px;
  color: var(--c-ink-3);
}

.compact .empty-text {
  font-size: 12.5px;
}

.empty-action {
  margin-top: 2px;
}
</style>
