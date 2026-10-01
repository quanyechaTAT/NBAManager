<template>
  <div class="playoff-page">
    <!-- 标题 -->
    <SportsHeader
      eyebrow="NBA PLAYOFFS · ROAD TO THE LARRY O'BRIEN TROPHY"
      title="NBA 季后赛对阵图"
      :subtitle="`${currentSeason} 赛季东西部十六强巅峰争夺，决战奥布莱恩金杯与季后赛晋级树`"
    >
      <template #actions>
        <button class="sync-btn" @click="syncData" :disabled="syncing">
          {{ syncing ? '数据同步中...' : '同步季后赛数据' }}
        </button>
      </template>
    </SportsHeader>

    <!-- 加载/错误/空 -->
    <div v-if="loading" class="state-card">
      <span class="state-spinner" aria-hidden="true"></span>
      <p class="state-text">正在加载季后赛对阵…</p>
    </div>
    <div v-else-if="loadError" class="state-card">
      <span class="state-icon state-icon--error" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      </span>
      <h3 class="state-heading">加载失败</h3>
      <p class="state-text">暂时无法获取季后赛数据，请检查网络后重试</p>
      <button class="state-btn" @click="load">重新加载</button>
    </div>
    <div v-else-if="!bracket" class="state-card">
      <span class="state-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
      </span>
      <h3 class="state-heading">暂无季后赛数据</h3>
      <p class="state-text">{{ currentSeason }} 赛季的对阵图尚未同步，同步后即可查看完整晋级路径</p>
      <button class="state-btn" @click="syncData" :disabled="syncing">{{ syncing ? '同步中…' : '立即同步' }}</button>
    </div>

    <!-- 对阵图 -->
    <div v-else ref="bracketWrapper" class="bracket-wrapper">
      <!-- 桌面端：轴对称布局 -->
      <div class="bracket-desktop">
        <!-- 东部联盟（从上往下收敛） -->
        <div class="conf-half east-half">
          <h2 class="conf-title">东部联盟</h2>
          <div class="conf-bracket">
            <div class="bracket-round round-first">
              <div class="round-series">
                <div v-for="m in eastRounds[1]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
              <span class="round-tag">首轮</span>
            </div>
            <div class="bracket-connector connector-r1-r2" aria-hidden="true">
              <svg viewBox="0 0 56 588" preserveAspectRatio="none">
                <path d="M0 73.5 H28 V147 H56 M0 220.5 H28 V147 M0 367.5 H28 V441 H56 M0 514.5 H28 V441" />
              </svg>
            </div>
            <div class="bracket-round round-semis">
              <div class="round-series">
                <div v-for="m in eastRounds[2]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
              <span class="round-tag">半决赛</span>
            </div>
            <div class="bracket-connector connector-r2-r3" aria-hidden="true">
              <svg viewBox="0 0 56 588" preserveAspectRatio="none">
                <path d="M0 147 H28 V294 H56 M0 441 H28 V294" />
              </svg>
            </div>
            <div class="bracket-round round-conf-final">
              <div class="round-series">
                <div v-for="m in eastRounds[3]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
              <span class="round-tag">分区决赛</span>
            </div>
          </div>
        </div>

        <!-- 总决赛横幅（中心轴） -->
        <div class="finals-banner">
          <div class="fb-inner">
            <div class="fb-side fb-left">
              <div class="fb-logo"><img v-if="getLogo(f.t1)" :src="getLogo(f.t1)" alt="" /><span v-else class="sc-fb">{{ f.t1?.charAt(0) }}</span></div>
              <span class="fb-name">{{ f.t1 }}</span>
            </div>
            <div class="fb-center">
              <span class="fb-label">NBA FINALS</span>
              <span class="fb-score">
                <span :class="{ win: f.winner === f.t1 }">{{ f.w1 ?? '-' }}</span>
                <span class="fb-sep">-</span>
                <span :class="{ win: f.winner === f.t2 }">{{ f.w2 ?? '-' }}</span>
              </span>
            </div>
            <div class="fb-side fb-right">
              <span class="fb-name">{{ f.t2 }}</span>
              <div class="fb-logo"><img v-if="getLogo(f.t2)" :src="getLogo(f.t2)" alt="" /><span v-else class="sc-fb">{{ f.t2?.charAt(0) }}</span></div>
            </div>
            <div v-if="f.winner && f.winner !== '待定'" class="fb-trophy"><span style="display:inline-flex;margin-right:4px;"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg></span>{{ f.winner }}</div>
          </div>
        </div>

        <!-- 西部联盟（从下往上收敛） -->
        <div class="conf-half west-half">
          <div class="conf-bracket">
            <div class="bracket-round round-conf-final">
              <span class="round-tag">分区决赛</span>
              <div class="round-series">
                <div v-for="m in westRounds[3]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="bracket-connector connector-r2-r3" aria-hidden="true">
              <svg viewBox="0 0 56 588" preserveAspectRatio="none">
                <path d="M0 294 H28 V147 H56 M28 441 V294 H56" />
              </svg>
            </div>
            <div class="bracket-round round-semis">
              <span class="round-tag">半决赛</span>
              <div class="round-series">
                <div v-for="m in westRounds[2]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="bracket-connector connector-r1-r2" aria-hidden="true">
              <svg viewBox="0 0 56 588" preserveAspectRatio="none">
                <path d="M0 147 H28 V73.5 H56 M0 147 H28 V220.5 H56 M0 441 H28 V367.5 H56 M0 441 H28 V514.5 H56" />
              </svg>
            </div>
            <div class="bracket-round round-first">
              <span class="round-tag">首轮</span>
              <div class="round-series">
                <div v-for="m in westRounds[1]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <span class="sc-name">{{ m.team2Name }}</span>
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <h2 class="conf-title">西部联盟</h2>
        </div>
      </div>

      <!-- 移动端 -->
      <div class="bracket-mobile">
        <div class="mob-conf">
          <h2 class="conf-title">东部联盟</h2>
          <div class="mob-rounds">
            <div v-for="r in mobileEastRounds" :key="'me'+r" class="mob-round">
              <span class="round-tag">{{ rLabel(r) }}</span>
              <div class="mob-series">
                <div v-for="m in eastRounds[r]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team2Name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="finals-banner">
          <div class="fb-inner">
            <div class="fb-side fb-left">
              <div class="fb-logo"><img v-if="getLogo(f.t1)" :src="getLogo(f.t1)" alt="" /><span v-else class="sc-fb">{{ f.t1?.charAt(0) }}</span></div>
              <span class="fb-name">{{ f.t1 }}</span>
            </div>
            <div class="fb-center">
              <span class="fb-label">NBA FINALS</span>
              <span class="fb-score">
                <span :class="{ win: f.winner === f.t1 }">{{ f.w1 ?? '-' }}</span>
                <span class="fb-sep">-</span>
                <span :class="{ win: f.winner === f.t2 }">{{ f.w2 ?? '-' }}</span>
              </span>
            </div>
            <div class="fb-side fb-right">
              <span class="fb-name">{{ f.t2 }}</span>
              <div class="fb-logo"><img v-if="getLogo(f.t2)" :src="getLogo(f.t2)" alt="" /><span v-else class="sc-fb">{{ f.t2?.charAt(0) }}</span></div>
            </div>
            <div v-if="f.winner && f.winner !== '待定'" class="fb-trophy"><span style="display:inline-flex;margin-right:4px;"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg></span>{{ f.winner }}</div>
          </div>
        </div>
        <div class="mob-conf">
          <h2 class="conf-title">西部联盟</h2>
          <div class="mob-rounds">
            <div v-for="r in mobileWestRounds" :key="'mw'+r" class="mob-round">
              <span class="round-tag">{{ rLabel(r) }}</span>
              <div class="mob-series">
                <div v-for="m in westRounds[r]" :key="m.id" class="series-card">
                  <div class="sc-team" :class="{ win: m.winnerName === m.team1Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team1Name)" :src="getLogo(m.team1Name)" alt="" /><span v-else class="sc-fb">{{ m.team1Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team1Name }}</span>
                  </div>
                  <div class="sc-score">
                    <span :class="{ win: m.winnerName === m.team1Name }">{{ m.team1Wins ?? '-' }}</span>
                    <span class="sc-sep">-</span>
                    <span :class="{ win: m.winnerName === m.team2Name }">{{ m.team2Wins ?? '-' }}</span>
                  </div>
                  <div class="sc-team" :class="{ win: m.winnerName === m.team2Name }">
                    <div class="sc-logo"><img v-if="getLogo(m.team2Name)" :src="getLogo(m.team2Name)" alt="" /><span v-else class="sc-fb">{{ m.team2Name?.charAt(0) }}</span></div>
                    <span class="sc-name">{{ m.team2Name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getTeamLogo } from '@/utils/teamLogos'
import type { PlayoffBracket, Matchup, RoundData } from '@/types/playoff'
import SportsHeader from '@/components/common/SportsHeader.vue'

const currentSeason = (() => {
  const d = new Date()
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  return m >= 10 ? `${y}-${(y + 1).toString().slice(2)}` : `${y - 1}-${y.toString().slice(2)}`
})()

const loading = ref(false)
const syncing = ref(false)
const bracket = ref<PlayoffBracket | null>(null)
const loadError = ref(false)
const bracketWrapper = ref<HTMLElement | null>(null)
const mobileEastRounds = [1, 2, 3] as const
const mobileWestRounds = [3, 2, 1] as const

const getLogo = (name: string) => getTeamLogo(name) || undefined

function emptyMatch(id: string): Matchup {
  return { id, round: 1, team1Name: '待定', team2Name: '待定', winnerName: '' }
}
function fillRound(items: Matchup[], count: number, prefix: string): Matchup[] {
  return Array.from({ length: count }, (_, i) => items[i] || emptyMatch(`${prefix}-${i}`))
}
function rounds(side: 'eastern' | 'western'): RoundData {
  const src = bracket.value?.[side] || []
  return {
    1: fillRound(src.filter((m: Matchup) => m.round === 1), 4, `${side}-r1`),
    2: fillRound(src.filter((m: Matchup) => m.round === 2), 2, `${side}-r2`),
    3: fillRound(src.filter((m: Matchup) => m.round === 3), 1, `${side}-r3`)
  }
}

const eastRounds = computed(() => rounds('eastern'))
const westRounds = computed(() => rounds('western'))
const f = computed(() => {
  const raw = bracket.value?.finals
  if (!raw) return { t1: '待定', t2: '待定', w1: 0, w2: 0, winner: '' }
  return {
    t1: raw.team1Name || raw.t1 || '待定',
    t2: raw.team2Name || raw.t2 || '待定',
    w1: raw.team1Wins ?? raw.w1 ?? 0,
    w2: raw.team2Wins ?? raw.w2 ?? 0,
    winner: raw.winnerName || raw.winner || ''
  }
})
function rLabel(r: number) { return r === 1 ? '首轮' : r === 2 ? '半决赛' : '分区决赛' }

async function centerBracket() {
  await nextTick()
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  })
  if (bracketWrapper.value) {
    bracketWrapper.value.scrollLeft = Math.max(
      0,
      (bracketWrapper.value.scrollWidth - bracketWrapper.value.clientWidth) / 2
    )
  }
}

