<template>
  <div class="copilot-workspace" :class="{ 'has-conversation': msgs.length > 0 }">
    <aside id="copilot-history" class="sidebar" :class="{ open: sidebarOpen }" :inert="!sidebarOpen" :aria-hidden="!sidebarOpen" aria-label="历史对话">
      <div class="sidebar-brand">
        <span class="sidebar-brand-kicker">COURTSIDE / AI</span>
        <strong class="sidebar-brand-heading">AI 战术智囊</strong>
        <span class="sidebar-brand-subtitle">高阶数据 · 战术复盘 · 赛事推演</span>
        <button class="sidebar-close" type="button" aria-label="关闭历史对话" @click="sidebarOpen = false">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18" aria-hidden="true">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>
      <button class="sidebar-new" type="button" @click="newChat">
        <svg viewBox="0 0 24 24" fill="none" width="19" height="19" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        新对话
      </button>
      <div class="sidebar-section-title">
        <svg viewBox="0 0 24 24" fill="none" width="17" height="17" aria-hidden="true"><path d="M4 5h16M4 11h11M4 17h8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="m17 16 2 2 3-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        历史对话
      </div>
      <div class="sidebar-body">
        <div v-if="!convs.length" class="sidebar-empty">
          <svg viewBox="0 0 48 48" fill="none" width="43" height="43" aria-hidden="true"><path d="M8 10h32v22H22l-8 7v-7H8V10Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M15 18h18M15 24h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          <strong>暂无对话</strong>
          <span>开始新对话，探索 NBA 的更多可能。</span>
        </div>
        <section v-for="group in groupedConvs" :key="group.label" class="sidebar-group">
          <h3>{{ group.label }}</h3>
          <div v-for="c in group.items" :key="c.id" class="sidebar-item" :class="{ active: c.id === sid }">
            <button class="sidebar-item-main" type="button" :aria-current="c.id === sid ? 'page' : undefined" @click="loadConv(c)">
              <span class="sidebar-item-title">{{ c.title }}</span>
              <span class="sidebar-item-time">{{ fmtTime(c.time) }}</span>
            </button>
            <button class="sidebar-item-del" type="button" :aria-label="`删除对话：${c.title}`" @click="delConv(c.id)">
              <svg viewBox="0 0 24 24" fill="none" width="14" height="14" aria-hidden="true"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
            </button>
          </div>
        </section>
      </div>
      <div class="sidebar-foot" aria-hidden="true">BASKETBALL DRIVES<br />BETTER QUESTIONS<span></span></div>
    </aside>
    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

    <div v-if="showShareModal" class="modal-mask" @click="showShareModal = false">
      <div class="modal-content" @click.stop>
        <h3>分享对话</h3>
        <p>复制链接后，可把这次分析结果分享给其他人查看。</p>
        <div class="modal-share-row">
          <input :value="shareUrl" readonly @focus="selectShareInput" />
          <button type="button" @click="copyShareUrl">复制</button>
        </div>
        <button class="modal-close-btn" type="button" @click="showShareModal = false">关闭</button>
      </div>
    </div>

    <div class="workspace-main">
      <div v-if="showLocalScoreStatus" class="score-status" role="status" aria-live="polite">
        <div class="score-status__label"><span class="score-status__dot" :class="{ 'is-live': liveMatches.length > 0 }" />实时赛况</div>
        <div class="score-status__track">
          <span v-if="!liveMatches.length" class="score-status__empty">{{ scoreStatusText }}</span>
          <span v-for="match in liveMatches" :key="match.id" class="score-status__game">
            {{ match.awayTeam }} <strong>{{ match.awayScore ?? '—' }}</strong>
            <span>:</span>
            <strong>{{ match.homeScore ?? '—' }}</strong> {{ match.homeTeam }}
          </span>
        </div>
        <span class="score-status__tagline" aria-hidden="true">BASKETBALL LIVES IN DATA</span>
      </div>
      <header class="top-bar">
        <div class="top-left">
          <button class="icon-btn" type="button" :aria-label="sidebarOpen ? '收起历史对话' : '打开历史对话'" :aria-expanded="sidebarOpen" aria-controls="copilot-history" @click="sidebarOpen = !sidebarOpen">
            <svg viewBox="0 0 24 24" fill="none" width="17" height="17">
              <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
          <span class="top-divider" aria-hidden="true" />
          <span class="top-status">AI 战术智囊</span>
          <span class="top-context" aria-hidden="true">NBA COPILOT</span>
          <button v-if="!sidebarOpen" class="top-btn top-new" type="button" @click="newChat">
            <svg viewBox="0 0 24 24" fill="none" width="15" height="15">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            新对话
          </button>
        </div>
        <div class="top-right">
          <span v-if="!msgs.length" class="top-action-hint">开始提问后可导出或分享</span>
          <button class="top-btn" type="button" aria-label="导出对话" :disabled="!msgs.length" :title="msgs.length ? '导出当前对话' : '开始对话后可导出'" @click="exportChat">
            <svg viewBox="0 0 24 24" fill="none" width="15" height="15">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            导出
          </button>
          <button class="top-btn" type="button" aria-label="分享对话" :disabled="!msgs.length" :title="msgs.length ? '分享当前对话' : '开始对话后可分享'" @click="shareChat">
            <svg viewBox="0 0 24 24" fill="none" width="15" height="15">
              <circle cx="18" cy="5" r="3" stroke="currentColor" stroke-width="2" />
              <circle cx="6" cy="12" r="3" stroke="currentColor" stroke-width="2" />
              <circle cx="18" cy="19" r="3" stroke="currentColor" stroke-width="2" />
              <path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" stroke="currentColor" stroke-width="2" />
            </svg>
            分享
          </button>
        </div>
      </header>

      <div class="content-area" ref="msgsEl" @scroll.passive="handleContentScroll">
        <div v-if="!msgs.length" class="welcome-screen">
          <section class="welcome-hero">
            <div class="hero-copy">
              <div class="hero-badge">COURTSIDE INTELLIGENCE</div>
              <h1 class="hero-title">把比赛读得<span>更深</span></h1>
              <p class="hero-desc">用实时数据与可靠来源，回答每一个篮球问题。</p>
            </div>
            <svg class="hero-court" viewBox="0 0 720 260" fill="none" aria-hidden="true">
              <path d="M24 20h666v220H24zM358 20v220M358 130h332M690 42H546v176h144M690 78h-46v104h46M690 98a32 32 0 0 0 0 64M546 68a86 86 0 0 0 0 124M358 72a58 58 0 1 1 0 116" stroke="currentColor" stroke-width="1.4" />
              <path d="m506 72 25-19m-11 1 12-2-5 12M430 168l31 21m-4-10 5 11-12-1" stroke="currentColor" stroke-width="1.4" stroke-dasharray="6 6" />
            </svg>
            <svg class="hero-ball" viewBox="0 0 220 220" fill="none" aria-hidden="true">
              <circle cx="110" cy="110" r="100" fill="#B34422" stroke="#F28150" stroke-width="2" />
              <circle cx="110" cy="110" r="94" stroke="#D6693B" stroke-width="2" />
              <path d="M110 10v200M10 110h200M34 45c75 24 104 80 75 164M186 45c-75 24-104 80-75 164" stroke="#2A1714" stroke-width="7" />
            </svg>
          </section>

          <section class="capabilities" aria-label="AI 能力入口">
            <button class="cap-main" type="button" @click="send(featuredPrompt)">
              <span class="cap-main-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" width="24" height="24" aria-hidden="true">
                  <rect x="3" y="4" width="18" height="18" rx="2.5" />
                  <path d="M16 2v4M8 2v4M3 10h18" stroke-linecap="round" />
                </svg>
              </span>
              <span class="cap-main-text">
                <span class="cap-main-kicker">FEATURED ANALYSIS</span>
                <span class="cap-main-title">{{ featuredTitle }}</span>
                <span class="cap-main-desc">{{ featuredDescription }}</span>
              </span>
              <span class="cap-main-arrow" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" width="21" height="21"><path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg></span>
            </button>

            <div class="cap-grid">
              <button class="cap-card" type="button" @click="send('对比勒布朗·詹姆斯和凯文·杜兰特本赛季的数据表现与高阶影响力')">
                <span class="cap-icon people">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                    <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
                  </svg>
                </span>
                <span class="cap-copy"><span class="cap-title">球星对决分析</span><span class="cap-description">多维高阶数据对照，洞察双星攻防影响力与关键球表现。</span></span>
                <span class="cap-card-arrow" aria-hidden="true">›</span>
              </button>
              <button class="cap-card" type="button" @click="send('深度剖析波士顿凯尔特人本赛季战术体系与攻防效率净值')">
                <span class="cap-icon globe">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 2a14.5 14.5 0 0 0 0 20M12 2a14.5 14.5 0 0 1 0 20M2 12h20" />
                  </svg>
                </span>
                <span class="cap-copy"><span class="cap-title">球队战术剖析</span><span class="cap-description">深度调取球队攻防效率净值、近期状态走势与轮换深度。</span></span>
                <span class="cap-card-arrow" aria-hidden="true">›</span>
              </button>
              <button class="cap-card" type="button" @click="send('基于当前赛季已有数据，推演季后赛分区对阵胜率与可能战术转折')">
                <span class="cap-icon trophy">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6M18 9h1.5a2.5 2.5 0 0 0 0-5H18M4 22h16" />
                    <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22M18 2H6v7a6 6 0 0 0 12 0V2Z" />
                  </svg>
                </span>
                <span class="cap-copy"><span class="cap-title">季后赛推演</span><span class="cap-description">基于全赛季数据与对阵关系，推演系列赛关键博弈点。</span></span>
                <span class="cap-card-arrow" aria-hidden="true">›</span>
              </button>
              <button class="cap-card" type="button" @click="send('本赛季真实命中率（TS%）与高阶正负值（BPM）最高的领袖球员有哪些？')">
                <span class="cap-icon chart">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M4 19V5M9 19v-8M14 19V8M19 19V3" stroke-linecap="round" />
                  </svg>
                </span>
                <span class="cap-copy"><span class="cap-title">数据王座榜</span><span class="cap-description">全维度洞察得分、篮板、助攻与高阶效率风云榜。</span></span>
                <span class="cap-card-arrow" aria-hidden="true">›</span>
              </button>
            </div>
          </section>

          <div class="prompt-chips" aria-label="推荐提问">
            <span class="prompt-label">战术灵感探索</span>
            <button class="chip" type="button" @click="send('今日赛程有哪些强强对话？')">今日焦点对决</button>
            <button class="chip" type="button" @click="send('分析湖人最近 5 场比赛的攻防效率与轮换转折')">湖人近况战术复盘</button>
            <button class="chip" type="button" @click="send('库里与东契奇关键时刻（Clutch Time）三分效率对比')">库里 vs 东契奇关键球</button>
            <button class="chip" type="button" @click="send('联盟进攻效率 Top 5 球队战术特征')">进攻效率 Top 5 剖析</button>
          </div>
        </div>

        <div v-else class="chat-flow">
          <div v-for="(m, i) in msgs" :key="i" class="msg-row" :class="m.role">
            <div v-if="m.role === 'user'" class="msg-bubble user">{{ m.content }}</div>
            <template v-else>
              <div class="msg-avatar">AI</div>
              <div class="msg-body">
                <div v-if="loading && i === msgs.length - 1 && !m.content && !m.error" class="msg-loading">
                  <div class="loading-dots"><span></span><span></span><span></span></div>
                  <span class="loading-text">{{ statusText }}</span>
                  <span class="loading-elapsed">{{ elapsed }}s</span>
                  <button class="loading-cancel" type="button" @click="cancelQuery">取消</button>
                </div>
                <div v-if="m.content" class="msg-bubble ai" v-html="render(m, i)"></div>
                <div v-if="m.relatedEntities?.length" class="msg-entities">
                  <span class="entities-label">相关实体</span>
                  <div class="entities-list">
                    <RelatedEntityCard v-for="entity in m.relatedEntities" :key="`${entity.type}-${entity.id}`" :entity="entity" />
                  </div>
                </div>
                <div v-if="m.error" class="msg-error">
                  <svg viewBox="0 0 24 24" fill="none" width="14" height="14">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
                    <path d="M12 8v4M12 16h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                  </svg>
                  <span>{{ m.error }}</span>
                  <button class="msg-retry" type="button" @click="retryLast">重试</button>
                </div>
                <div v-if="m.sources?.length" class="msg-sources">
                  <button class="sources-toggle" type="button" @click="m.showSources = !m.showSources">
                    <svg viewBox="0 0 24 24" fill="none" width="12" height="12" :class="{ flip: m.showSources }">
                      <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    </svg>
                    {{ m.sources.length }} 个参考来源
                  </button>
                  <div v-if="m.showSources" class="sources-list">
                    <div v-for="(s, si) in m.sources" :key="si" class="source-item">
                      <span class="source-num">{{ si + 1 }}</span>
                      <span>{{ s }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="m.responseTime" class="msg-meta">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 6v6l4 2" />
                  </svg>
                  {{ (m.responseTime / 1000).toFixed(1) }}s
                </div>
                <div v-if="m.followUps?.length && i === msgs.length - 1 && !loading" class="follow-ups">
                  <span class="follow-ups-label">继续追问</span>
                  <div class="follow-ups-list">
                    <button v-for="(q, qi) in m.followUps" :key="qi" class="follow-up-btn" type="button" @click="send(q)">{{ q }}</button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="composer-area">
        <div class="composer-chips" v-if="msgs.length">
          <button class="chip-sm" type="button" @click="send('详细解释一下')">详细解释</button>
          <button class="chip-sm" type="button" @click="send('对比其他球员')">对比其他球员</button>
          <button class="chip-sm" type="button" @click="send('给出数据来源')">数据来源</button>
        </div>
        <div class="composer-wrap" :class="{ focus: composerFocused, 'has-text': canSend }">
          <textarea
            ref="inputEl"
            v-model="question"
            placeholder="询问球员、球队、赛程或战术问题…"
            rows="2"
            maxlength="500"
            :disabled="loading"
            @keydown="handleKeydown"
            @input="autoGrow"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
          ></textarea>
          <div class="composer-footer">
            <span class="char-count" :class="{ warn: question.length > 450 }">{{ question.length }}/500</span>
            <button class="send-btn" :class="{ active: canSend, loading }" type="button" :disabled="!canSend" aria-label="发送" @click="send()">
              <svg v-if="!loading" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <svg v-else class="spin-icon" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" opacity=".3" />
                <path d="M12 2C6.48 2 2 6.48 2 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>
        <div class="composer-hint">
          <span>Enter 发送 · Ctrl+Enter 换行</span>
          <span v-if="docCount > 0">已索引 {{ docCount.toLocaleString() }} 条数据</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getToken, clearAuthStorage } from '@/utils/authStorage'
