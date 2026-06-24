<template>
  <div class="rank-list-panel" v-loading="loading">
    <!-- 标题区域 -->
    <div v-if="title || subtitle" class="rank-list-panel__header">
      <div class="rank-list-panel__header-text">
        <span v-if="title" class="rank-list-panel__title">{{ title }}</span>
        <span v-if="subtitle" class="rank-list-panel__subtitle">{{ subtitle }}</span>
      </div>
      <slot name="header-actions" />
    </div>

    <!-- 空态 -->
    <div v-if="!loading && items.length === 0" class="rank-list-panel__empty">
      <span class="rank-list-panel__empty-icon">📊</span>
      <span class="rank-list-panel__empty-text">暂无排行数据</span>
    </div>

    <!-- 排行列表 -->
    <div v-else class="rank-list-panel__body">
      <!-- 表头 -->
      <div class="rank-list-panel__row rank-list-panel__row--head">
        <span class="rank-list-panel__cell rank-list-panel__cell--rank">#</span>
        <span class="rank-list-panel__cell rank-list-panel__cell--name">{{ nameColumnTitle }}</span>
        <span
          v-for="col in columns"
          :key="col.key"
          class="rank-list-panel__cell rank-list-panel__cell--stat"
          :style="{ width: col.width ? `${col.width}px` : 'auto', textAlign: col.align || 'center' }"
        >
          {{ col.title }}
        </span>
      </div>

      <!-- 数据行 -->
      <div
        v-for="(item, index) in items"
        :key="item.rank + '-' + item.name"
        class="rank-list-panel__row"
        :class="{
          'rank-list-panel__row--first': index === 0,
          'rank-list-panel__row--top3': index < 3,
          'rank-list-panel__row--clickable': clickable,
        }"
        @click="handleItemClick(item)"
      >
        <!-- 排名 -->
        <span
          class="rank-list-panel__cell rank-list-panel__cell--rank"
          :class="{
            'rank-list-panel__rank--gold': index === 0,
            'rank-list-panel__rank--silver': index === 1,
            'rank-list-panel__rank--bronze': index === 2,
          }"
        >
          {{ item.rank }}
        </span>

        <!-- 名称 + 球队信息 -->
        <span class="rank-list-panel__cell rank-list-panel__cell--name">
          <!-- 槽位渲染 -->
          <slot name="name-cell" :item="item" :index="index">
            <span class="rank-list-panel__name-text">{{ item.name }}</span>
            <span v-if="item.teamName" class="rank-list-panel__team-name">{{ item.teamName }}</span>
          </slot>
        </span>

        <!-- 数据列 -->
        <span
          v-for="col in columns"
          :key="col.key"
          class="rank-list-panel__cell rank-list-panel__cell--stat"
          :style="{ width: col.width ? `${col.width}px` : 'auto', textAlign: col.align || 'center' }"
        >
          <slot :name="`cell-${col.key}`" :item="item" :index="index">
            {{ formatCellValue(item, col) }}
          </slot>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RankItem, RankColumn } from '@/api/types'

interface Props {
  /** 排行榜标题 */
  title?: string
  /** 副标题 */
  subtitle?: string
  /** 名称列标题 */
  nameColumnTitle?: string
  /** 列定义 */
  columns: RankColumn[]
  /** 排行数据 */
  items: RankItem[]
  /** 加载状态 */
  loading?: boolean
  /** 是否支持点击 */
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  subtitle: '',
  nameColumnTitle: '名称',
  loading: false,
  clickable: false,
})

const emit = defineEmits<{
  (e: 'item-click', item: RankItem): void
}>()

/** 格式化单元格值 */
function formatCellValue(item: RankItem, col: RankColumn): string {
  if (col.render) {
    return col.render(item)
  }
  const val = item.extra?.[col.key] ?? item.value
  return String(val)
}

/** 处理行点击 */
function handleItemClick(item: RankItem) {
  if (props.clickable) {
    emit('item-click', item)
  }
}
</script>

<style scoped>
.rank-list-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.rank-list-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border-light);
}

.rank-list-panel__header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rank-list-panel__title {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.rank-list-panel__subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.rank-list-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  gap: 8px;
}

.rank-list-panel__empty-icon {
  font-size: 28px;
  opacity: 0.5;
}

.rank-list-panel__empty-text {
  font-size: 13px;
  color: var(--text-muted);
}

/* --- 表格体 --- */
.rank-list-panel__body {
  padding: 4px 0;
}

.rank-list-panel__row {
  display: flex;
  align-items: center;
  padding: 7px 20px;
  transition: background var(--duration-fast) var(--ease-smooth);
}

.rank-list-panel__row:hover {
  background: var(--bg-hover);
}

.rank-list-panel__row--head {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 20px 8px;
  border-bottom: 1px solid var(--border-light);
}

.rank-list-panel__row--head:hover {
  background: transparent;
}

.rank-list-panel__row--first {
  background: var(--accent-lighter);
  border-left: 3px solid var(--accent);
}

.rank-list-panel__row--clickable {
  cursor: pointer;
}

.rank-list-panel__row--clickable:hover {
  background: var(--bg-hover);
}

/* --- 单元格 --- */
.rank-list-panel__cell {
  display: flex;
  align-items: center;
  min-width: 0;
}

.rank-list-panel__cell--rank {
  width: 32px;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-heading);
  color: var(--text-muted);
  flex-shrink: 0;
}

.rank-list-panel__cell--name {
  flex: 1;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.rank-list-panel__cell--stat {
  font-size: 13px;
  font-family: var(--font-heading);
  font-weight: 500;
  color: var(--text-secondary);
}

/* 排名高亮 */
.rank-list-panel__rank--gold {
  color: var(--accent) !important;
}

.rank-list-panel__rank--silver {
  color: var(--purple-light) !important;
}

.rank-list-panel__rank--bronze {
  color: var(--cyan) !important;
}

.rank-list-panel__row--top3 .rank-list-panel__cell--name {
  font-weight: 600;
}

/* 名称和球队 */
.rank-list-panel__name-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-list-panel__team-name {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .rank-list-panel__row {
    padding: 6px 12px;
  }

  .rank-list-panel__header {
    padding: 12px;
  }

  .rank-list-panel__cell--stat {
    font-size: 12px;
  }
}
</style>