async function load() {
  loading.value = true; loadError.value = false
  try {
    const { data } = await request.get('/nba/playoff', { params: { season: currentSeason } })
    bracket.value = data.eastern?.length > 0 || data.western?.length > 0 || data.finals ? data : null
  } catch { bracket.value = null; loadError.value = true }
  finally {
    loading.value = false
    if (bracket.value) await centerBracket()
  }
}
async function syncData() {
  syncing.value = true
  try { await request.post('/nba/playoff/sync', null, { params: { season: currentSeason } }); ElMessage.success('同步成功'); await load() }
  catch { ElMessage.error('同步失败') }
  finally { syncing.value = false }
}
onMounted(load)
</script>

<style scoped>
/* ===== 页面 ===== */
.playoff-page { max-width: 1680px; margin: 0 auto; padding: 24px 16px 80px; animation: courtside-rise 0.45s var(--ease-out-expo, ease) both; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

/* ===== 标题栏 ===== */
.playoff-title-bar { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 28px; flex-wrap: wrap; }
.playoff-title { margin: 0; font-size: 28px; font-weight: 800; color: var(--text-primary); font-family: var(--font-heading); letter-spacing: -0.4px; }
.playoff-season { font-size: 13px; color: var(--text-muted); font-weight: 600; }
.sync-btn { padding: 6px 14px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: 6px; color: var(--text-secondary); font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; }
.sync-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.sync-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 状态 ===== */
.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  max-width: 420px;
  margin: 48px auto 64px;
  padding: 44px 36px 40px;
  text-align: center;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}