import { sanitizeHtml } from '@/utils/sanitize'
import RelatedEntityCard from '@/components/domain/RelatedEntityCard.vue'
import { useMatchFeed } from '@/composables/useMatchFeed'
import type { RelatedEntity } from '@/api/types'

interface M {
  role: 'user' | 'assistant'
  content: string
  sources?: string[]
  showSources?: boolean
  responseTime?: number
  followUps?: string[]
  error?: string
  relatedEntities?: RelatedEntity[]
}
interface C { id: string; title: string; time: number; messages: M[] }

const route = useRoute()
const router = useRouter()
const inputEl = ref<HTMLTextAreaElement>()
const msgsEl = ref<HTMLElement>()
const question = ref('')
const msgs = ref<M[]>([])
const loading = ref(false)
const docCount = ref(0)
const sid = ref(gen())
const desktopSidebarQuery = typeof window !== 'undefined' ? window.matchMedia('(min-width: 769px)') : null
const sidebarOpen = ref(desktopSidebarQuery?.matches ?? false)
const isMobileViewport = ref(!(desktopSidebarQuery?.matches ?? false))
const convs = ref<C[]>([])
const composerFocused = ref(false)
const statusText = ref('正在思考...')
const elapsed = ref(0)
let currentAbort: AbortController | null = null
const activeAssistantIndex = ref<number | null>(null)
let statusTimer: ReturnType<typeof setTimeout> | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null
let scrollFrame: number | null = null
const autoFollow = ref(true)
const AUTO_FOLLOW_THRESHOLD = 96
const STREAM_IDLE_TIMEOUT_MS = Number(import.meta.env.VITE_RAG_STREAM_IDLE_TIMEOUT_MS || 300000)
const { matches: feedMatches, tickerMatches, live: liveMatches, groups: matchGroups, loading: feedLoading, failed: feedFailed, refresh: refreshMatchFeed } = useMatchFeed()
const showLocalScoreStatus = computed(() => isMobileViewport.value || !tickerMatches.value.length)

