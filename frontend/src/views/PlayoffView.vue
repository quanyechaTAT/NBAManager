<template>
  <div class="playoff-page">
    <!-- 标题 -->
    <div class="playoff-title-bar">
      <h1 class="playoff-title">NBA 季后赛对阵图</h1>
      <span class="playoff-season">{{ currentSeason }} 赛季</span>
      <button class="sync-btn" @click="syncData" :disabled="syncing">
        {{ syncing ? '同步中...' : '同步数据' }}
      </button>
    </div>

    <!-- 加载/错误/空 -->
    <div v-if="loading" class="state-msg">加载中...</div>
    <div v-else-if="loadError" class="state-msg">
      加载失败 <button class="retry-btn" @click="load">重试</button>
    </div>
    <div v-else-if="!bracket" class="state-msg">
      暂无季后赛数据 <button class="retry-btn" @click="syncData">同步</button>
    </div>

    <!-- 对阵图 -->
    <div v-else class="bracket-wrapper">
      <!-- 桌面端：轴对称布局 -->
      <div class="bracket-desktop">
        <!-- 西部联盟（从上往下收敛） -->
        <div class="conf-half west-half">
          <h2 class="conf-title">西部联盟</h2>
          <div class="conf-bracket">
            <div class="bracket-round">
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
              <span class="round-tag">首轮</span>
            </div>
            <div class="bracket-connector"><div class="conn-vert"></div></div>
            <div class="bracket-round">
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
              <span class="round-tag">半决赛</span>
            </div>
            <div class="bracket-connector"><div class="conn-vert"></div></div>
            <div class="bracket-round">
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
            <div v-if="f.winner && f.winner !== '待定'" class="fb-trophy">🏆 {{ f.winner }}</div>
          </div>
        </div>

        <!-- 东部联盟（从下往上收敛） -->
        <div class="conf-half east-half">
          <div class="conf-bracket">
            <div class="bracket-round">
              <span class="round-tag">分区决赛</span>
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
            </div>
            <div class="bracket-connector"><div class="conn-vert"></div></div>
            <div class="bracket-round">
              <span class="round-tag">半决赛</span>
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
            </div>
            <div class="bracket-connector"><div class="conn-vert"></div></div>
            <div class="bracket-round">
              <span class="round-tag">首轮</span>
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
            </div>
          </div>
          <h2 class="conf-title">东部联盟</h2>
        </div>
      </div>

      <!-- 移动端 -->
      <div class="bracket-mobile">
        <div class="mob-conf">
          <h2 class="conf-title">西部联盟</h2>
          <div class="mob-rounds">
            <div v-for="r in [1,2,3]" :key="'mw'+r" class="mob-round">
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
            <div v-if="f.winner && f.winner !== '待定'" class="fb-trophy">🏆 {{ f.winner }}</div>
          </div>
        </div>
        <div class="mob-conf">
          <h2 class="conf-title">东部联盟</h2>
          <div class="mob-rounds">
            <div v-for="r in [3,2,1]" :key="'me'+r" class="mob-round">
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getTeamLogo } from '@/utils/teamLogos'
import type { PlayoffBracket, Matchup, RoundData } from '@/types/playoff'

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

async function load() {
  loading.value = true; loadError.value = false
  try {
    const { data } = await request.get('/nba/playoff', { params: { season: currentSeason } })
    bracket.value = data.eastern?.length > 0 || data.western?.length > 0 || data.finals ? data : null
  } catch { bracket.value = null; loadError.value = true }
  finally { loading.value = false }
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
.playoff-page { max-width: 1200px; margin: 0 auto; padding: 24px 16px 80px; animation: fadeIn 0.3s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

/* ===== 标题栏 ===== */
.playoff-title-bar { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 28px; flex-wrap: wrap; }
.playoff-title { margin: 0; font-size: 22px; font-weight: 800; color: var(--text-primary); font-family: var(--font-heading); }
.playoff-season { font-size: 13px; color: var(--text-muted); font-weight: 600; }
.sync-btn { padding: 6px 14px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: 6px; color: var(--text-secondary); font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; }
.sync-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.sync-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 状态 ===== */
.state-msg { text-align: center; padding: 60px 0; color: var(--text-muted); font-size: 14px; }
.retry-btn { margin-left: 8px; padding: 4px 12px; background: var(--accent); border: none; border-radius: 4px; color: #fff; font-size: 12px; cursor: pointer; }

/* ===== 桌面端：轴对称布局 ===== */
.bracket-desktop { display: flex; flex-direction: column; align-items: center; gap: 0; }
.bracket-mobile { display: none; }

.conf-half { width: 100%; display: flex; flex-direction: column; align-items: center; }
.conf-title { text-align: center; margin: 12px 0; font-size: 14px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1.5px; }
.conf-bracket { display: flex; flex-direction: column; align-items: center; gap: 0; }

/* 轮次 */
.bracket-round { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.round-tag { font-size: 11px; font-weight: 700; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.5px; padding: 3px 10px; background: var(--bg-hover); border-radius: 4px; }
.round-series { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; }

/* 连接线 */
.bracket-connector { display: flex; justify-content: center; height: 24px; }
.conn-vert { width: 2px; height: 100%; background: var(--border-light); }

/* ===== 系列赛卡片（胶囊） ===== */
.series-card {
  display: flex; align-items: center; gap: 0;
  width: 220px; height: 64px;
  background: var(--bg-card); border: 1px solid var(--border-light);
  border-radius: 32px; padding: 0 10px;
  transition: all 0.15s; flex-shrink: 0;
}
.series-card:hover { border-color: var(--border-medium); box-shadow: var(--shadow-sm); }

.sc-team { display: flex; align-items: center; gap: 5px; flex: 1; min-width: 0; }
.sc-team.win .sc-name { color: var(--accent); font-weight: 700; }
.sc-logo { width: 32px; height: 32px; border-radius: 50%; border: 1px solid var(--border-light); display: flex; align-items: center; justify-content: center; overflow: hidden; background: var(--bg-hover); flex-shrink: 0; }
.sc-logo img { width: 24px; height: 24px; object-fit: contain; }
.sc-fb { font-size: 12px; font-weight: 700; color: var(--text-muted); }
.sc-name { font-size: 11px; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 48px; }
.sc-score { display: flex; align-items: center; gap: 3px; padding: 0 6px; font-size: 18px; font-weight: 800; color: var(--text-secondary); font-variant-numeric: tabular-nums; flex-shrink: 0; }
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
  .series-card { width: 180px; height: 52px; border-radius: 26px; padding: 0 8px; }
  .sc-logo { width: 26px; height: 26px; }
  .sc-logo img { width: 20px; height: 20px; }
  .sc-name { font-size: 10px; max-width: 36px; }
  .sc-score { font-size: 15px; }
  .mob-series { gap: 8px; }
  .fb-inner { padding: 10px 14px; min-height: 60px; border-radius: 30px; gap: 10px; }
  .fb-logo { width: 32px; height: 32px; }
  .fb-logo img { width: 26px; height: 26px; }
  .fb-name { font-size: 11px; }
  .fb-score { font-size: 20px; }
}
</style>