.state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 10px;
  border-radius: 50%;
  background: var(--accent-lighter);
  color: var(--accent);
}
.state-icon--error {
  background: var(--danger-glow);
  color: var(--danger);
}
.state-spinner {
  width: 34px;
  height: 34px;
  margin-bottom: 12px;
  border: 3px solid var(--border-light);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: stateSpin 0.9s linear infinite;
}
@keyframes stateSpin { to { transform: rotate(360deg); } }
.state-heading {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}
.state-text { margin: 0; font-size: 13.5px; line-height: 1.6; color: var(--text-muted); }
.state-btn {
  margin-top: 16px;
  padding: 9px 26px;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-md);
  color: var(--accent-contrast, #04202A);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background var(--duration-nav, 0.18s) var(--ease-smooth);
}
.state-btn:hover:not(:disabled) { background: var(--accent-light); }
.state-btn:disabled { opacity: 0.55; cursor: not-allowed; }

/* ===== 桌面端：横向轴对称布局 ===== */
.bracket-wrapper { width: 100%; overflow-x: auto; padding: 18px 0 22px; overscroll-behavior-inline: contain; scrollbar-color: var(--border-medium) transparent; scrollbar-gutter: stable; }
.bracket-desktop { display: grid; width: 1980px; min-width: 1980px; margin: 0 auto; grid-template-columns: minmax(0, 1fr) 340px minmax(0, 1fr); align-items: center; gap: 28px; }
.bracket-mobile { display: none; }

