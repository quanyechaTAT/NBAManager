<template>
  <div class="copilot-workspace">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-head">
        <span>历史对话</span>
        <button class="sidebar-close" @click="sidebarOpen = false">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        </button>
      </div>
      <div class="sidebar-body">
        <p v-if="!convs.length" class="sidebar-empty">暂无对话</p>
        <div v-for="c in convs" :key="c.id" class="sidebar-item" :class="{ active: c.id === sid }" @click="loadConv(c)">
          <span class="sidebar-item-title">{{ c.title }}</span>
          <span class="sidebar-item-time">{{ fmtTime(c.time) }}</span>
          <button class="sidebar-item-del" @click.stop="delConv(c.id)">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </button>
        </div>
      </div>
    </aside>
    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

    <!-- 分享弹窗 -->
    <div v-if="showShareModal" class="modal-mask" @click="showShareModal = false">
      <div class="modal-content" @click.stop>
        <h3>分享对话</h3>
        <p>复制下方链接分享给他人：</p>
        <div class="modal-share-row">
          <input :value="shareUrl" readonly @focus="$event.target.select()" />
          <button @click="copyShareUrl">复制</button>
        </div>
        <button class="modal-close-btn" @click="showShareModal = false">关闭</button>
      </div>
    </div>

    <!-- 主区域 -->
    <div class="workspace-main">
      <!-- 顶部导航 -->
      <header class="top-bar">
        <div class="top-left">
          <button class="top-btn" @click="sidebarOpen = !sidebarOpen" title="历史对话">
            <svg viewBox="0 0 24 24" fill="none" width="16" height="16"><path d="M3 12h18M3 6h18M3 18h18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </button>
          <button class="top-btn" @click="newChat">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            新对话
          </button>
        </div>
        <div class="top-right">
          <button class="top-btn" @click="exportChat" v-if="msgs.length">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            导出
          </button>
          <button class="top-btn" @click="shareChat" v-if="msgs.length">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14"><circle cx="18" cy="5" r="3" stroke="currentColor" stroke-width="2"/><circle cx="6" cy="12" r="3" stroke="currentColor" stroke-width="2"/><circle cx="18" cy="19" r="3" stroke="currentColor" stroke-width="2"/><path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" stroke="currentColor" stroke-width="2"/></svg>
            分享
          </button>
        </div>
      </header>

      <!-- 内容区 -->
      <div class="content-area" ref="msgsEl">
        <!-- 欢迎页 -->
        <div v-if="!msgs.length" class="welcome-screen">
          <!-- 标题区 -->
          <div class="welcome-hero">
            <div class="hero-badge">NBA Copilot</div>
            <h1 class="hero-title">你的 NBA 数据分析助手</h1>
            <p class="hero-desc">基于系统知识库、实时赛事数据与联网搜索，为你生成可追溯的篮球分析结论。</p>
            <p class="hero-stat">已连接 {{ docCount.toLocaleString() }} 条知识片段</p>
          </div>

          <!-- 能力卡片区 -->
          <div class="capabilities">
            <!-- 左侧主推荐卡 -->
            <button class="cap-main" @click="send('分析今天所有比赛的胜负关键因素和看点')">
              <div class="cap-main-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="28" height="28"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
              </div>
              <div class="cap-main-text">
                <span class="cap-main-title">今日赛事分析</span>
                <span class="cap-main-desc">分析今日比赛的胜负关键、核心对位、伤病影响和战术看点。</span>
              </div>
              <svg viewBox="0 0 24 24" fill="none" width="20" height="20" class="cap-main-arrow"><path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>

            <!-- 右侧快捷能力卡 -->
            <div class="cap-grid">
              <button class="cap-card" @click="send('对比勒布朗·詹姆斯和凯文·杜兰特本赛季的数据表现')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                </div>
                <span class="cap-title">球员对比</span>
              </button>

              <button class="cap-card" @click="send('雷霆队本赛季的战绩和核心球员表现如何？')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
                </div>
                <span class="cap-title">球队查询</span>
              </button>

              <button class="cap-card" @click="send('解释上一场总决赛的技术统计和关键数据')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                </div>
                <span class="cap-title">数据解读</span>
              </button>

              <button class="cap-card" @click="send('生成本赛季季后赛的赛前看点和预测分析')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>
                </div>
                <span class="cap-title">季后赛预测</span>
              </button>

              <button class="cap-card" @click="send('本赛季三分命中率最高的球员有哪些？')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
                </div>
                <span class="cap-title">数据排行</span>
              </button>

              <button class="cap-card" @click="send('谁是本赛季的MVP热门候选人？分析他们的数据表现')">
                <div class="cap-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                </div>
                <span class="cap-title">MVP 分析</span>
              </button>
            </div>
          </div>

          <!-- 快捷 Prompt -->
          <div class="prompt-chips">
            <button class="chip" @click="send('今天有哪些比赛？')">今天有哪些比赛？</button>
            <button class="chip" @click="send('分析湖人最近5场比赛的表现')">分析湖人最近 5 场表现</button>
            <button class="chip" @click="send('库里和东契奇谁的三分更准？')">库里和东契奇谁的三分更准？</button>
            <button class="chip" @click="send('本赛季助攻榜前五是谁？')">本赛季助攻榜 Top 5</button>
          </div>
        </div>

        <!-- 对话流 -->
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
                  <button class="loading-cancel" @click="cancelQuery">取消</button>
                </div>
                <div v-if="m.content" class="msg-bubble ai" v-html="render(m, i)"></div>
                <div v-if="m.relatedEntities?.length" class="msg-entities">
                  <span class="entities-label">相关链接</span>
                  <div class="entities-list">
                    <RelatedEntityCard v-for="entity in m.relatedEntities" :key="`${entity.type}-${entity.id}`" :entity="entity" />
                  </div>
                </div>
                <div v-if="m.error" class="msg-error">
                  <svg viewBox="0 0 24 24" fill="none" width="14" height="14"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><path d="M12 8v4M12 16h.01"/></svg>
                  <span>{{ m.error }}</span>
                  <button class="msg-retry" @click="retryLast">重试</button>
                </div>
                <div v-if="m.sources?.length" class="msg-sources">
                  <button class="sources-toggle" @click="m.showSources = !m.showSources">
                    <svg viewBox="0 0 24 24" fill="none" width="12" height="12" :class="{ flip: m.showSources }"><path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    {{ m.sources.length }} 个参考来源
                  </button>
                  <div v-if="m.showSources" class="sources-list">
                    <div v-for="(s, si) in m.sources" :key="si" class="source-item">
                      <span class="source-num">{{ si + 1 }}</span><span>{{ s }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="m.responseTime" class="msg-meta">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                  {{ (m.responseTime / 1000).toFixed(1) }}s
                </div>
                <div v-if="m.followUps?.length && i === msgs.length - 1 && !loading" class="follow-ups">
                  <span class="follow-ups-label">继续问：</span>
                  <div class="follow-ups-list">
                    <button v-for="(q, qi) in m.followUps" :key="qi" class="follow-up-btn" @click="send(q)">{{ q }}</button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="composer-area">
        <!-- 快捷 Prompt（对话模式下也显示） -->
        <div class="composer-chips" v-if="msgs.length">
          <button class="chip-sm" @click="send('详细解释一下')">详细解释</button>
          <button class="chip-sm" @click="send('对比其他球员')">对比其他球员</button>
          <button class="chip-sm" @click="send('给出数据来源')">数据来源</button>
        </div>
        <div class="composer-wrap" :class="{ focus: composerFocused, 'has-text': canSend }">
          <textarea
            ref="inputEl"
            v-model="question"
            placeholder="询问任何 NBA 数据、球员、球队、赛程或战术问题..."
            rows="2"
            @keydown="handleKeydown"
            @input="autoGrow"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            :disabled="loading"
            maxlength="500"
          ></textarea>
          <div class="composer-footer">
            <span class="char-count" :class="{ warn: question.length > 450 }">{{ question.length }}/500</span>
            <button class="send-btn" :class="{ active: canSend, loading }" @click="send()" :disabled="!canSend">
              <svg v-if="!loading" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M22 2L11 13M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <svg v-else class="spin-icon" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" opacity=".3"/>
                <path d="M12 2C6.48 2 2 6.48 2 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </div>
        <div class="composer-hint">
          <span>Enter 发送 · Ctrl+Enter 换行</span>
          <span v-if="docCount > 0">已索引 {{ docCount.toLocaleString() }} 条</span>
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
  elapsed.value = 0; statusText.value = '正在思考...'
}