const scoreStatusText = computed(() => {
  if (feedLoading.value && !feedMatches.value.length) return '赛况更新中'
  if (feedFailed.value && !feedMatches.value.length) return '赛况暂不可用'
  return tickerMatches.value.length ? '当前暂无直播比赛' : '当前暂无比赛'
})

const todayMatches = computed(() => matchGroups.value.find(group => group.kind === 'today')?.matches ?? [])
const recentMatches = computed(() => matchGroups.value.find(group => group.kind === 'recent')?.matches ?? [])
const featuredTitle = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '今日赛事分析'
  return recentMatches.value.length ? '最近比赛复盘' : '赛事分析'
})
const featuredDescription = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '用真实赛况梳理胜负关键、核心对位与比赛看点。'
  if (recentMatches.value.length) return '回看最近已结束的比赛，梳理关键转折与数据。'
  return '从赛程、球队或球员入手，提出你的篮球问题。'
})
const featuredPrompt = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '基于今天的真实赛事数据，分析比赛看点、核心对位与胜负关键；未开赛的比赛请明确标注为赛前分析。'
  if (recentMatches.value.length) return '根据最近已结束的比赛数据，复盘关键转折和双方表现，并标明比赛日期与数据来源。'
  return '目前可以分析哪些 NBA 赛事、球队和球员数据？请说明数据范围。'
})