.conf-half { width: 100%; min-width: 0; display: flex; flex-direction: column; align-items: center; }
.conf-title { text-align: center; margin: 16px 0 22px; font-size: 15px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1.5px; }
.west-half .conf-title { order: -1; }
.conf-bracket { display: flex; width: 100%; height: 588px; flex-direction: row; align-items: stretch; gap: 0; }
.east-half .conf-bracket { flex-direction: row; }
.west-half .conf-bracket { flex-direction: row; }

/* 轮次 */
.bracket-round { display: flex; min-width: 0; flex: 1; flex-direction: column; align-items: center; }
.round-tag { font-size: 11px; font-weight: 700; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.5px; padding: 3px 10px; background: var(--bg-hover); border-radius: 4px; }
.round-series { display: grid; width: 100%; height: 100%; grid-template-rows: repeat(8, minmax(0, 1fr)); align-items: center; justify-items: center; }
.bracket-round .round-tag { position: absolute; z-index: 1; }
.bracket-round { position: relative; }
.round-first .round-tag { top: calc(12.5% - 84px); }
.round-semis .round-tag { top: calc(25% - 84px); }
.round-conf-final .round-tag { top: calc(50% - 84px); }
.round-first .series-card:nth-child(1) { grid-row: 1 / 3; }
.round-first .series-card:nth-child(2) { grid-row: 3 / 5; }
.round-first .series-card:nth-child(3) { grid-row: 5 / 7; }
.round-first .series-card:nth-child(4) { grid-row: 7 / 9; }
.round-semis .series-card:nth-child(1) { grid-row: 2 / 4; }
.round-semis .series-card:nth-child(2) { grid-row: 6 / 8; }
.round-conf-final .series-card { grid-row: 4 / 6; }