function cancelQuery(): void {
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  clearTimers(); loading.value = false
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
  if (d < 3600000) return `${Math.floor(d / 60000)}分钟前`
  if (d < 86400000) return `${Math.floor(d / 3600000)}小时前`
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
  const a = document.createElement('a'); a.href = url; a.download = `NBA对话_${new Date().toISOString().slice(0, 10)}.md`; a.click()
  URL.revokeObjectURL(url)
}

const shareUrl = ref('')
const showShareModal = ref(false)
async function shareChat(): Promise<void> {
  if (!msgs.value.length) return
  await saveConv()
  try {
    const { data } = await request.post(`/rag/conversations/${sid.value}/share`)
    if (data?.shareId) { shareUrl.value = `${window.location.origin}/smart-search?share=${data.shareId}`; showShareModal.value = true; await navigator.clipboard.writeText(shareUrl.value) }
  } catch { /* ignore */ }
}
function copyShareUrl(): void { navigator.clipboard.writeText(shareUrl.value) }

function generateFollowUps(content: string): string[] {
  const lower = content.toLowerCase()
  const s: string[] = []
  if (lower.includes('球员') || lower.includes('得分')) { s.push('对比这名球员和其他顶级球员'); s.push('查看生涯趋势') }
  if (lower.includes('球队') || lower.includes('战绩')) { s.push('季后赛前景分析'); s.push('对比同分区对手') }
  if (lower.includes('比赛') || lower.includes('总决赛')) { s.push('关键转折点分析'); s.push('双方技术统计') }
  if (!s.length) { s.push('详细解释一下'); s.push('相关数据还有哪些？') }
  return s.slice(0, 3)
}

