<template>
  <div
    v-if="matches.length > 0"
    class="live-ticker"
    :class="{ 'live-ticker--paused': paused }"
    @mouseenter="paused = true"
    @mouseleave="paused = false"
  >
    <div class="live-ticker__track">
      <!-- 实际内容：两组数据实现无缝衔接 -->
      <div class="live-ticker__content">
        <template v-for="(group, gi) in [matches, matches]" :key="gi">
          <div
            v-for="match in group"
            :key="`${gi}-${match.id}`"
            class="live-ticker__item"
          >
            <!-- 主队 -->
            <div class="live-ticker__team">
              <img
                v-if="getTeamLogo(match.homeTeam)"
                :src="getTeamLogo(match.homeTeam)"
                :alt="match.homeTeam"
                class="live-ticker__logo"
              />
              <span class="live-ticker__team-name">{{ getShortName(match.homeTeam) }}</span>
              <span
                v-if="match.status === 'FINISHED' || match.status === 'LIVE'"
                class="live-ticker__score"
                :class="{ 'live-ticker__score--winner': match.homeScore > match.awayScore }"
              >
                {{ match.homeScore }}
              </span>
            </div>

            <!-- 分隔 / 状态 -->
            <div class="live-ticker__vs" :class="`live-ticker__vs--${match.status.toLowerCase()}`">
              <span v-if="match.status === 'LIVE'" class="live-ticker__live-dot" />
              <span>{{ getVsText(match) }}</span>
            </div>

            <!-- 客队 -->
            <div class="live-ticker__team">
              <img
                v-if="getTeamLogo(match.awayTeam)"
                :src="getTeamLogo(match.awayTeam)"
                :alt="match.awayTeam"
                class="live-ticker__logo"
              />
              <span class="live-ticker__team-name">{{ getShortName(match.awayTeam) }}</span>
              <span
                v-if="match.status === 'FINISHED' || match.status === 'LIVE'"
                class="live-ticker__score"
                :class="{ 'live-ticker__score--winner': match.awayScore > match.homeScore }"
              >
                {{ match.awayScore }}
              </span>
            </div>

            <!-- 分隔线 -->
            <div class="live-ticker__divider" />
          </div>
        </template>
      </div>
    </div>

    <!-- 左侧渐变遮罩 -->
    <div class="live-ticker__gradient live-ticker__gradient--left" />
    <!-- 右侧渐变遮罩 -->
    <div class="live-ticker__gradient live-ticker__gradient--right" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { LiveMatch } from '@/api/types'
import { getTeamLogo } from '@/utils/teamLogos'

interface Props {
  /** 比赛数据列表 */
  matches: LiveMatch[]
}

defineProps<Props>()

/** 鼠标悬停暂停滚动 */
const paused = ref(false)

/**
 * 获取简短队名（最后一个词）
 * @example "Los Angeles Lakers" → "Lakers"
 */
function getShortName(teamName: string): string {
  if (!teamName) return '???'
  const parts = teamName.split(' ')
  return parts[parts.length - 1]
}

/**
 * 获取 VS 文本
 * @returns 如 "vs"、"112-108"、"LIVE"
 */
function getVsText(match: LiveMatch): string {
  if (match.status === 'LIVE') {
    return match.timeStatus || 'LIVE'
  }
  if (match.status === 'FINISHED') {
    return 'FINAL'
  }
  return 'vs'
}
</script>

<style scoped>
.live-ticker {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 10px 0;
}

.live-ticker__track {
  width: 100%;
  overflow: hidden;
}

.live-ticker__content {
  display: flex;
  align-items: center;
  white-space: nowrap;
  animation: tickerScroll 30s linear infinite;
}

.live-ticker--paused .live-ticker__content {
  animation-play-state: paused;
}

@keyframes tickerScroll {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-50%);
  }
}

.live-ticker__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  flex-shrink: 0;
}

.live-ticker__team {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.live-ticker__logo {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.live-ticker__team-name {
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.3px;
}

.live-ticker__score {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 800;
  color: var(--text-primary);
  margin-left: 2px;
}

.live-ticker__score--winner {
  color: var(--accent);
}

.live-ticker__vs {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.live-ticker__vs--live {
  color: var(--danger);
}

.live-ticker__vs--finished {
  color: var(--text-muted);
}

.live-ticker__vs--scheduled {
  color: var(--cyan);
}

/* LIVE 红色闪烁点 */
.live-ticker__live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--danger);
  animation: liveBlink 1.2s ease-in-out infinite;
}

@keyframes liveBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.live-ticker__divider {
  width: 1px;
  height: 20px;
  background: var(--border-light);
  margin-left: 16px;
  flex-shrink: 0;
}

/* 渐变遮罩 */
.live-ticker__gradient {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 60px;
  pointer-events: none;
  z-index: 2;
}

.live-ticker__gradient--left {
  left: 0;
  background: linear-gradient(90deg, var(--bg-card) 0%, transparent 100%);
}

.live-ticker__gradient--right {
  right: 0;
  background: linear-gradient(-90deg, var(--bg-card) 0%, transparent 100%);
}

/* 移动端 */
@media (max-width: 768px) {
  .live-ticker__content {
    animation-duration: 25s;
  }

  .live-ticker__item {
    gap: 6px;
    padding: 0 12px;
  }

  .live-ticker__team-name {
    font-size: 11px;
  }
}
</style>