const groupedConvs = computed(() => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const buckets = new Map<string, C[]>()
  for (const conv of [...convs.value].sort((a, b) => b.time - a.time)) {
    const timestamp = Number(conv.time)
    const label = timestamp >= todayStart
      ? '今天'
      : timestamp >= todayStart - 86400000
        ? '昨天'
        : timestamp >= todayStart - 7 * 86400000
          ? '近 7 天'
          : '更早'
    if (!buckets.has(label)) buckets.set(label, [])
    buckets.get(label)!.push(conv)
  }
  return [...buckets].map(([label, items]) => ({ label, items }))
})

function onSidebarBreakpointChange(event: MediaQueryListEvent): void {
  sidebarOpen.value = event.matches
  isMobileViewport.value = !event.matches
}

function gen() { return 's_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) }
const canSend = computed(() => question.value.trim().length > 0 && !loading.value && question.value.trim().length <= 500)

// 持久化当前对话到 sessionStorage，切换页面后回来可恢复
const STORAGE_KEY = 'nba_copilot_active_session'

function saveActiveSession(): void {
  if (!msgs.value.length) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ sid: sid.value, msgs: msgs.value }))
  } catch { /* ignore quota errors */ }
}

function restoreActiveSession(): boolean {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const { sid: savedSid, msgs: savedMsgs } = JSON.parse(raw)
    if (Array.isArray(savedMsgs) && savedMsgs.length > 0) {
      sid.value = savedSid
      msgs.value = savedMsgs
      autoFollow.value = true
      return true
    }
  } catch { /* ignore parse errors */ }
  return false
}

