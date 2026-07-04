<template>
  <div class="copilot-workspace">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-head">
        <span>历史对话</span>
        <button class="sidebar-close" type="button" aria-label="关闭历史对话" @click="sidebarOpen = false">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>
      <div class="sidebar-body">
        <p v-if="!convs.length" class="sidebar-empty">暂无对话</p>
        <div v-for="c in convs" :key="c.id" class="sidebar-item" :class="{ active: c.id === sid }" @click="loadConv(c)">
          <span class="sidebar-item-title">{{ c.title }}</span>
          <span class="sidebar-item-time">{{ fmtTime(c.time) }}</span>
          <button class="sidebar-item-del" type="button" aria-label="删除对话" @click.stop="delConv(c.id)">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14">
              <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>
    </aside>
    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

    <div v-if="showShareModal" class="modal-mask" @click="showShareModal = false">
      <div class="modal-content" @click.stop>
        <h3>分享对话</h3>
        <p>复制链接后，可把这次分析结果分享给其他人查看。</p>
        <div class="modal-share-row">
          <input :value="shareUrl" readonly @focus="$event.target.select()" />
          <button type="button" @click="copyShareUrl">复制</button>
        </div>
        <button class="modal-close-btn" type="button" @click="showShareModal = false">关闭</button>
      </div>
    </div>

    <div class="workspace-main">
      <header class="top-bar">
        <div class="top-left">
          <button class="icon-btn" type="button" title="历史对话" @click="sidebarOpen = !sidebarOpen">
            <svg viewBox="0 0 24 24" fill="none" width="17" height="17">
              <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
          <button class="top-btn" type="button" @click="newChat">
            <svg viewBox="0 0 24 24" fill="none" width="15" height="15">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            新对话
          </button>
          <span class="top-status">NBA Copilot</span>
        </div>
        <div class="top-right">
          <button class="top-btn" type="button" :disabled="!msgs.length" @click="exportChat">
            <svg viewBox="0 0 24 24" fill="none" width="15" height="15">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            导出
          </button>
          <button class="top-btn primary" type="button" :disabled="!msgs.length" @click="shareChat">
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

      <div class="content-area" ref="msgsEl">
        <div v-if="!msgs.length" class="welcome-screen">
          <section class="welcome-hero">
            <div class="hero-badge">NBA COPILOT</div>
            <h1 class="hero-title">你的 <span>NBA 数据分析助手</span></h1>
            <p class="hero-desc">
              基于系统知识库、实时赛事数据与联网搜索，为你生成可追溯的篮球分析结论。
            </p>
            <div class="hero-metrics">
              <span>已连接 {{ docCount.toLocaleString() }} 条知识片段</span>
              <span>RAG + 实时检索</span>
              <span>比赛、球员、球队全域问答</span>
            </div>
          </section>

          <section class="capabilities" aria-label="AI 能力入口">
            <button class="cap-main" type="button" @click="send('分析今天所有比赛的胜负关键因素和看点')">
              <div class="cap-main-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" width="30" height="30">
                  <rect x="3" y="4" width="18" height="18" rx="2.5" />
                  <path d="M16 2v4M8 2v4M3 10h18" stroke-linecap="round" />
                </svg>
              </div>
              <div class="cap-main-text">
                <span class="cap-main-kicker">推荐任务</span>
                <span class="cap-main-title">今日赛事分析</span>
                <span class="cap-main-desc">分析今日比赛的胜负关键、核心对位、伤病影响和战术看点。</span>
                <span class="cap-main-foot">实时数据 · 战术视角 · 可追溯来源</span>
              </div>
              <svg viewBox="0 0 24 24" fill="none" width="22" height="22" class="cap-main-arrow">
                <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>

            <div class="cap-grid">
              <button class="cap-card" type="button" @click="send('对比勒布朗·詹姆斯和凯文·杜兰特本赛季的数据表现')">
                <span class="cap-icon people">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                    <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
                  </svg>
                </span>
                <span class="cap-title">球员对比</span>
              </button>
              <button class="cap-card" type="button" @click="send('雷霆队本赛季的战绩和核心球员表现如何？')">
                <span class="cap-icon globe">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 2a14.5 14.5 0 0 0 0 20M12 2a14.5 14.5 0 0 1 0 20M2 12h20" />
                  </svg>
                </span>
                <span class="cap-title">球队查询</span>
              </button>
              <button class="cap-card" type="button" @click="send('解释上一场总决赛的抢断统计和关键数据')">
                <span class="cap-icon doc">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <path d="M14 2v6h6M16 13H8M16 17H8" />
                  </svg>
                </span>
                <span class="cap-title">数据解读</span>
              </button>
              <button class="cap-card" type="button" @click="send('生成本赛季季后赛的赛前看点和预测分析')">
                <span class="cap-icon trophy">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6M18 9h1.5a2.5 2.5 0 0 0 0-5H18M4 22h16" />
                    <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22M18 2H6v7a6 6 0 0 0 12 0V2Z" />
                  </svg>
                </span>
                <span class="cap-title">季后赛预测</span>
              </button>
              <button class="cap-card" type="button" @click="send('本赛季三分命中率最高的球员有哪些？')">
                <span class="cap-icon chart">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="M4 19V5M9 19v-8M14 19V8M19 19V3" stroke-linecap="round" />
                  </svg>
                </span>
                <span class="cap-title">数据排行</span>
              </button>
              <button class="cap-card" type="button" @click="send('谁是本赛季的 MVP 热门候选人？分析他们的数据表现')">
                <span class="cap-icon star">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                    <path d="m12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2Z" />
                  </svg>
                </span>
                <span class="cap-title">MVP 分析</span>
              </button>
            </div>
          </section>

          <div class="prompt-chips">
            <button class="chip" type="button" @click="send('今天有哪些比赛？')">今天有哪些比赛？</button>
            <button class="chip" type="button" @click="send('分析湖人最近 5 场比赛的表现')">湖人最近 5 场表现</button>
            <button class="chip" type="button" @click="send('库里和东契奇谁的三分更准？')">库里 vs 东契奇三分</button>
            <button class="chip" type="button" @click="send('本赛季助攻榜前五是谁？')">助攻榜 Top 5</button>
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
            placeholder="询问任何 NBA 数据、球员、球队、赛程或战术问题..."
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
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import request from '@/utils/request'
import { getToken, clearAuthStorage } from '@/utils/authStorage'
import { sanitizeHtml } from '@/utils/sanitize'
import RelatedEntityCard from '@/components/domain/RelatedEntityCard.vue'
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