async function send(text?: string): Promise<void> {
  const content = (text || question.value.trim()).slice(0, 500)
  if (!content || loading.value) return
  loading.value = true
  msgs.value.push({ role: 'user', content }); question.value = ''
  const ai = msgs.value.length; msgs.value.push({ role: 'assistant', content: '' }); scroll()

  statusText.value = '正在检索相关数据...'
  elapsed.value = 0
  elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)
  statusTimer = setTimeout(() => { statusText.value = '正在生成回答...' }, 3000)

  const abortController = new AbortController(); currentAbort = abortController
  const timeout = content.length < 50 ? 30000 : content.length < 200 ? 60000 : 90000
  const timeoutId = setTimeout(() => {
    if (loading.value) { abortController.abort(); msgs.value[ai].error = '请求超时，请稍后重试'; msgs.value[ai].content = ''; loading.value = false; currentAbort = null; clearTimers() }
  }, timeout)

  try {
    const token = getToken() || ''
    const response = await fetch(`/api/rag/stream?question=${encodeURIComponent(content)}&top_k=5&session_id=${sid.value}`, {
      headers: { 'Authorization': `Bearer ${token}` }, signal: abortController.signal,
    })

    if (response.status === 401 || response.status === 403) {
      msgs.value[ai].error = '登录已过期，请重新登录'; msgs.value[ai].content = ''
      loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers()
      clearAuthStorage(); setTimeout(() => { window.location.href = '/login' }, 1500); return
    }
    if (!response.ok) {
      msgs.value[ai].error = `服务异常 (${response.status})`; msgs.value[ai].content = ''
      loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers(); return
    }

    const reader = response.body!.getReader(); const decoder = new TextDecoder(); let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n'); buffer = lines.pop() || ''
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
              loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers(); saveConv()
            }
            else if (d.type === 'error') { msgs.value[ai].error = d.error || '服务暂时不可用'; msgs.value[ai].content = ''; loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers() }
          } catch { /* ignore */ }
        }
      }
    }
    if (loading.value) { msgs.value[ai].followUps = generateFollowUps(msgs.value[ai].content); loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers(); saveConv() }
  } catch (err: any) {
    if (err.name !== 'AbortError') { msgs.value[ai].error = '网络连接失败，请检查网络后重试'; msgs.value[ai].content = '' }
    loading.value = false; currentAbort = null; clearTimeout(timeoutId); clearTimers()
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
/* ===== NBA Copilot 主题变量 ===== */
.copilot-workspace {
  --copilot-bg: #050816;
  --copilot-bg-2: #0b1020;
  --copilot-panel: rgba(15, 23, 42, 0.78);
  --copilot-panel-strong: rgba(17, 24, 39, 0.92);
  --copilot-border: rgba(148, 163, 184, 0.18);
  --copilot-border-strong: rgba(129, 140, 248, 0.36);
  --copilot-text: #f8fafc;
  --copilot-muted: #94a3b8;
  --copilot-dim: #64748b;
  --copilot-blue: #3b82f6;
  --copilot-purple: #8b5cf6;
  --copilot-cyan: #22d3ee;
  --copilot-orange: #f97316;
  --copilot-green: #22c55e;
}

/* ===== 浅色主题 ===== */
[data-theme="light"] .copilot-workspace {
  --copilot-bg: #f6f8fc;
  --copilot-bg-2: #eef2ff;
  --copilot-panel: rgba(255, 255, 255, 0.86);
  --copilot-panel-strong: rgba(255, 255, 255, 0.96);
  --copilot-border: rgba(148, 163, 184, 0.28);
  --copilot-border-strong: rgba(59, 130, 246, 0.28);
  --copilot-text: #0f172a;
  --copilot-muted: #64748b;
  --copilot-dim: #94a3b8;
}

/* ===== 全屏工作台 ===== */
.copilot-workspace {
  display: flex;
  height: calc(100vh - 56px);
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(59, 130, 246, .22), transparent),
    radial-gradient(ellipse 60% 50% at 80% 15%, rgba(249, 115, 22, .14), transparent),
    radial-gradient(ellipse 50% 40% at 50% 85%, rgba(139, 92, 246, .10), transparent),
    linear-gradient(180deg, #060c1e 0%, #0a1628 40%, #0d1a30 100%);
  color: var(--copilot-text);
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
}

/* 浅色主题背景覆盖 — 禁止深色渐变 */
[data-theme="light"] .copilot-workspace {
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(59, 130, 246, .08), transparent),
    radial-gradient(ellipse 60% 50% at 80% 15%, rgba(249, 115, 22, .06), transparent),
    radial-gradient(ellipse 50% 40% at 50% 85%, rgba(139, 92, 246, .05), transparent),
    linear-gradient(180deg, #f8fafc 0%, #f0f4ff 50%, #eef2ff 100%) !important;
}

/* 球场线条纹理 */
.copilot-workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(148, 163, 184, 0.04) 1px, transparent 1px),
    linear-gradient(rgba(148, 163, 184, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  pointer-events: none;
  z-index: 0;
}
[data-theme="light"] .copilot-workspace::before {
  background:
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 60px 60px;
}

@media (max-width: 768px) { .copilot-workspace { height: calc(100vh - 56px - 56px); } }

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px; flex-shrink: 0;
  background: var(--copilot-panel-strong);
  border-right: 1px solid var(--copilot-border);
  display: flex; flex-direction: column;
  transition: margin-left 0.2s ease; margin-left: -260px;
  z-index: 10; position: relative;
}
.sidebar.open { margin-left: 0; }
.sidebar-mask { display: none; }
.sidebar-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid var(--copilot-border);
  font-size: 14px; font-weight: 600; color: var(--copilot-text);
}
.sidebar-close { background: none; border: none; color: var(--copilot-muted); cursor: pointer; padding: 6px; border-radius: 6px; }
.sidebar-close:hover { background: rgba(255,255,255,0.06); color: var(--copilot-text); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 8px; }
.sidebar-empty { text-align: center; color: var(--copilot-muted); padding: 32px 0; font-size: 13px; }
.sidebar-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; border-radius: 8px; cursor: pointer;
  transition: all 0.12s; margin-bottom: 2px;
}
.sidebar-item:hover { background: rgba(255,255,255,0.06); }
.sidebar-item.active {
  background: rgba(59, 130, 246, 0.12);
  border-left: 3px solid var(--copilot-blue);
}
.sidebar-item-title {
  flex: 1; font-size: 13px; color: var(--copilot-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sidebar-item-time { font-size: 11px; color: var(--copilot-muted); white-space: nowrap; }
.sidebar-item-del {
  background: none; border: none; color: var(--copilot-muted); cursor: pointer;
  padding: 4px; opacity: 0; transition: all 0.12s; border-radius: 4px;
}
.sidebar-item-del:hover { background: rgba(239,68,68,0.15); color: #ef4444; }
.sidebar-item:hover .sidebar-item-del { opacity: 1; }

/* ===== 主区域 ===== */
.workspace-main {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  position: relative; z-index: 1;
}

/* ===== 顶部导航 ===== */
.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px; height: 52px; flex-shrink: 0;
  background: var(--copilot-panel);
  backdrop-filter: blur(16px) saturate(180%);
  border-bottom: 1px solid var(--copilot-border);
  position: relative;
  z-index: 2;
}
.top-bar::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--copilot-border-strong), transparent);
}
[data-theme="light"] .top-bar {
  background: rgba(255,255,255,0.72);
}
.top-left, .top-right { display: flex; gap: 8px; align-items: center; }
.top-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--copilot-border); border-radius: 8px;
  color: var(--copilot-muted); font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all 0.15s; white-space: nowrap;
}
.top-btn:hover {
  background: rgba(255,255,255,0.10);
  color: var(--copilot-text);
  border-color: var(--copilot-border-strong);
}

