<template>
  <div
    class="team-badge"
    :class="[`team-badge--${size}`, { 'team-badge--clickable': clickable }]"
    :style="badgeStyle"
    @click="handleClick"
  >
    <div class="team-badge__logo-wrap" :style="logoWrapStyle">
      <img
        v-if="logoUrl"
        :src="logoUrl"
        :alt="teamName"
        class="team-badge__logo"
        @error="onLogoError"
      />
      <span v-else class="team-badge__logo-fallback" :style="fallbackStyle">
        {{ initials }}
      </span>
    </div>

    <div v-if="showName" class="team-badge__info">
      <span class="team-badge__name">{{ teamName }}</span>
      <span v-if="subtitle" class="team-badge__subtitle">{{ subtitle }}</span>
    </div>

    <div v-if="showColorBar" class="team-badge__color-bar" :style="colorBarStyle" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getTeamColor } from '@/utils/teamColors'
import { getTeamLogo, getTeamLogoFallback } from '@/utils/teamLogos'

interface Props {
  /** 球队名称（全称如 "Los Angeles Lakers"） */
  teamName: string
  /** 尺寸 */
  size?: 'sm' | 'md' | 'lg'
  /** 是否显示球队名称 */
  showName?: boolean
  /** 是否底部队色条 */
  showColorBar?: boolean
  /** 副标题（如战绩 "42-18"） */
  subtitle?: string
  /** 是否可点击 */
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  showName: true,
  showColorBar: true,
  subtitle: '',
  clickable: false
})

const emit = defineEmits<{
  (e: 'click', teamName: string): void
}>()

/** Logo 加载失败时切换到 CDN 兜底 */
const logoFailed = ref(false)

/** Logo URL */
const logoUrl = computed(() => {
  if (logoFailed.value) {
    return getTeamLogoFallback(props.teamName)
  }
  return getTeamLogo(props.teamName)
})

/** Logo 加载失败回调 */
function onLogoError() {
  if (!logoFailed.value) {
    logoFailed.value = true
  }
}

/** 球队颜色 */
const teamColor = computed(() => getTeamColor(props.teamName))

/** 球队名首字母缩写 */
const initials = computed(() => {
  if (!props.teamName) return '?'
  const words = props.teamName.split(' ')
  if (words.length >= 2) {
    return (words[0][0] + words[words.length - 1][0]).toUpperCase()
  }
  return props.teamName.substring(0, 2).toUpperCase()
})

/** 尺寸配置 */
const sizeConfig = computed(() => {
  const configs = {
    sm: { logo: 24, fontSize: 12, gap: 6, radius: 4, padding: '4px 8px', barHeight: 2 },
    md: { logo: 32, fontSize: 13, gap: 8, radius: 6, padding: '6px 10px', barHeight: 3 },
    lg: { logo: 44, fontSize: 15, gap: 10, radius: 8, padding: '8px 14px', barHeight: 4 },
  }
  return configs[props.size]
})

/** 外层容器样式 */
const badgeStyle = computed(() => ({
  padding: sizeConfig.value.padding,
  gap: `${sizeConfig.value.gap}px`,
  borderRadius: `${sizeConfig.value.radius}px`,
}))

/** Logo 外层圆形容器样式 */
const logoWrapStyle = computed(() => ({
  width: `${sizeConfig.value.logo}px`,
  height: `${sizeConfig.value.logo}px`,
  borderRadius: `${sizeConfig.value.radius}px`,
  background: `${teamColor.value.primary}15`,
}))

/** Logo 图片样式 */
const fallbackStyle = computed(() => ({
  fontSize: `${sizeConfig.value.fontSize}px`,
  color: teamColor.value.primary,
}))

/** 底部队色条样式 */
const colorBarStyle = computed(() => ({
  background: `linear-gradient(90deg, ${teamColor.value.primary}, ${teamColor.value.secondary})`,
  height: `${sizeConfig.value.barHeight}px`,
}))

function handleClick() {
  if (props.clickable) {
    emit('click', props.teamName)
  }
}
</script>

<style scoped>
.team-badge {
  display: inline-flex;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
  position: relative;
  overflow: hidden;
}

.team-badge--clickable {
  cursor: pointer;
}

.team-badge--clickable:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.team-badge__logo-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.team-badge__logo {
  width: 75%;
  height: 75%;
  object-fit: contain;
}

.team-badge__logo-fallback {
  font-family: var(--font-heading);
  font-weight: 800;
  letter-spacing: 0.5px;
}

.team-badge__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.team-badge__name {
  font-family: var(--font-heading);
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-badge__subtitle {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.2;
}

.team-badge__color-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
}

/* --- Size variants --- */
.team-badge--sm .team-badge__name { font-size: 12px; }
.team-badge--md .team-badge__name { font-size: 13px; }
.team-badge--lg .team-badge__name { font-size: 15px; }

.team-badge--lg {
  padding: 10px 16px;
}
</style>