const inputEl = ref<HTMLTextAreaElement>()
const msgsEl = ref<HTMLElement>()
const question = ref('')
const msgs = ref<M[]>([])
const loading = ref(false)
const docCount = ref(0)
const sid = ref(gen())
const sidebarOpen = ref(false)
const convs = ref<C[]>([])
const composerFocused = ref(false)
const statusText = ref('正在思考...')
const elapsed = ref(0)
let currentAbort: AbortController | null = null
let statusTimer: ReturnType<typeof setTimeout> | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null

function gen() { return 's_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) }
const canSend = computed(() => question.value.trim().length > 0 && !loading.value && question.value.trim().length <= 500)

function render(m: M, i: number): string {
  let html = sanitizeHtml(m.content).replace(/\*\*(.*?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>')
  if (loading.value && i === msgs.value.length - 1) html += '<span class="typing-cursor"></span>'
  return html
}

function autoGrow(): void {
  const e = inputEl.value
  if (e) { e.style.height = 'auto'; e.style.height = Math.min(e.scrollHeight, 180) + 'px' }
}

function scroll(): void {
  nextTick(() => { if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight })
}
watch(msgs, scroll, { deep: true })

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
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  clearTimers()
  loading.value = false
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
  const validMsgs = msgs.value.filter(m => !m.error)
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
    if (data?.messages) { sid.value = c.id; msgs.value = JSON.parse(data.messages); sidebarOpen.value = false; scroll() }
  } catch { /* ignore */ }
}

async function delConv(id: string): Promise<void> {
  try {
    await request.delete(`/rag/conversations/${id}`)
    convs.value = convs.value.filter(c => c.id !== id)
    if (sid.value === id) newChat()
  } catch { /* ignore */ }
}

function newChat(): void { if (msgs.value.length) saveConv(); msgs.value = []; sid.value = gen() }

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
      shareUrl.value = `${window.location.origin}/smart-search?share=${data.shareId}`
      showShareModal.value = true
      await navigator.clipboard.writeText(shareUrl.value)
    }
  } catch { /* ignore */ }
}
function copyShareUrl(): void { navigator.clipboard.writeText(shareUrl.value) }

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
  scroll()

  statusText.value = '正在检索相关数据...'
  elapsed.value = 0
  elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)
  statusTimer = setTimeout(() => { statusText.value = '正在生成回答...' }, 3000)

  const abortController = new AbortController()
  currentAbort = abortController
  const timeout = content.length < 50 ? 30000 : content.length < 200 ? 60000 : 90000
  const timeoutId = setTimeout(() => {
    if (loading.value) {
      abortController.abort()
      msgs.value[ai].error = '请求超时，请稍后重试'
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      clearTimers()
    }
  }, timeout)

  try {
    const token = getToken() || ''
    const response = await fetch(`/api/rag/stream?question=${encodeURIComponent(content)}&top_k=5&session_id=${sid.value}`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: abortController.signal,
    })

    if (response.status === 401 || response.status === 403) {
      msgs.value[ai].error = '登录已过期，请重新登录'
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      clearTimeout(timeoutId)
      clearTimers()
      clearAuthStorage()
      setTimeout(() => { window.location.href = '/login' }, 1500)
      return
    }
    if (!response.ok) {
      msgs.value[ai].error = `服务异常 (${response.status})`
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      clearTimeout(timeoutId)
      clearTimers()
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const d = JSON.parse(line.slice(6))
            if (d.type === 'sources') { msgs.value[ai].sources = d.sources || []; statusText.value = '正在生成回答...' }
            else if (d.type === 'chunk') { msgs.value[ai].content += d.content; scroll() }
            else if (d.type === 'entities') { msgs.value[ai].relatedEntities = d.entities || [] }
            else if (d.type === 'done') {
              msgs.value[ai].responseTime = d.response_time
              msgs.value[ai].followUps = generateFollowUps(msgs.value[ai].content)
              if (d.entities?.length && !msgs.value[ai].relatedEntities?.length) msgs.value[ai].relatedEntities = d.entities
              loading.value = false
              currentAbort = null
              clearTimeout(timeoutId)
              clearTimers()
              saveConv()
            }
            else if (d.type === 'error') {
              msgs.value[ai].error = d.error || '服务暂时不可用'
              msgs.value[ai].content = ''
              loading.value = false
              currentAbort = null
              clearTimeout(timeoutId)
              clearTimers()
            }
          } catch { /* ignore */ }
        }
      }
    }
    if (loading.value) {
      msgs.value[ai].followUps = generateFollowUps(msgs.value[ai].content)
      loading.value = false
      currentAbort = null
      clearTimeout(timeoutId)
      clearTimers()
      saveConv()
    }
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      msgs.value[ai].error = '网络连接失败，请检查网络后重试'
      msgs.value[ai].content = ''
    }
    loading.value = false
    currentAbort = null
    clearTimeout(timeoutId)
    clearTimers()
  }
}