function render(m: M, i: number): string {
  let html = sanitizeHtml(m.content).replace(/\*\*(.*?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>')
  if (loading.value && i === msgs.value.length - 1) html += '<span class="typing-cursor"></span>'
  return html
}

function autoGrow(): void {
  const e = inputEl.value
  if (e) { e.style.height = 'auto'; e.style.height = Math.min(e.scrollHeight, 180) + 'px' }
}

function isNearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= AUTO_FOLLOW_THRESHOLD
}

function handleContentScroll(): void {
  if (msgsEl.value) autoFollow.value = isNearBottom(msgsEl.value)
}

function scrollToBottom(force = false): void {
  if (!force && !autoFollow.value) return
  nextTick(() => {
    if (!msgsEl.value) return
    if (scrollFrame !== null) {
      cancelAnimationFrame(scrollFrame)
      scrollFrame = null
    }
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = null
      if (!msgsEl.value) return
      msgsEl.value.scrollTop = msgsEl.value.scrollHeight
    })
  })
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && loading.value) cancelQuery()
  else if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) { e.preventDefault(); send() }
  else if (e.key === 'Enter' && e.ctrlKey) question.value += '\n'
}

function clearTimers(): void {
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  elapsed.value = 0
  statusText.value = '正在思考...'
}