/* ===== 内容区 ===== */
.content-area {
  flex: 1; overflow-y: auto; overflow-x: hidden;
  position: relative;
  -webkit-overflow-scrolling: touch;
}

/* 浅色主题 — 卡片/按钮/输入框覆盖 */
[data-theme="light"] .cap-main,
[data-theme="light"] .cap-card {
  background: rgba(255,255,255,0.80);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
[data-theme="light"] .cap-main:hover,
[data-theme="light"] .cap-card:hover {
  border-color: rgba(59, 130, 246, 0.30);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.08);
}
[data-theme="light"] .chip {
  background: rgba(255,255,255,0.70);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .chip:hover {
  background: rgba(59, 130, 246, 0.06);
  border-color: rgba(59, 130, 246, 0.30);
}
[data-theme="light"] .msg-bubble.ai {
  background: rgba(255,255,255,0.80);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .composer-wrap {
  background: rgba(255,255,255,0.80);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .composer-wrap.focus {
  border-color: var(--copilot-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.10), 0 4px 16px rgba(0,0,0,0.06);
}
[data-theme="light"] .sidebar {
  background: rgba(255,255,255,0.92);
  border-right-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .modal-content {
  background: rgba(255,255,255,0.95);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .msg-loading {
  background: rgba(255,255,255,0.80);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .msg-error {
  background: rgba(239,68,68,0.04);
  border-color: rgba(239,68,68,0.15);
}
[data-theme="light"] .source-item {
  background: rgba(255,255,255,0.80);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .follow-up-btn {
  background: rgba(255,255,255,0.70);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .follow-up-btn:hover {
  background: rgba(59, 130, 246, 0.06);
  border-color: rgba(59, 130, 246, 0.30);
}
[data-theme="light"] .chip-sm {
  background: rgba(255,255,255,0.60);
  border-color: rgba(148, 163, 184, 0.22);
}
[data-theme="light"] .sources-toggle {
  background: rgba(255,255,255,0.70);
  border-color: rgba(148, 163, 184, 0.22);
}

/* ===== 欢迎页 ===== */
.welcome-screen {
  display: flex; flex-direction: column; align-items: center;
  padding: 48px 24px 40px; min-height: 100%;
  max-width: 1140px; margin: 0 auto; width: 100%;
  box-sizing: border-box;
}

/* 标题区 */
.welcome-hero { text-align: center; margin-bottom: 32px; }
.hero-badge {
  display: inline-block; padding: 5px 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15));
  color: var(--copilot-blue);
  border: 1px solid rgba(59, 130, 246, 0.25);
  border-radius: 20px; font-size: 11px; font-weight: 700;
  letter-spacing: 1.5px; text-transform: uppercase;
  margin-bottom: 16px;
}
.hero-title {
  margin: 0 0 12px; font-size: 34px; font-weight: 800;
  font-family: var(--font-heading);
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #f8fafc 0%, var(--copilot-blue) 50%, var(--copilot-purple) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.2;
}
[data-theme="light"] .hero-title {
  background: linear-gradient(135deg, #0f172a 0%, var(--copilot-blue) 50%, var(--copilot-purple) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.hero-desc {
  margin: 0 0 10px; font-size: 15px; color: var(--copilot-muted);
  line-height: 1.6; max-width: 560px; margin-left: auto; margin-right: auto;
}
.hero-stat {
  font-size: 12px; color: var(--copilot-green);
  padding: 5px 14px;
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.2);
  border-radius: 16px;
  display: inline-block; margin-top: 10px;
}

/* 能力卡片区 */
.capabilities {
  display: flex; gap: 16px; width: 100%; margin-bottom: 24px;
  align-items: stretch;
}
.cap-main {
  flex: 1; display: flex; align-items: center; gap: 18px;
  padding: 20px 24px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border); border-radius: 16px;
  cursor: pointer; transition: all 0.2s ease; text-align: left;
  backdrop-filter: blur(8px);
  min-height: 160px; max-height: 180px;
}
.cap-main:hover {
  border-color: var(--copilot-border-strong);
  box-shadow: 0 8px 32px rgba(59, 130, 246, 0.12);
  transform: translateY(-2px);
}
.cap-main-icon {
  width: 52px; height: 52px; border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.18), rgba(139, 92, 246, 0.18));
  color: var(--copilot-blue);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.cap-main-text { flex: 1; }
.cap-main-title { display: block; font-size: 16px; font-weight: 700; color: var(--copilot-text); margin-bottom: 6px; }
.cap-main-desc { font-size: 13px; color: var(--copilot-muted); line-height: 1.6; }
.cap-main-arrow { color: var(--copilot-dim); flex-shrink: 0; transition: transform 0.2s; }
.cap-main:hover .cap-main-arrow { transform: translateX(4px); color: var(--copilot-blue); }

.cap-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); grid-template-rows: repeat(3, 1fr);
  gap: 10px; width: 340px; flex-shrink: 0;
}
.cap-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  padding: 12px 8px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border); border-radius: 12px;
  cursor: pointer; transition: all 0.2s ease;
  min-height: 88px; max-height: 110px;
}
.cap-card:hover {
  border-color: var(--copilot-border-strong);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}