onMounted(async () => {
  try { const { data } = await request.get('/rag/stats'); docCount.value = data?.documentCount || 0 } catch { docCount.value = 0 }
  await loadConvs()
  const params = new URLSearchParams(window.location.search)
  const shareId = params.get('share')
  if (shareId) {
    try { const { data } = await request.get(`/rag/shared/${shareId}`); if (data?.messages) { sid.value = data.id; msgs.value = JSON.parse(data.messages) } } catch { /* ignore */ }
  }
})

onUnmounted(() => { cancelQuery() })
</script>

<style scoped>
.copilot-workspace,
.copilot-workspace * {
  box-sizing: border-box;
}

.copilot-workspace {
  --copilot-bg: #050814;
  --copilot-bg-2: #0b1020;
  --copilot-surface: rgba(12, 18, 34, 0.84);
  --copilot-surface-strong: rgba(10, 16, 30, 0.96);
  --copilot-card: rgba(16, 24, 44, 0.76);
  --copilot-card-soft: rgba(20, 30, 52, 0.58);
  --copilot-border: rgba(148, 163, 184, 0.19);
  --copilot-border-strong: rgba(122, 146, 255, 0.42);
  --copilot-text: #f8fafc;
  --copilot-muted: #a7b3c8;
  --copilot-dim: #748197;
  --copilot-blue: #4f8cff;
  --copilot-purple: #9b5cff;
  --copilot-cyan: #22d3ee;
  --copilot-orange: #ff7a2f;
  --copilot-green: #22c55e;
  --composer-safe-bottom: 0px;
  display: flex;
  height: calc(100vh - 56px);
  min-height: 0;
  overflow: hidden;
  position: relative;
  color: var(--copilot-text);
  background:
    radial-gradient(circle at 14% 0%, rgba(79, 140, 255, 0.26), transparent 29%),
    radial-gradient(circle at 86% 6%, rgba(255, 122, 47, 0.19), transparent 25%),
    radial-gradient(circle at 50% 100%, rgba(155, 92, 255, 0.15), transparent 36%),
    linear-gradient(135deg, #050814 0%, #081228 46%, #10131d 100%);
}

.copilot-workspace::before,
.copilot-workspace::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.copilot-workspace::before {
  opacity: 0.6;
  background:
    linear-gradient(90deg, rgba(148, 163, 184, 0.045) 1px, transparent 1px),
    linear-gradient(rgba(148, 163, 184, 0.035) 1px, transparent 1px),
    radial-gradient(ellipse at 50% 34%, transparent 0 34%, rgba(79, 140, 255, 0.1) 35%, transparent 36% 100%);
  background-size: 80px 80px, 80px 80px, 760px 360px;
}

.copilot-workspace::after {
  opacity: 0.78;
  background:
    linear-gradient(115deg, transparent 0 63%, rgba(155, 92, 255, 0.11) 67%, transparent 74%),
    radial-gradient(ellipse at 84% 28%, rgba(79, 140, 255, 0.12), transparent 24%),
    linear-gradient(to bottom, rgba(2, 6, 23, 0.1), rgba(2, 6, 23, 0.28));
}

[data-theme="light"] .copilot-workspace {
  --copilot-bg: #f6f8fc;
  --copilot-bg-2: #eef4ff;
  --copilot-surface: rgba(255, 255, 255, 0.82);
  --copilot-surface-strong: rgba(255, 255, 255, 0.96);
  --copilot-card: rgba(255, 255, 255, 0.82);
  --copilot-card-soft: rgba(255, 255, 255, 0.64);
  --copilot-border: rgba(148, 163, 184, 0.25);
  --copilot-border-strong: rgba(79, 140, 255, 0.32);
  --copilot-text: #0f172a;
  --copilot-muted: #526179;
  --copilot-dim: #8a96aa;
  color: var(--copilot-text);
  background:
    radial-gradient(circle at 15% 2%, rgba(79, 140, 255, 0.2), transparent 30%),
    radial-gradient(circle at 87% 8%, rgba(255, 122, 47, 0.14), transparent 28%),
    radial-gradient(circle at 48% 100%, rgba(155, 92, 255, 0.12), transparent 38%),
    linear-gradient(135deg, #f8fbff 0%, #eef4ff 54%, #fff8f3 100%);
}

[data-theme="light"] .copilot-workspace::before {
  opacity: 0.55;
  background:
    linear-gradient(90deg, rgba(79, 140, 255, 0.055) 1px, transparent 1px),
    linear-gradient(rgba(79, 140, 255, 0.045) 1px, transparent 1px),
    radial-gradient(ellipse at 50% 34%, transparent 0 34%, rgba(79, 140, 255, 0.08) 35%, transparent 36% 100%);
  background-size: 80px 80px, 80px 80px, 760px 360px;
}

[data-theme="light"] .copilot-workspace::after {
  background:
    linear-gradient(115deg, transparent 0 63%, rgba(255, 122, 47, 0.08) 67%, transparent 74%),
    radial-gradient(ellipse at 84% 28%, rgba(79, 140, 255, 0.1), transparent 24%);
}

.sidebar {
  width: 282px;
  flex-shrink: 0;
  margin-left: -282px;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 20;
  color: var(--copilot-text);
  background: var(--copilot-surface-strong);
  border-right: 1px solid var(--copilot-border);
  box-shadow: 18px 0 48px rgba(0, 0, 0, 0.24);
  transition: margin-left 0.22s ease;
}

.sidebar.open { margin-left: 0; }

.sidebar-head {
  height: 58px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--copilot-border);
  font-size: 14px;
  font-weight: 700;
}

.sidebar-close,
.sidebar-item-del,
.icon-btn,
.top-btn,
.cap-main,
.cap-card,
.chip,
.chip-sm,
.send-btn,
.loading-cancel,
.msg-retry,
.sources-toggle,
.follow-up-btn,
.modal-close-btn {
  font-family: inherit;
}

.sidebar-close,
.sidebar-item-del,
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  color: var(--copilot-muted);
  background: transparent;
  cursor: pointer;
}