/* 连接线 */
.bracket-connector { width: 56px; height: 588px; flex-shrink: 0; }
.bracket-connector svg { display: block; width: 100%; height: 100%; overflow: visible; }
.bracket-connector path { fill: none; stroke: var(--border-medium); stroke-width: 1.5; vector-effect: non-scaling-stroke; }

/* ===== 系列赛卡片（胶囊） ===== */
.series-card {
  display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: 1fr auto 1fr; align-items: stretch;
  width: 214px; height: 112px;
  background: var(--bg-card); border: 1px solid var(--border-light);
  border-radius: 14px; padding: 10px 14px;
  transition: all 0.15s; flex-shrink: 0;
}
.series-card:hover { border-color: var(--border-medium); box-shadow: var(--shadow-sm); }

.sc-team { position: relative; display: flex; box-sizing: border-box; width: 100%; min-width: 0; align-items: center; justify-content: center; padding: 0 34px; text-align: center; }
.series-card > .sc-team:first-child { grid-column: 1; grid-row: 1; }
.series-card > .sc-team:last-child { grid-column: 1; grid-row: 3; }
.series-card > .sc-team:first-child .sc-logo { position: absolute; left: 0; }
.series-card > .sc-team:last-child .sc-logo { position: absolute; right: 0; }
.sc-team.win .sc-name { color: var(--accent); font-weight: 700; }
.sc-logo { width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--border-light); display: flex; align-items: center; justify-content: center; overflow: hidden; background: var(--bg-hover); flex-shrink: 0; }
.sc-logo img { width: 21px; height: 21px; object-fit: contain; }
.sc-fb { font-size: 12px; font-weight: 700; color: var(--text-muted); }
.sc-name { width: 100%; min-width: 0; font-size: 12px; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center; }
.sc-score { display: flex; grid-column: 1; grid-row: 2; align-items: center; justify-content: center; gap: 7px; padding: 4px 0; border-top: 1px solid var(--border-light); border-bottom: 1px solid var(--border-light); font-size: 20px; font-weight: 800; color: var(--text-secondary); font-variant-numeric: tabular-nums; flex-shrink: 0; }
.sc-sep { font-size: 12px; color: var(--text-dim); }
.sc-score .win { color: var(--accent); }