.cap-icon {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(139, 92, 246, 0.12));
  color: var(--copilot-blue);
  display: flex; align-items: center; justify-content: center;
}
.cap-title { font-size: 12px; font-weight: 600; color: var(--copilot-text); text-align: center; }

/* 快捷 Prompt */
.prompt-chips {
  display: flex; flex-wrap: wrap; gap: 10px; justify-content: center;
  margin-top: 8px;
}
.chip {
  padding: 8px 18px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border); border-radius: 20px;
  color: var(--copilot-muted); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.15s;
  backdrop-filter: blur(4px);
  white-space: nowrap;
}
.chip:hover {
  border-color: var(--copilot-border-strong);
  color: var(--copilot-blue);
  background: rgba(59, 130, 246, 0.08);
}

/* ===== 对话流 ===== */
.chat-flow {
  padding: 20px 16px 160px; max-width: 980px; margin: 0 auto; width: 100%;
  box-sizing: border-box;
}
.msg-row { display: flex; margin-bottom: 20px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { gap: 12px; }
.msg-bubble {
  padding: 12px 16px; border-radius: 16px; font-size: 14px; line-height: 1.7;
  max-width: 78%; word-break: break-word;
}
.msg-bubble.user {
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
  color: #fff; border-radius: 16px 16px 4px 16px;
}
.msg-bubble.ai {
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border);
  color: var(--copilot-text);
  backdrop-filter: blur(8px);
}
.msg-bubble.ai :deep(b) { color: var(--copilot-blue); font-weight: 600; }
.msg-avatar {
  flex-shrink: 0; width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15));
  border: 1px solid var(--copilot-border);
  border-radius: 8px; color: var(--copilot-blue);
  font-size: 10px; font-weight: 700; margin-top: 2px;
}
.msg-body { flex: 1; min-width: 0; }
.typing-cursor { display: inline-block; width: 2px; height: 14px; background: var(--copilot-blue); margin-left: 2px; vertical-align: middle; animation: cursor-blink 0.8s infinite; }
@keyframes cursor-blink { 0%,100%{opacity:1} 50%{opacity:0} }