.sidebar-close,
.icon-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.sidebar-close:hover,
.icon-btn:hover {
  color: var(--copilot-text);
  border-color: var(--copilot-border);
  background: rgba(255, 255, 255, 0.07);
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px;
}

.sidebar-empty {
  margin: 0;
  padding: 32px 12px;
  text-align: center;
  color: var(--copilot-dim);
  font-size: 13px;
}

.sidebar-item {
  min-height: 54px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 8px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.16s, background 0.16s;
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--copilot-border);
}

.sidebar-item.active {
  background: rgba(79, 140, 255, 0.12);
  border-color: var(--copilot-border-strong);
}

.sidebar-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--copilot-text);
}

.sidebar-item-time {
  grid-column: 1;
  font-size: 11px;
  color: var(--copilot-dim);
}

.sidebar-item-del {
  grid-column: 2;
  grid-row: 1 / 3;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  opacity: 0;
}

.sidebar-item:hover .sidebar-item-del { opacity: 1; }
.sidebar-item-del:hover { color: #ef4444; background: rgba(239, 68, 68, 0.12); }

.sidebar-mask {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.54);
  backdrop-filter: blur(3px);
  z-index: 18;
}

.workspace-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

.top-bar {
  height: 58px;
  flex-shrink: 0;
  padding: 0 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(180deg, rgba(8, 13, 27, 0.82), rgba(8, 13, 27, 0.58));
  border-bottom: 1px solid var(--copilot-border);
  backdrop-filter: blur(18px) saturate(150%);
}