/* ===== 总决赛横幅 ===== */
.finals-banner { width: 100%; max-width: 600px; margin: 16px auto; }
.fb-inner { display: flex; align-items: center; justify-content: center; gap: 14px; padding: 14px 20px; background: linear-gradient(135deg, #0a2463, #1e3a8a); border-radius: 40px; position: relative; min-height: 76px; }
.fb-side { display: flex; align-items: center; gap: 8px; }
.fb-left { justify-content: flex-start; }
.fb-right { justify-content: flex-end; }
.fb-logo { width: 40px; height: 40px; border-radius: 50%; border: 2px solid rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; overflow: hidden; background: rgba(255,255,255,0.1); flex-shrink: 0; }
.fb-logo img { width: 32px; height: 32px; object-fit: contain; }
.fb-name { font-size: 13px; font-weight: 700; color: #fff; white-space: nowrap; }
.fb-center { display: flex; flex-direction: column; align-items: center; gap: 2px; flex-shrink: 0; }
.fb-label { font-size: 9px; font-weight: 800; color: rgba(255,255,255,0.55); letter-spacing: 2px; text-transform: uppercase; }
.fb-score { display: flex; align-items: center; gap: 5px; font-size: 24px; font-weight: 800; color: #fff; font-variant-numeric: tabular-nums; }
.fb-sep { font-size: 14px; color: rgba(255,255,255,0.4); }
.fb-score .win { color: #fbbf24; }
.fb-trophy { position: absolute; right: -6px; top: -6px; padding: 3px 8px; background: #fbbf24; border-radius: 10px; font-size: 10px; font-weight: 700; color: #0a2463; }

.bracket-desktop .finals-banner { position: relative; width: 280px; margin: 0 auto; }
.bracket-desktop .finals-banner::before,
.bracket-desktop .finals-banner::after {
  position: absolute;
  top: 50%;
  width: 58px;
  border-top: 1.5px solid var(--border-medium);
  content: '';
}
.bracket-desktop .finals-banner::before { right: 100%; }
.bracket-desktop .finals-banner::after { left: 100%; }
.bracket-desktop .fb-inner {
  display: grid;
  min-height: 250px;
  grid-template-rows: 1fr auto 1fr;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  padding: 24px 22px;
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: 18px;
  box-shadow: var(--shadow-sm);
}
.bracket-desktop .fb-side { position: relative; box-sizing: border-box; width: 100%; min-height: 42px; justify-content: center; padding: 0 52px; }
.bracket-desktop .fb-left { grid-row: 1; }
.bracket-desktop .fb-center { grid-row: 2; width: 100%; padding: 12px 0; border-top: 1px solid var(--border-light); border-bottom: 1px solid var(--border-light); }
.bracket-desktop .fb-right { grid-row: 3; }
.bracket-desktop .fb-left .fb-logo { position: absolute; left: 0; }
.bracket-desktop .fb-right .fb-logo { position: absolute; right: 0; }
.bracket-desktop .fb-logo { width: 42px; height: 42px; background: var(--bg-hover); border-color: var(--border-light); }
.bracket-desktop .fb-name { width: 100%; overflow: hidden; color: var(--text-primary); font-size: 14px; text-align: center; text-overflow: ellipsis; }
.bracket-desktop .fb-label { color: var(--text-muted); }
.bracket-desktop .fb-score { color: var(--text-primary); font-size: 30px; }
.bracket-desktop .fb-sep { color: var(--text-dim); }
.bracket-desktop .fb-score .win { color: var(--accent); }
.bracket-desktop .fb-trophy { right: 12px; top: 12px; max-width: 180px; overflow: hidden; background: var(--accent); color: var(--text-on-accent); text-overflow: ellipsis; white-space: nowrap; }

/* ===== 移动端 ===== */
.mob-conf { margin-bottom: 20px; }
.mob-rounds { display: flex; flex-direction: column; align-items: center; gap: 0; }
.mob-round { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.mob-series { display: flex; justify-content: center; gap: 12px; flex-wrap: wrap; }

@media (max-width: 768px) {
  .bracket-desktop { display: none; }
  .bracket-mobile { display: block; }
  .playoff-page { padding: 16px 8px 80px; }
  .playoff-title { font-size: 18px; }
  .series-card { width: 220px; height: 108px; border-radius: 14px; padding: 9px 12px; }
  .sc-logo { width: 26px; height: 26px; }
  .sc-logo img { width: 20px; height: 20px; }
  .sc-name { font-size: 11px; max-width: 126px; }
  .sc-score { font-size: 19px; }
  .mob-series { gap: 8px; }
  .fb-inner { padding: 10px 14px; min-height: 60px; border-radius: 30px; gap: 10px; }
  .fb-logo { width: 32px; height: 32px; }
  .fb-logo img { width: 26px; height: 26px; }
  .fb-name { font-size: 11px; }
  .fb-score { font-size: 20px; }
}
</style>