function cancelQuery(): void {
  const index = activeAssistantIndex.value
  if (index !== null && msgs.value[index]?.role === 'assistant') {
    msgs.value[index].error = '回答已取消'
    msgs.value[index].content = ''
  }
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  activeAssistantIndex.value = null
  clearTimers()
  loading.value = false
  saveActiveSession()
}

function retryLast(): void {
  const lastUserMsg = [...msgs.value].reverse().find(m => m.role === 'user')
  if (lastUserMsg) {
    const lastIdx = msgs.value.length - 1
    if (msgs.value[lastIdx]?.error) msgs.value.splice(lastIdx, 1)
    send(lastUserMsg.content)
  }
}

async function loadConvs(): Promise<void> {
  try { const { data } = await request.get('/rag/conversations'); convs.value = data || [] }
  catch { convs.value = [] }
}

async function saveConv(): Promise<void> {
  if (!msgs.value.length) return
  const validMsgs: M[] = []
  for (let index = 0; index < msgs.value.length - 1; index += 1) {
    const user = msgs.value[index]
    const assistant = msgs.value[index + 1]
    if (user.role !== 'user' || assistant.role !== 'assistant' || assistant.error || !assistant.content.trim()) continue
    validMsgs.push(user, assistant)
    index += 1
  }
  if (!validMsgs.length) return
  const title = validMsgs[0]?.content?.slice(0, 30) || '新对话'
  try {
    await request.post('/rag/conversations', { sessionId: sid.value, title, messages: JSON.stringify(validMsgs) })
    await loadConvs()
  } catch { /* ignore */ }
}

async function loadConv(c: C): Promise<void> {
  try {
    const { data } = await request.get(`/rag/conversations/${c.id}`)
    if (data?.messages) {
      sid.value = c.id
      msgs.value = JSON.parse(data.messages)
      if (!desktopSidebarQuery?.matches) sidebarOpen.value = false
      autoFollow.value = true
      saveActiveSession()
      scrollToBottom(true)
    }
  } catch { /* ignore */ }
}

async function delConv(id: string): Promise<void> {
  try {
    await request.delete(`/rag/conversations/${id}`)
    convs.value = convs.value.filter(c => c.id !== id)
    if (sid.value === id) newChat()
  } catch { /* ignore */ }
}

function newChat(): void {
  if (loading.value) cancelQuery()
  if (msgs.value.length) saveConv()
  msgs.value = []
  sid.value = gen()
  autoFollow.value = true
  if (!desktopSidebarQuery?.matches) sidebarOpen.value = false
  sessionStorage.removeItem(STORAGE_KEY)
}