[data-theme="light"] .top-bar {
  background: rgba(255, 255, 255, 0.68);
}

.top-left,
.top-right {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.top-status {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--copilot-blue);
  background: rgba(79, 140, 255, 0.1);
  border: 1px solid rgba(79, 140, 255, 0.18);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.top-btn {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  border: 1px solid var(--copilot-border);
  border-radius: 10px;
  color: var(--copilot-muted);
  background: rgba(255, 255, 255, 0.055);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  white-space: nowrap;
  transition: transform 0.16s, border-color 0.16s, color 0.16s, background 0.16s;
}

.top-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  color: var(--copilot-text);
  border-color: var(--copilot-border-strong);
  background: rgba(79, 140, 255, 0.1);
}

.top-btn.primary {
  color: #fff;
  border-color: rgba(79, 140, 255, 0.35);
  background: linear-gradient(135deg, rgba(79, 140, 255, 0.58), rgba(155, 92, 255, 0.58));
}

.top-btn:disabled {
  opacity: 0.46;
  cursor: not-allowed;
}

.content-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
  -webkit-overflow-scrolling: touch;
}

.welcome-screen {
  width: min(1120px, calc(100% - 48px));
  min-height: 100%;
  margin: 0 auto;
  padding: clamp(28px, 5vh, 50px) 0 26px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 22px;
}

.welcome-hero {
  text-align: center;
  padding: 4px 16px 2px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 16px;
  margin-bottom: 13px;
  border-radius: 999px;
  color: #d9c4ff;
  background: linear-gradient(135deg, rgba(155, 92, 255, 0.28), rgba(79, 140, 255, 0.17));
  border: 1px solid rgba(155, 92, 255, 0.34);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

[data-theme="light"] .hero-badge {
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.08);
  border-color: rgba(124, 58, 237, 0.16);
}

.hero-title {
  margin: 0;
  color: var(--copilot-text);
  font-family: var(--font-heading);
  font-size: clamp(34px, 4.2vw, 56px);
  font-weight: 850;
  line-height: 1.08;
  letter-spacing: 0;
}

