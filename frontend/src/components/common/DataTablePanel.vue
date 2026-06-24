<template>
  <div class="data-table-panel">
    <!-- 表格标题栏 -->
    <div v-if="title || $slots['table-header']" class="data-table-panel__header">
      <div v-if="title" class="data-table-panel__title-wrap">
        <span class="data-table-panel__title">{{ title }}</span>
        <span v-if="total > 0" class="data-table-panel__total">共 {{ total }} 条</span>
      </div>
      <slot name="table-header" />
    </div>

    <!-- 空态 -->
    <div v-if="!loading && data.length === 0" class="data-table-panel__empty">
      <div class="data-table-panel__empty-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" />
          <path d="M16 7V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v3" />
        </svg>
      </div>
      <p class="data-table-panel__empty-text">{{ emptyText }}</p>
      <slot name="empty-actions" />
    </div>

    <!-- Element Plus 表格 -->
    <el-table
      v-else
      :data="data"
      :border="border"
      :stripe="stripe"
      :row-key="rowKey"
      :default-sort="defaultSort"
      :highlight-current-row="highlightCurrentRow"
      :show-header="showHeader"
      :max-height="maxHeight"
      :height="height"
      v-loading="loading"
      :empty-text="emptyText"
      class="data-table-panel__table"
      @sort-change="handleSortChange"
      @row-click="handleRowClick"
      @selection-change="handleSelectionChange"
    >
      <!-- 多选列 -->
      <el-table-column
        v-if="selectable"
        type="selection"
        width="45"
        align="center"
        fixed="left"
      />

      <!-- 序号列 -->
      <el-table-column
        v-if="showIndex"
        type="index"
        label="#"
        width="50"
        align="center"
        fixed="left"
      />

      <!-- 通过 slot 注入的自定义列 -->
      <slot />

      <!-- 底部操作槽 -->
      <template #empty>
        <div class="data-table-panel__empty-inner">
          <span class="data-table-panel__empty-inner-icon">📭</span>
          <span>{{ emptyText }}</span>
        </div>
      </template>
    </el-table>

    <!-- 分页器 -->
    <div v-if="total > 0 && showPagination" class="data-table-panel__pagination">
      <el-pagination
        background
        :layout="paginationLayout"
        :total="total"
        v-model:current-page="currentPage"
        v-model:page-size="currentPageSize"
        :page-sizes="pageSizes"
        :small="smallPagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface SortChangeEvent {
  prop: string
  order: 'ascending' | 'descending' | null
}

interface Props {
  /** 表格数据 */
  data: Record<string, unknown>[]
  /** 总条数（用于分页） */
  total?: number
  /** 当前页码（v-model） */
  page?: number
  /** 每页条数（v-model） */
  pageSize?: number
  /** 分页每页可选条数 */
  pageSizes?: number[]
  /** 表格标题 */
  title?: string
  /** 空态文本 */
  emptyText?: string
  /** 是否显示边框 */
  border?: boolean
  /** 是否显示斑马纹 */
  stripe?: boolean
  /** 行数据的唯一标识字段 */
  rowKey?: string
  /** 默认排序 */
  defaultSort?: { prop: string; order: 'ascending' | 'descending' }
  /** 是否高亮当前行 */
  highlightCurrentRow?: boolean
  /** 是否显示表头 */
  showHeader?: boolean
  /** 表格最大高度 */
  maxHeight?: string | number
  /** 表格固定高度 */
  height?: string | number
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 是否显示多选列 */
  selectable?: boolean
  /** 是否显示分页器 */
  showPagination?: boolean
  /** 分页器布局 */
  paginationLayout?: string
  /** 是否使用小型分页器 */
  smallPagination?: boolean
  /** 加载状态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  total: 0,
  page: 1,
  pageSize: 10,
  pageSizes: () => [10, 20, 50],
  title: '',
  emptyText: '暂无数据',
  border: true,
  stripe: true,
  rowKey: 'id',
  defaultSort: undefined,
  highlightCurrentRow: false,
  showHeader: true,
  maxHeight: undefined,
  height: undefined,
  showIndex: false,
  selectable: false,
  showPagination: true,
  paginationLayout: 'total, prev, pager, next, sizes',
  smallPagination: false,
  loading: false,
})

const emit = defineEmits<{
  (e: 'page-change', page: number, pageSize: number): void
  (e: 'size-change', pageSize: number): void
  (e: 'sort-change', sort: { prop: string; order: 'ascending' | 'descending' | null }): void
  (e: 'row-click', row: Record<string, unknown>): void
  (e: 'selection-change', selection: Record<string, unknown>[]): void
  (e: 'update:page', page: number): void
  (e: 'update:page-size', pageSize: number): void
}>()

const currentPage = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val),
})

const currentPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:page-size', val),
})

/** 选中的行 */
const selectedRows = ref<Record<string, unknown>[]>([])

/** 分页切换 */
function handlePageChange(page: number) {
  emit('page-change', page, props.pageSize)
}

/** 每页条数切换 */
function handleSizeChange(size: number) {
  emit('size-change', size)
  emit('page-change', 1, size)
}

/** 排序变化 */
function handleSortChange(sort: SortChangeEvent) {
  emit('sort-change', sort)
}

/** 行点击 */
function handleRowClick(row: Record<string, unknown>) {
  emit('row-click', row)
}

/** 多选变化 */
function handleSelectionChange(selection: Record<string, unknown>[]) {
  selectedRows.value = selection
  emit('selection-change', selection)
}
</script>

<style scoped>
.data-table-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

/* --- 头部 --- */
.data-table-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border-light);
}

.data-table-panel__title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.data-table-panel__title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.data-table-panel__total {
  font-size: 12px;
  color: var(--text-muted);
}

/* --- 空态 --- */
.data-table-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.data-table-panel__empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: var(--text-muted);
}

.data-table-panel__empty-text {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.data-table-panel__empty-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.data-table-panel__empty-inner-icon {
  font-size: 28px;
  opacity: 0.5;
}

/* --- 表格覆盖 --- */
.data-table-panel__table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(26, 32, 48, 0.8);
  --el-table-row-hover-bg-color: var(--bg-hover);
  --el-table-border-color: var(--border-light);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}

/* --- 分页器 --- */
.data-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  border-top: 1px solid var(--border-light);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .data-table-panel__header {
    padding: 12px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .data-table-panel__pagination {
    justify-content: center;
    padding: 12px;
  }
}
</style>