.msg-entities { margin-top: 12px; }
.entities-label { display: block; font-size: 11px; font-weight: 600; color: var(--copilot-dim); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
.entities-list { display: flex; flex-wrap: wrap; gap: 8px; }

.msg-loading {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border);
  border-radius: 12px;
}
.loading-dots { display: flex; gap: 4px; }
.loading-dots span { width: 6px; height: 6px; background: var(--copilot-blue); border-radius: 50%; animation: dot-bounce 1.4s infinite; }
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce { 0%,60%,100%{transform:translateY(0);opacity:0.4} 30%{transform:translateY(-4px);opacity:1} }
.loading-text { font-size: 13px; color: var(--copilot-muted); }
.loading-elapsed { font-size: 12px; color: var(--copilot-dim); font-variant-numeric: tabular-nums; }
.loading-cancel {
  padding: 5px 12px;
  background: rgba(239,68,68,0.08);
  border: 1px solid rgba(239,68,68,0.2);
  border-radius: 6px; color: var(--copilot-muted); font-size: 12px; cursor: pointer;
}
.loading-cancel:hover { background: rgba(239,68,68,0.15); color: #ef4444; border-color: rgba(239,68,68,0.4); }

.msg-error {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  background: rgba(239,68,68,0.06);
  border: 1px solid rgba(239,68,68,0.15);
  border-radius: 10px; color: #ef4444; font-size: 13px;
}
.msg-error svg { flex-shrink: 0; }
.msg-retry {
  padding: 4px 12px;
  background: rgba(239,68,68,0.15);
  border: 1px solid rgba(239,68,68,0.3);
  border-radius: 6px; color: #ef4444; font-size: 12px; cursor: pointer; margin-left: 4px;
}
.msg-retry:hover { background: rgba(239,68,68,0.25); }

.msg-sources { margin-top: 10px; }
.sources-toggle {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 12px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border);
  border-radius: 8px; color: var(--copilot-muted); font-size: 12px; cursor: pointer;
}
.sources-toggle:hover { background: rgba(255,255,255,0.06); color: var(--copilot-text); }
.sources-toggle svg { transition: transform 0.2s; }
.sources-toggle svg.flip { transform: rotate(180deg); }
.sources-list { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
.source-item {
  display: flex; gap: 8px; padding: 8px 12px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border);
  border-radius: 8px; font-size: 12px; color: var(--copilot-muted);
}
.source-num {
  flex-shrink: 0; width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(59, 130, 246, 0.12);
  color: var(--copilot-blue); border-radius: 5px;
  font-size: 10px; font-weight: 700;
}

