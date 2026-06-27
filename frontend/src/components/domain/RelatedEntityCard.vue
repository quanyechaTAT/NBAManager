<template>
  <router-link
    :to="targetRoute"
    class="re-card"
    :class="`re-card--${entity.type}`"
    :title="`查看${entityTypeLabel}详情`"
  >
    <div class="re-card__icon">
      <!-- 球员图标 -->
      <svg v-if="entity.type === 'player'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
      <!-- 球队图标 -->
      <svg v-else-if="entity.type === 'team'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
      <!-- 比赛图标 -->
      <svg v-else-if="entity.type === 'game'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
        <rect x="2" y="7" width="20" height="15" rx="2" ry="2" />
        <polyline points="17 2 12 7 7 2" />
      </svg>
      <!-- 新闻/其他图标 -->
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="16" y1="13" x2="8" y2="13" />
        <line x1="16" y1="17" x2="8" y2="17" />
      </svg>
    </div>
    <div class="re-card__info">
      <span class="re-card__type">{{ entityTypeLabel }}</span>
      <span class="re-card__name">{{ entity.name }}</span>
      <span v-if="entity.description" class="re-card__desc">{{ entity.description }}</span>
    </div>
    <svg class="re-card__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
      <polyline points="9 18 15 12 9 6" />
    </svg>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { type RelatedEntity } from '@/api/types'

interface Props {
  /** 关联实体数据 */
  entity: RelatedEntity
}

const props = defineProps<Props>()

/** 实体类型标签 */
const entityTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    player: '球员',
    team: '球队',
    game: '比赛',
    news: '资讯'
  }
  return labels[props.entity.type] || '详情'
})

/** 根据实体类型生成路由跳转地址 */
const targetRoute = computed(() => {
  const { type, id } = props.entity
  switch (type) {
    case 'player':
      return { path: '/players/detail', query: { id: String(id) } }
    case 'team':
      return { path: '/teams/detail', query: { id: String(id) } }
    case 'game':
      return { path: '/match-detail', query: { gameId: String(id) } }
    case 'news':
      return { path: '/news', query: { id: String(id) } }
    default:
      return { path: '/' }
  }
})
</script>

<style scoped>
.re-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  text-decoration: none;
  color: var(--text-primary);
  transition: all 0.15s ease;
  cursor: pointer;
  max-width: 280px;
}

.re-card:hover {
  border-color: var(--accent);
  box-shadow: 0 2px 8px var(--accent-glow);
  transform: translateY(-1px);
}

.re-card--player {
  border-left: 3px solid var(--accent);
}

.re-card--team {
  border-left: 3px solid var(--team-primary, var(--accent));
}

.re-card--game {
  border-left: 3px solid #10b981;
}

.re-card--news {
  border-left: 3px solid #f59e0b;
}

.re-card__icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--accent-lighter);
  color: var(--accent);
}

.re-card--team .re-card__icon {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.re-card--game .re-card__icon {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.re-card--news .re-card__icon {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.re-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.re-card__type {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-dim);
}

.re-card__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.re-card__desc {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.re-card__arrow {
  flex-shrink: 0;
  color: var(--text-dim);
  transition: transform 0.15s ease;
}

.re-card:hover .re-card__arrow {
  transform: translateX(2px);
  color: var(--accent);
}
</style>