.hero-title span {
  color: transparent;
  background: linear-gradient(110deg, #c084fc 0%, #60a5fa 52%, #22d3ee 100%);
  -webkit-background-clip: text;
  background-clip: text;
}

[data-theme="light"] .hero-title span {
  background: linear-gradient(110deg, #a855f7 0%, #3b82f6 58%, #0891b2 100%);
  -webkit-background-clip: text;
  background-clip: text;
}

.hero-desc {
  max-width: 700px;
  margin: 14px auto 0;
  color: var(--copilot-muted);
  font-size: 15px;
  line-height: 1.7;
  text-wrap: pretty;
}

.hero-metrics {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.hero-metrics span {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--copilot-muted);
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid var(--copilot-border);
  font-size: 12px;
  line-height: 1.2;
}

[data-theme="light"] .hero-metrics span {
  background: rgba(255, 255, 255, 0.62);
}

.capabilities {
  display: grid;
  grid-template-columns: minmax(430px, 1fr) minmax(360px, 420px);
  gap: 16px;
  align-items: stretch;
}

.cap-main,
.cap-card {
  border: 1px solid var(--copilot-border);
  color: var(--copilot-text);
  background: linear-gradient(180deg, var(--copilot-card), rgba(10, 16, 30, 0.7));
  backdrop-filter: blur(16px) saturate(150%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 18px 42px rgba(0, 0, 0, 0.16);
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

[data-theme="light"] .cap-main,
[data-theme="light"] .cap-card {
  background: rgba(255, 255, 255, 0.74);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.cap-main:hover,
.cap-card:hover {
  transform: translateY(-2px);
  border-color: var(--copilot-border-strong);
  box-shadow: 0 20px 46px rgba(79, 140, 255, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.cap-main {
  min-height: 176px;
  padding: 28px;
  border-radius: 20px;
  display: grid;
  grid-template-columns: 62px 1fr auto;
  gap: 18px;
  align-items: center;
  text-align: left;
  position: relative;
  overflow: hidden;
}

.cap-main::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.9;
  background:
    radial-gradient(circle at 8% 12%, rgba(255, 122, 47, 0.13), transparent 34%),
    linear-gradient(125deg, rgba(79, 140, 255, 0.1), transparent 48%);
  pointer-events: none;
}

.cap-main > * {
  position: relative;
  z-index: 1;
}

.cap-main-icon,
.cap-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--copilot-blue);
  background: linear-gradient(135deg, rgba(79, 140, 255, 0.18), rgba(155, 92, 255, 0.19));
  border: 1px solid rgba(79, 140, 255, 0.2);
}

.cap-main-icon {
  width: 62px;
  height: 62px;
  border-radius: 18px;
  box-shadow: 0 0 30px rgba(79, 140, 255, 0.16);
}

.cap-main-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cap-main-kicker,
.cap-main-foot {
  color: var(--copilot-dim);
  font-size: 12px;
  font-weight: 650;
}

.cap-main-title {
  color: var(--copilot-text);
  font-size: 20px;
  font-weight: 850;
  line-height: 1.25;
}

.cap-main-desc {
  color: var(--copilot-muted);
  font-size: 14px;
  line-height: 1.58;
  text-wrap: pretty;
}

.cap-main-arrow {
  color: var(--copilot-purple);
  transition: transform 0.18s ease;
}

.cap-main:hover .cap-main-arrow { transform: translateX(4px); }

.cap-grid {
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.cap-card {
  min-height: 88px;
  padding: 14px 10px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.cap-icon {
  width: 40px;
  height: 40px;
  border-radius: 13px;
}

.cap-icon.people { color: #e879f9; }
.cap-icon.globe { color: #60a5fa; }
.cap-icon.doc { color: #5eead4; }
.cap-icon.trophy { color: #f59e0b; }
.cap-icon.chart { color: #22d3ee; }
.cap-icon.star { color: #818cf8; }

.cap-title {
  color: var(--copilot-text);
  font-size: 13px;
  font-weight: 760;
  line-height: 1.2;
  text-align: center;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.chip,
.chip-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--copilot-border);
  color: var(--copilot-muted);
  background: rgba(255, 255, 255, 0.055);
  cursor: pointer;
  white-space: nowrap;
  transition: transform 0.16s, border-color 0.16s, color 0.16s, background 0.16s;
}

.chip {
  min-height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 650;
}

.chip-sm {
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
}

[data-theme="light"] .chip,
[data-theme="light"] .chip-sm {
  background: rgba(255, 255, 255, 0.72);
}

.chip:hover,
.chip-sm:hover {
  transform: translateY(-1px);
  color: var(--copilot-blue);
  border-color: var(--copilot-border-strong);
  background: rgba(79, 140, 255, 0.1);
}

.chat-flow {
  width: min(1040px, calc(100% - 32px));
  margin: 0 auto;
  padding: 26px 0 160px;
}

.msg-row {
  display: flex;
  margin-bottom: 18px;
}

.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { gap: 12px; }

.msg-bubble {
  max-width: min(78%, 760px);
  padding: 13px 16px;
  border-radius: 17px;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.msg-bubble.user {
  color: #fff;
  border-radius: 17px 17px 5px 17px;
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
  box-shadow: 0 14px 30px rgba(79, 140, 255, 0.22);
}

.msg-bubble.ai {
  color: var(--copilot-text);
  border: 1px solid var(--copilot-border);
  background: var(--copilot-card);
  backdrop-filter: blur(16px);
}

[data-theme="light"] .msg-bubble.ai {
  background: rgba(255, 255, 255, 0.78);
}

.msg-bubble.ai :deep(b) {
  color: var(--copilot-blue);
  font-weight: 750;
}

.msg-avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  margin-top: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  color: #dbeafe;
  background: linear-gradient(135deg, rgba(79, 140, 255, 0.24), rgba(155, 92, 255, 0.22));
  border: 1px solid rgba(79, 140, 255, 0.24);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.05em;
}

[data-theme="light"] .msg-avatar {
  color: #2563eb;
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: middle;
  background: var(--copilot-blue);
  animation: cursor-blink 0.8s infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.msg-loading,
.msg-error,
.source-item,
.follow-up-btn,
.sources-toggle {
  border: 1px solid var(--copilot-border);
  background: var(--copilot-card);
  color: var(--copilot-muted);
  backdrop-filter: blur(12px);
}

[data-theme="light"] .msg-loading,
[data-theme="light"] .msg-error,
[data-theme="light"] .source-item,
[data-theme="light"] .follow-up-btn,
[data-theme="light"] .sources-toggle {
  background: rgba(255, 255, 255, 0.78);
}

.msg-loading {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 15px;
  border-radius: 13px;
}

.loading-dots {
  display: flex;
  gap: 4px;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--copilot-blue);
  animation: dot-bounce 1.4s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.38; }
  30% { transform: translateY(-4px); opacity: 1; }
}

.loading-text { font-size: 13px; color: var(--copilot-muted); }
.loading-elapsed { font-size: 12px; color: var(--copilot-dim); font-variant-numeric: tabular-nums; }

.loading-cancel,
.msg-retry {
  border: 1px solid rgba(239, 68, 68, 0.24);
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  cursor: pointer;
}

.loading-cancel {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12px;
}

.msg-error {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 13px;
  border-color: rgba(239, 68, 68, 0.18);
  border-radius: 12px;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.07);
  font-size: 13px;
}

.msg-retry {
  min-height: 26px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12px;
}

.msg-entities,
.msg-sources,
.follow-ups,
.msg-meta {
  margin-top: 10px;
}

.entities-label,
.follow-ups-label {
  display: block;
  margin-bottom: 8px;
  color: var(--copilot-dim);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.entities-list,
.follow-ups-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sources-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 12px;
}

.sources-toggle svg {
  transition: transform 0.18s;
}

.sources-toggle svg.flip {
  transform: rotate(180deg);
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.source-item {
  display: flex;
  gap: 8px;
  padding: 8px 11px;
  border-radius: 10px;
  font-size: 12px;
}

.source-num {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--copilot-blue);
  background: rgba(79, 140, 255, 0.13);
  font-size: 10px;
  font-weight: 850;
}

.msg-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--copilot-dim);
  font-size: 11px;
}

.follow-up-btn {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
}

.follow-up-btn:hover,
.sources-toggle:hover {
  color: var(--copilot-blue);
  border-color: var(--copilot-border-strong);
}

.composer-area {
  width: min(1120px, calc(100% - 48px));
  flex-shrink: 0;
  margin: 0 auto;
  padding: 10px 0 calc(14px + var(--composer-safe-bottom));
  position: relative;
  z-index: 4;
}

.composer-area::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 100vw;
  height: 170px;
  transform: translateX(-50%);
  z-index: -1;
  pointer-events: none;
  background: linear-gradient(to top, rgba(5, 8, 20, 0.96) 0%, rgba(5, 8, 20, 0.72) 50%, transparent 100%);
}

[data-theme="light"] .composer-area::before {
  background: linear-gradient(to top, rgba(246, 248, 252, 0.98) 0%, rgba(246, 248, 252, 0.74) 50%, transparent 100%);
}

.composer-chips {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.composer-wrap {
  border: 1px solid var(--copilot-border);
  border-radius: 20px;
  background: linear-gradient(180deg, var(--copilot-card), rgba(8, 13, 27, 0.86));
  backdrop-filter: blur(18px) saturate(150%);
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.24), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  transition: border-color 0.16s, box-shadow 0.16s, background 0.16s;
}

[data-theme="light"] .composer-wrap {
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.composer-wrap.focus {
  border-color: var(--copilot-border-strong);
  box-shadow: 0 0 0 3px rgba(79, 140, 255, 0.12), 0 20px 48px rgba(79, 140, 255, 0.12);
}

.composer-wrap.has-text {
  border-color: rgba(155, 92, 255, 0.48);
}

.composer-wrap textarea {
  width: 100%;
  min-height: 58px;
  max-height: 180px;
  padding: 15px 16px 2px;
  display: block;
  resize: none;
  border: 0;
  outline: 0;
  color: var(--copilot-text);
  background: transparent;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.composer-wrap textarea::placeholder {
  color: var(--copilot-dim);
}

.composer-footer {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 12px 10px 16px;
}

.char-count {
  color: var(--copilot-dim);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.char-count.warn {
  color: #ef4444;
  font-weight: 750;
}

.send-btn {
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--copilot-dim);
  background: rgba(255, 255, 255, 0.07);
  cursor: pointer;
  transition: transform 0.18s, filter 0.18s, opacity 0.18s, color 0.18s, background 0.18s;
}

.send-btn.active {
  color: #fff;
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
  box-shadow: 0 12px 26px rgba(79, 140, 255, 0.28);
}

.send-btn.active:hover {
  transform: translateY(-1px) scale(1.04);
  filter: brightness(1.08);
}

.send-btn:disabled {
  opacity: 0.44;
  cursor: not-allowed;
}

.send-btn.loading {
  color: #fff;
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
}

.spin-icon {
  animation: spin-anim 1s linear infinite;
}

@keyframes spin-anim {
  to { transform: rotate(360deg); }
}

.composer-hint {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 4px 0;
  color: var(--copilot-dim);
  font-size: 11px;
  line-height: 1.4;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(2, 6, 23, 0.66);
  backdrop-filter: blur(5px);
}

.modal-content {
  width: min(420px, 100%);
  padding: 26px;
  border: 1px solid var(--copilot-border);
  border-radius: 18px;
  color: var(--copilot-text);
  background: var(--copilot-surface-strong);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.34);
}

.modal-content h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 800;
}

.modal-content p {
  margin: 0 0 16px;
  color: var(--copilot-muted);
  font-size: 14px;
  line-height: 1.55;
}

.modal-share-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.modal-share-row input {
  min-width: 0;
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--copilot-border);
  border-radius: 10px;
  color: var(--copilot-text);
  background: rgba(255, 255, 255, 0.06);
}

.modal-share-row button,
.modal-close-btn {
  border: 1px solid var(--copilot-border);
  border-radius: 10px;
  cursor: pointer;
  font-weight: 700;
}

.modal-share-row button {
  padding: 0 15px;
  color: #fff;
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
}

.modal-close-btn {
  width: 100%;
  min-height: 40px;
  color: var(--copilot-muted);
  background: rgba(255, 255, 255, 0.06);
}

@media (max-width: 1180px) {
  .capabilities {
    grid-template-columns: minmax(390px, 1fr) minmax(320px, 380px);
  }
}

@media (max-width: 900px) {
  .welcome-screen {
    justify-content: flex-start;
    width: min(100% - 32px, 760px);
    padding-top: 22px;
  }

  .capabilities {
    grid-template-columns: 1fr;
  }

  .cap-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .cap-main {
    min-height: 150px;
  }
}

@media (max-width: 768px) {
  .copilot-workspace {
    --composer-safe-bottom: 56px;
    height: calc(100dvh - 56px - 56px);
  }

  .sidebar {
    position: fixed;
    top: 56px;
    left: 0;
    bottom: 56px;
  }

  .sidebar-mask {
    display: block;
  }

  .top-bar {
    height: 48px;
    padding: 0 12px;
  }

  .top-status {
    display: none;
  }

  .top-btn {
    min-height: 32px;
    padding: 0 10px;
    font-size: 11px;
  }

  .top-right .top-btn:not(.primary) {
    display: none;
  }

  .content-area {
    padding-bottom: 124px;
  }

  .welcome-screen {
    width: 100%;
    min-height: auto;
    padding: 14px 14px 16px;
    gap: 10px;
  }

  .welcome-hero {
    padding: 0 2px;
  }

  .hero-badge {
    min-height: 24px;
    margin-bottom: 8px;
    padding: 0 13px;
    font-size: 10px;
  }

  .hero-title {
    font-size: 25px;
    line-height: 1.16;
  }

  .hero-desc {
    margin-top: 8px;
    font-size: 13px;
    line-height: 1.45;
  }

  .hero-metrics {
    display: none;
  }

  .capabilities {
    gap: 6px;
  }

  .cap-main {
    min-height: 90px;
    grid-template-columns: 42px 1fr auto;
    gap: 10px;
    padding: 12px;
    border-radius: 15px;
  }

  .cap-main-icon {
    width: 42px;
    height: 42px;
    border-radius: 13px;
  }

  .cap-main-kicker,
  .cap-main-foot {
    display: none;
  }

  .cap-main-title {
    font-size: 16px;
  }

  .cap-main-desc {
    display: -webkit-box;
    overflow: hidden;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    font-size: 12px;
    line-height: 1.4;
  }

  .cap-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .cap-card {
    min-height: 56px;
    padding: 6px 8px;
    border-radius: 13px;
    gap: 4px;
  }

  .cap-icon {
    width: 28px;
    height: 28px;
    border-radius: 10px;
  }

  .cap-icon svg {
    width: 17px;
    height: 17px;
  }

  .cap-title {
    font-size: 11.5px;
  }

  .prompt-chips {
    justify-content: flex-start;
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 8px;
  }

  .chip {
    min-height: 28px;
    padding: 0 11px;
    font-size: 12px;
  }

  .chat-flow {
    width: 100%;
    padding: 16px 12px 148px;
  }

  .msg-bubble {
    max-width: 88%;
    font-size: 13px;
  }

  .msg-row.assistant {
    gap: 8px;
  }

  .msg-avatar {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }

  .composer-area {
    width: 100%;
    padding: 6px 10px calc(8px + var(--composer-safe-bottom));
  }

  .composer-area::before {
    height: 132px;
  }

  .composer-wrap {
    border-radius: 17px;
  }

  .composer-wrap textarea {
    min-height: 42px;
    padding: 11px 14px 0;
    font-size: 16px;
  }

  .composer-footer {
    min-height: 38px;
    padding: 2px 10px 8px 14px;
  }

  .send-btn {
    width: 36px;
    height: 36px;
  }

  .composer-hint {
    font-size: 10px;
    padding-left: 2px;
    padding-right: 2px;
  }
}

@media (max-width: 430px) {
  .top-left {
    gap: 6px;
  }

  .top-btn {
    padding: 0 9px;
  }

  .hero-title {
    font-size: 24px;
  }

  .hero-desc {
    font-size: 12.5px;
  }

  .cap-card {
    min-height: 54px;
  }

  .composer-hint span:last-child {
    max-width: 42%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
