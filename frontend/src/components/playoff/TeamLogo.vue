<template>
  <div class="team-logo-wrapper">
    <img
      v-if="currentSrc"
      :src="currentSrc"
      :alt="teamName"
      class="team-logo"
      @error="onImgError"
    />
    <div v-else class="team-logo-placeholder">{{ initials }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getTeamCode } from '@/utils/teamLogos'

interface Props {
  teamName: string
}

const props = defineProps<Props>()

/**
 * 图片加载错误计数：
 *   0 → 尝试本地图片  /images/team-logos/{code}.png
 *   1 → 尝试 CDN 兜底 https://a.espncdn.com/i/teamlogos/nba/500/{code}.png
 *  ≥2 → 显示占位符
 */
const imgErrorCount = ref(0)

const currentSrc = computed(() => {
  if (!props.teamName || props.teamName === '待定' || props.teamName === 'TBD') {
    return undefined
  }
  const code = getTeamCode(props.teamName)
  if (!code) return undefined

  if (imgErrorCount.value === 0) {
    return `/images/team-logos/${code}.png`
  }
  if (imgErrorCount.value === 1) {
    return `https://a.espncdn.com/i/teamlogos/nba/500/${code}.png`
  }
  return undefined
})

const initials = computed(() => {
  if (!props.teamName) return '?'
  return props.teamName.trim().charAt(0)
})

function onImgError() {
  imgErrorCount.value++
}
</script>

<style scoped>
.team-logo-wrapper {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.team-logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.team-logo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-stripe, #f1f5f9);
  border-radius: 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-dim, #9ca3af);
}
</style>