function fmtTime(t: number): string {
  const d = Date.now() - t
  if (d < 60000) return '刚刚'
  if (d < 3600000) return `${Math.floor(d / 60000)} 分钟前`
  if (d < 86400000) return `${Math.floor(d / 3600000)} 小时前`
  return new Date(t).toLocaleDateString('zh-CN')
}

function exportChat(): void {
  if (!msgs.value.length) return
  let md = `# NBA Copilot 对话\n\n导出时间: ${new Date().toLocaleString('zh-CN')}\n\n---\n\n`
  msgs.value.forEach(m => {
    md += `**${m.role === 'user' ? '用户' : 'AI 助手'}**\n\n${m.content}\n\n`
    if (m.sources?.length) md += `> 参考来源: ${m.sources.join(', ')}\n\n`
    md += `---\n\n`
  })
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `NBA对话_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

const shareUrl = ref('')
const showShareModal = ref(false)
async function shareChat(): Promise<void> {
  if (!msgs.value.length) return
  await saveConv()
  try {
    const { data } = await request.post(`/rag/conversations/${sid.value}/share`)
    if (data?.shareId) {
      const target = router.resolve({ name: 'smart-search', query: { share: data.shareId } })
      shareUrl.value = new URL(target.href, `${window.location.origin}/`).toString()
      showShareModal.value = true
      await navigator.clipboard.writeText(shareUrl.value)
    }
  } catch { /* ignore */ }
}
function copyShareUrl(): void { navigator.clipboard.writeText(shareUrl.value) }
function selectShareInput(event: FocusEvent): void {
  const input = event.target as HTMLInputElement | null
  input?.select()
}

function generateFollowUps(content: string): string[] {
  const lower = content.toLowerCase()
  const suggestions: string[] = []
  if (lower.includes('球员') || lower.includes('得分')) {
    suggestions.push('对比这名球员和其他顶级球员')
    suggestions.push('查看生涯趋势')
  }
  if (lower.includes('球队') || lower.includes('战绩')) {
    suggestions.push('分析季后赛前景')
    suggestions.push('对比同分区对手')
  }
  if (lower.includes('比赛') || lower.includes('总决赛')) {
    suggestions.push('分析关键转折点')
    suggestions.push('查看双方战术统计')
  }
  if (!suggestions.length) {
    suggestions.push('详细解释一下')
    suggestions.push('相关数据还有哪些？')
  }
  return suggestions.slice(0, 3)
}

async function send(text?: string): Promise<void> {
  const content = (text || question.value.trim()).slice(0, 500)
  if (!content || loading.value) return
  loading.value = true
  msgs.value.push({ role: 'user', content })
  question.value = ''
  const ai = msgs.value.length
  msgs.value.push({ role: 'assistant', content: '' })
  activeAssistantIndex.value = ai
  autoFollow.value = true
  scrollToBottom(true)

  statusText.value = '正在检索相关数据...'
  elapsed.value = 0
  elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)
  statusTimer = setTimeout(() => { statusText.value = '正在生成回答...' }, 3000)

  const abortController = new AbortController()
  currentAbort = abortController
  let idleTimedOut = false
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  const clearIdleTimeout = () => {
    if (timeoutId) clearTimeout(timeoutId)
    timeoutId = null
  }
  const resetIdleTimeout = () => {
    clearIdleTimeout()
    timeoutId = setTimeout(() => {
      if (loading.value) {
        idleTimedOut = true
        abortController.abort()
      }
    }, STREAM_IDLE_TIMEOUT_MS)
  }
  resetIdleTimeout()

  try {
    const token = getToken() || ''
    const headers: Record<string, string> = {}
    if (token) headers.Authorization = `Bearer ${token}`
    // GET 流式（POST + StreamingResponseBody 在 Tomcat 上不写 chunked 终止块，
    // 浏览器会把流尾判为网络错误）；会话上下文由服务端按 session_id 加载
    const streamParams = new URLSearchParams({ question: content, top_k: '5' })
    if (sid.value) streamParams.set('session_id', sid.value)
    // 连接失败自动重试一次：后端短暂重启/网络抖动时无缝恢复，避免直接把错误抛给用户
    let response!: Response
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        response = await fetch(`/api/rag/stream?${streamParams.toString()}`, {
          method: 'GET',
          headers,
          signal: abortController.signal,
        })
        break
      } catch (err: any) {
        if (err?.name === 'AbortError' || attempt === 1) throw err
        statusText.value = '连接中断，正在重连...'
        await new Promise((resolve) => setTimeout(resolve, 1200))
      }
    }

    if (response.status === 401 || response.status === 403) {
      msgs.value[ai].error = '登录已过期，请重新登录'
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
      clearAuthStorage()
      setTimeout(() => router.push({ name: 'login', query: { redirect: route.fullPath } }), 1500)
      return
    }
    if (!response.ok) {
      msgs.value[ai].error = `服务异常 (${response.status})`
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
      return
    }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let receivedDone = false
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      resetIdleTimeout()
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const d = JSON.parse(line.slice(6))
            if (d.type === 'sources') { msgs.value[ai].sources = d.sources || []; statusText.value = '正在生成回答...' }
            else if (d.type === 'chunk') { msgs.value[ai].content += d.content; scrollToBottom() }
            else if (d.type === 'entities') { msgs.value[ai].relatedEntities = d.entities || [] }
            else if (d.type === 'done') {
              receivedDone = true
              msgs.value[ai].responseTime = d.response_time
              msgs.value[ai].followUps = generateFollowUps(msgs.value[ai].content)
              if (d.entities?.length && !msgs.value[ai].relatedEntities?.length) msgs.value[ai].relatedEntities = d.entities
              loading.value = false
              currentAbort = null
              activeAssistantIndex.value = null
              clearIdleTimeout()
              clearTimers()
              saveConv()
              saveActiveSession()
            }
            else if (d.type === 'error') {
              msgs.value[ai].error = d.error || '服务暂时不可用'
              msgs.value[ai].content = ''
              loading.value = false
              currentAbort = null
              activeAssistantIndex.value = null
              clearIdleTimeout()
              clearTimers()
              saveActiveSession()
            }
          } catch { /* ignore */ }
        }
      }
    }
    if (loading.value) {
      if (!receivedDone) {
        msgs.value[ai].error = '连接在回答完成前中断，请重试'
        msgs.value[ai].content = ''
      }
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
    }
  } catch (err: any) {
    if (idleTimedOut) {
      msgs.value[ai].error = 'AI 长时间未返回新数据，请稍后重试'
      msgs.value[ai].content = ''
    } else if (err.name !== 'AbortError') {
      msgs.value[ai].error = '网络连接失败，请检查网络后重试'
      msgs.value[ai].content = ''
    }
    loading.value = false
    currentAbort = null
    activeAssistantIndex.value = null
    clearIdleTimeout()
    clearTimers()
  }
}

onMounted(async () => {
  desktopSidebarQuery?.addEventListener('change', onSidebarBreakpointChange)
  if (!feedMatches.value.length) void refreshMatchFeed()
  try { const { data } = await request.get('/rag/stats'); docCount.value = data?.documentCount || 0 } catch { docCount.value = 0 }
  const shareId = typeof route.query.share === 'string' ? route.query.share.trim() : ''
  if (shareId) {
    try { const { data } = await request.get(`/rag/shared/${shareId}`); if (data?.messages) { sid.value = data.id; msgs.value = JSON.parse(data.messages); scrollToBottom(true) } } catch { /* ignore */ }
  } else if (!restoreActiveSession()) {
    await loadConvs()
  } else {
    // 恢复了活跃会话，也加载历史列表
    await loadConvs()
    nextTick(() => scrollToBottom(true))
  }
})

onUnmounted(() => {
  desktopSidebarQuery?.removeEventListener('change', onSidebarBreakpointChange)
  saveActiveSession()
  cancelQuery()
  if (scrollFrame !== null) cancelAnimationFrame(scrollFrame)
})
</script>

<style scoped src="./SmartSearchSports.css"></style>