.msg-meta { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--copilot-dim); margin-top: 8px; }
.msg-meta svg { opacity: 0.5; }

.follow-ups { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--copilot-border); }
.follow-ups-label { display: block; font-size: 12px; color: var(--copilot-muted); margin-bottom: 10px; }
.follow-ups-list { display: flex; flex-wrap: wrap; gap: 8px; }
.follow-up-btn {
  padding: 7px 14px;
  background: var(--copilot-panel);
  border: 1px solid var(--copilot-border);
  border-radius: 18px; color: var(--copilot-muted); font-size: 12px;
  cursor: pointer; transition: all 0.15s;
}
.follow-up-btn:hover {
  border-color: var(--copilot-border-strong);
  color: var(--copilot-blue);
  background: rgba(59, 130, 246, 0.08);
}

/* ===== 底部输入区 ===== */
.composer-area {
  flex-shrink: 0; padding: 12px 20px 20px;
  background: linear-gradient(to top, var(--copilot-bg) 60%, transparent);
  max-width: 1120px; margin: 0 auto; width: 100%;
  position: relative; z-index: 2;
  box-sizing: border-box;
}
.composer-chips { display: flex; gap: 8px; margin-bottom: 10px; overflow-x: auto; }
.chip-sm {
  padding: 5px 12px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--copilot-border);
  border-radius: 16px;
  color: var(--copilot-dim); font-size: 11px; cursor: pointer;
  transition: all 0.15s; white-space: nowrap;
}
.chip-sm:hover {
  border-color: var(--copilot-border-strong);
  color: var(--copilot-blue);
}
.composer-wrap {
  display: flex; flex-direction: column;
  border: 1.5px solid var(--copilot-border);
  border-radius: 18px;
  background: var(--copilot-panel);
  backdrop-filter: blur(12px);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer-wrap.focus {
  border-color: var(--copilot-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15), 0 8px 32px rgba(0,0,0,0.2);
}
.composer-wrap.has-text { border-color: var(--copilot-purple); }
.composer-wrap textarea {
  width: 100%; min-height: 48px; max-height: 180px;
  padding: 14px 16px 0; background: none; border: none; outline: none;
  color: var(--copilot-text); font-size: 14px; font-family: var(--font-body);
  line-height: 1.5; resize: none;
}
.composer-wrap textarea::placeholder { color: var(--copilot-dim); }
.composer-footer { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px 10px; }
.char-count { font-size: 11px; color: var(--copilot-dim); }
.char-count.warn { color: #ef4444; font-weight: 600; }
.send-btn {
  width: 40px; height: 40px; display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 50%; cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(255,255,255,0.06); color: var(--copilot-dim);
}
.send-btn.active {
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
  color: #fff; box-shadow: 0 4px 16px rgba(59, 130, 246, 0.3);
}
.send-btn.active:hover { filter: brightness(1.1); transform: scale(1.08); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.send-btn.loading { background: var(--copilot-blue); color: #fff; opacity: 0.7; }
.spin-icon { animation: spin-anim 1s linear infinite; }
@keyframes spin-anim { to { transform: rotate(360deg); } }
.composer-hint { display: flex; justify-content: space-between; font-size: 11px; color: var(--copilot-dim); padding: 6px 4px 0; }

/* ===== 分享弹窗 ===== */
.modal-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; z-index: 200;
}
.modal-content {
  background: var(--copilot-panel-strong);
  border: 1px solid var(--copilot-border);
  border-radius: 16px; padding: 28px; max-width: 420px; width: 90%;
  backdrop-filter: blur(12px);
}
.modal-content h3 { margin: 0 0 10px; font-size: 18px; font-weight: 700; color: var(--copilot-text); }
.modal-content p { margin: 0 0 16px; font-size: 14px; color: var(--copilot-muted); }
.modal-share-row { display: flex; gap: 8px; margin-bottom: 16px; }
.modal-share-row input {
  flex: 1; padding: 10px 12px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--copilot-border);
  border-radius: 8px; color: var(--copilot-text); font-size: 13px;
}
.modal-share-row button {
  padding: 10px 16px;
  background: linear-gradient(135deg, var(--copilot-blue), var(--copilot-purple));
  border: none; border-radius: 8px; color: #fff; font-weight: 600; cursor: pointer;
}
.modal-close-btn {
  width: 100%; padding: 10px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--copilot-border);
  border-radius: 8px; color: var(--copilot-muted); cursor: pointer;
}

/* ===== 响应式 ===== */
@media (max-width: 1200px) {
  .cap-grid { width: 300px; }
}

@media (max-width: 900px) {
  .capabilities { flex-direction: column; gap: 12px; }
  .cap-grid { width: 100%; grid-template-columns: repeat(2, 1fr); }
  .cap-main { padding: 18px; min-height: 140px; }
}

@media (max-width: 768px) {
  .copilot-workspace { height: calc(100vh - 56px - 56px); }
  .sidebar { position: fixed; top: 56px; left: 0; bottom: 56px; z-index: 100; width: 260px; }
  .sidebar-mask { display: block; position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 99; }
  .capabilities { flex-direction: column; gap: 10px; }
  .cap-grid { width: 100%; grid-template-columns: repeat(2, 1fr); gap: 8px; }
  .cap-card { min-height: 80px; padding: 10px 6px; }
  .cap-main { min-height: 120px; padding: 16px; }
  .hero-title { font-size: 24px; }
  .hero-desc { font-size: 13px; }
  .msg-bubble { max-width: 92%; font-size: 13px; }
  .top-bar { padding: 0 12px; height: 48px; }
  .top-btn { padding: 5px 10px; font-size: 11px; }
  .composer-area { padding: 8px 12px 68px; }
  .composer-wrap textarea { font-size: 16px; }
  .welcome-screen { padding: 24px 12px 40px; }
  .chat-flow { padding: 16px 12px 140px; }
}

@media (max-width: 480px) {
  .cap-grid { grid-template-columns: repeat(2, 1fr); }
  .prompt-chips { gap: 6px; }
  .chip { padding: 6px 12px; font-size: 12px; }
  .hero-title { font-size: 22px; }
  .capabilities { gap: 8px; }
  .composer-area { padding: 6px 8px 64px; }
}
</style>
