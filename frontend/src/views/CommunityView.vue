<template>
  <div class="community-page">
    <section class="community-hero">
      <div class="hero-copy">
        <span class="hero-kicker">COURTSIDE FAN LOUNGE</span>
        <h1>球迷热血看台</h1>
        <p>聊球星、辩战术、预测季后赛，汇聚联盟最硬核的篮球声浪与球迷阵地</p>
        <div class="hero-stats" aria-label="看台数据摘要">
          <div><strong>{{ compactNumber(visiblePosts.length) }}</strong><span>本页讨论</span></div>
          <div><strong>{{ compactNumber(pageCommentCount) }}</strong><span>热议回帖</span></div>
          <div><strong>{{ compactNumber(pageInteractionCount) }}</strong><span>球迷互动</span></div>
        </div>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <span class="court-line court-line--one"></span>
        <span class="court-line court-line--two"></span>
        <div class="logo-orbit">
          <img src="/images/team-logos/lal.png" alt="" />
          <img src="/images/team-logos/bos.png" alt="" />
          <img src="/images/team-logos/gs.png" alt="" />
        </div>
      </div>
      <el-button class="hero-create" type="primary" aria-label="发布新帖子" @click="openCreate">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
        登台发帖
      </el-button>
    </section>

    <div class="community-toolbar">
      <el-tabs v-model="activeCategory" class="community-tabs" @tab-change="onCategoryChange">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="讨论" name="DISCUSSION" />
        <el-tab-pane label="新闻" name="NEWS" />
        <el-tab-pane label="辩论" name="DEBATE" />
        <el-tab-pane label="预测" name="PREDICTION" />
      </el-tabs>
      <div class="toolbar-actions">
        <span v-if="activeTopic" class="topic-filter">
          # {{ activeTopic }}
          <button title="清除话题筛选" @click="clearTopic">×</button>
        </span>
        <el-select v-model="sortMode" class="sort-select" aria-label="帖子排序" @change="onSortChange">
          <el-option label="最新发布" value="latest" />
          <el-option label="最多浏览" value="hot" />
          <el-option label="最多讨论" value="discussed" />
        </el-select>
      </div>
    </div>

    <div class="community-layout">
      <main class="community-main" v-loading="loading">
        <article v-for="post in visiblePosts" :key="post.id" class="post-card" tabindex="0" @click="goToPost(post.id)" @keydown.enter="goToPost(post.id)">
          <div class="post-author-block">
            <span class="author-avatar" v-if="post.avatarUrl"><img :src="post.avatarUrl" alt="" @error="(e: Event) => { (e.target as HTMLImageElement).style.display='none'; (e.target as HTMLImageElement).nextElementSibling?.setAttribute('style', 'display:grid') }" /><span style="display:none">{{ userInitial(post.username) }}</span></span>
            <span class="author-avatar" v-else>{{ userInitial(post.username) }}</span>
            <div><strong>{{ post.username || '社区用户' }}</strong><span>{{ formatTime(post.createTime) }}</span></div>
          </div>
          <div class="post-content">
            <div class="post-labels">
              <span class="post-category-tag" :class="categoryClass(post.category)">{{ categoryLabel(post.category) }}</span>
              <span v-if="post.isTop" class="status-tag status-tag--top">置顶</span>
              <span v-if="post.isFeatured" class="status-tag status-tag--featured">精华</span>
              <span v-if="isHot(post)" class="status-tag status-tag--hot">热门</span>
            </div>
            <h2>{{ post.title }}</h2>
            <p>{{ excerpt(post.content) }}</p>
            <div v-if="postTags(post).length" class="post-tags">
              <button v-for="tag in postTags(post).slice(0, 3)" :key="tag" @click.stop="selectTopic(tag)">#{{ tag }}</button>
            </div>
            <div class="post-signals">
              <span><svg viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z"/><circle cx="12" cy="12" r="3"/></svg>{{ post.viewCount }}</span>
              <span><svg viewBox="0 0 24 24"><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3M7 22V11l4-9a3 3 0 0 1 3 3v4h5.28a2 2 0 0 1 2 2.3l-1.38 9a2 2 0 0 1-2 1.7Z"/></svg>{{ post.likeCount }}</span>
              <span><svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z"/></svg>{{ post.commentCount }}</span>
              <span><svg viewBox="0 0 24 24"><path d="m12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01Z"/></svg>{{ post.favoriteCount ?? 0 }}</span>
              <time>{{ formatTime(post.lastReplyTime || post.createTime) }}活跃</time>
            </div>
          </div>
        </article>
        <AppEmpty v-if="!loading && visiblePosts.length === 0" :description="activeTopic ? '当前页面没有该话题的帖子' : '暂无帖子'" />
        <div v-if="total > 0" class="pager">
          <el-pagination background layout="prev, pager, next" :total="total" v-model:current-page="page" :page-size="10" @current-change="loadPosts" />
        </div>
      </main>

      <aside class="community-sidebar">
        <section class="sidebar-card hot-ranking">
          <header><svg viewBox="0 0 24 24"><path d="M13 2 3 14h9l-1 8 10-12h-9Z"/></svg><span>热帖排行</span></header>
          <button v-for="(post, index) in visibleHotPosts.slice(0, 5)" :key="post.id" class="ranking-item" @click="goToPost(post.id)">
            <span class="rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
            <span class="ranking-copy"><strong>{{ post.title }}</strong><small>{{ post.viewCount }} 浏览 · {{ post.commentCount }} 讨论</small></span>
          </button>
          <AppEmpty v-if="visibleHotPosts.length === 0" description="暂无热帖" size="sm" />
        </section>

        <section class="sidebar-card topic-card">
          <header><svg viewBox="0 0 24 24"><path d="M20.59 13.41 11 3.83V3H4v7h.83l9.58 9.59a2 2 0 0 0 2.82 0l3.36-3.36a2 2 0 0 0 0-2.82Z"/><circle cx="7.5" cy="6.5" r="1"/></svg><span>热门话题</span></header>
          <div v-if="popularTopics.length" class="topic-cloud">
            <button v-for="topic in popularTopics" :key="topic.name" :class="{ active: activeTopic === topic.name }" @click="toggleTopic(topic.name)">
              <span># {{ topic.name }}</span><small>{{ topic.postCount }} 篇</small>
            </button>
          </div>
          <p v-else class="sidebar-empty">帖子添加标签后将在这里聚合</p>
        </section>

        <section class="sidebar-card rules-card">
          <header><svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg><span>看台公约与球友指南</span></header>
          <div class="rules-list">
            <div class="rule-item">
              <span class="rule-dot">🏀</span>
              <p><strong>理性评球</strong>：以客观战术与数据为依归，尊重球星表现与多元立场</p>
            </div>
            <div class="rule-item">
              <span class="rule-dot">🔥</span>
              <p><strong>战术辩论</strong>：鼓励深度攻防战术剖析、阵容轮换与高阶数据推演</p>
            </div>
            <div class="rule-item">
              <span class="rule-dot">⚡</span>
              <p><strong>文明看台</strong>：杜绝人身攻击与恶意刷屏，共同维护硬核纯粹看台氛围</p>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <el-dialog v-model="createVisible" title="发布帖子" width="600px" destroy-on-close class="dialog-light" append-to-body center>
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入帖子标题" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="form.category" style="width:100%"><el-option label="讨论" value="DISCUSSION"/><el-option label="新闻" value="NEWS"/><el-option label="辩论" value="DEBATE"/><el-option label="预测" value="PREDICTION"/></el-select></el-form-item>
        <el-form-item label="内容">
          <div class="post-editor"><el-input v-model="form.content" type="textarea" :rows="8" maxlength="5000" show-word-limit placeholder="分享你的观点..."/><button ref="contentEmojiBtn" class="emoji-trigger" title="插入表情" @click.prevent="emoji.open(contentEmojiBtn!, value => form.content += value)"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg></button></div>
        </el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="用逗号分隔，如：湖人,季后赛" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitPost">发布</el-button></template>
    </el-dialog>
    <EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPost, fetchHotPosts, fetchPosts, fetchTopics, type Post, type PostRequest, type Topic } from '@/api/community'
import { useIdentityStore } from '@/stores/identity'
import AppEmpty from '@/components/common/AppEmpty.vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'

const router = useRouter()
const emoji = useEmojiPicker()
const identityStore = useIdentityStore()
const loading = ref(false)
const submitting = ref(false)
const contentEmojiBtn = ref<HTMLElement | null>(null)
const posts = ref<Post[]>([])
const hotPosts = ref<Post[]>([])
/** 过滤压测/内部测试帖，避免出现在普通用户热榜 */
const TEST_TITLE_RE = /(压测|压宿|宿主|IDOR|注入|audit\d|loadtest|test[_-]?\d|测试帖)/i
const visibleHotPosts = computed(() => (hotPosts.value || []).filter((p: Post) => !TEST_TITLE_RE.test(p.title || '')))
const popularTopics = ref<Topic[]>([])
const total = ref(0)
const page = ref(1)
const activeCategory = ref('ALL')
const activeTopic = ref('')
const sortMode = ref<'latest' | 'hot' | 'discussed'>('latest')
const createVisible = ref(false)
const form = ref<PostRequest>({ title: '', content: '', category: 'DISCUSSION', tags: '' })

const categoryMap: Record<string, string> = { DISCUSSION: '讨论', NEWS: '新闻', DEBATE: '辩论', PREDICTION: '预测' }
const categoryClassMap: Record<string, string> = { DISCUSSION: 'tag-discussion', NEWS: 'tag-news', DEBATE: 'tag-debate', PREDICTION: 'tag-prediction' }
const sortMap = { latest: 'createTime,desc', hot: 'viewCount,desc', discussed: 'commentCount,desc' }

const canSubmit = computed(() => form.value.title.trim().length > 0 && form.value.content.trim().length > 0)
const visiblePosts = computed(() => posts.value)
const pageCommentCount = computed(() => posts.value.reduce((sum, post) => sum + (post.commentCount || 0), 0))
const pageInteractionCount = computed(() => posts.value.reduce((sum, post) => sum + (post.likeCount || 0) + (post.commentCount || 0) + (post.favoriteCount || 0), 0))

function postTags(post: Post) { return (post.tags || '').split(/[,，#]/).map(tag => tag.trim()).filter(Boolean) }
function categoryLabel(category: string) { return categoryMap[category] || category }
function categoryClass(category: string) { return categoryClassMap[category] || '' }
function userInitial(username?: string) { return (username || '球').trim().slice(0, 1).toUpperCase() }
function excerpt(content: string) { return content.length > 100 ? `${content.slice(0, 100)}...` : content }
function isHot(post: Post) { return (post.viewCount > 100 || post.likeCount > 20 || post.commentCount > 10) && !post.isTop && !post.isFeatured }
function compactNumber(value: number) { return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value || 0) }
function formatTime(value?: string) {
  if (!value) return ''
  const diff = Date.now() - new Date(value).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.max(1, Math.floor(diff / 86400000))}天前`
}

async function loadPosts() {
  loading.value = true
  try {
    const params: { page: number; size: number; category?: string; tag?: string; sort?: string } = { page: page.value - 1, size: 10, sort: sortMap[sortMode.value] }
    if (activeCategory.value !== 'ALL') params.category = activeCategory.value
    if (activeTopic.value) params.tag = activeTopic.value
    const { data } = await fetchPosts(params)
    posts.value = data.content || []
    total.value = data.totalElements || 0
  } catch { ElMessage.error('加载帖子失败') } finally { loading.value = false }
}

async function loadHot() {
  try { const { data } = await fetchHotPosts({ page: 0, size: 10 }); hotPosts.value = data.content || [] } catch { hotPosts.value = [] }
}

async function loadTopics() {
  try {
    const category = activeCategory.value !== 'ALL' ? activeCategory.value : undefined
    const { data } = await fetchTopics({ category, limit: 8 })
    popularTopics.value = data || []
  } catch { popularTopics.value = [] }
}

function onCategoryChange() { page.value = 1; activeTopic.value = ''; loadPosts(); loadTopics() }
function onSortChange() { page.value = 1; loadPosts() }
function selectTopic(topic: string) { activeTopic.value = topic; page.value = 1; loadPosts() }
function clearTopic() { activeTopic.value = ''; page.value = 1; loadPosts() }
function toggleTopic(topic: string) { activeTopic.value === topic ? clearTopic() : selectTopic(topic) }
function goToPost(id: number) { router.push({ path: '/community/post', query: { id: String(id) } }) }
function openCreate() { createVisible.value = true; emoji.close() }

async function submitPost() {
  if (!canSubmit.value) { ElMessage.warning('请填写标题和内容'); return }
  submitting.value = true
  try {
    await createPost({ ...form.value, title: form.value.title.trim(), content: form.value.content.trim(), tags: form.value.tags?.trim() })
    ElMessage.success('发布成功')
    createVisible.value = false
    form.value = { title: '', content: '', category: 'DISCUSSION', tags: '' }
    page.value = 1
    await Promise.all([loadPosts(), loadHot()])
  } catch { ElMessage.error('发布失败') } finally { submitting.value = false }
}

// 实时身份同步：更新帖子作者名和头像
watch(() => identityStore.revision, () => {
  posts.value = posts.value.map(post => {
    const id = identityStore.getIdentity(post.userId)
    if (!id) return post
    return { ...post, username: id.username, avatarUrl: id.avatarUrl }
  })
  hotPosts.value = hotPosts.value.map(post => {
    const id = identityStore.getIdentity(post.userId)
    if (!id) return post
    return { ...post, username: id.username, avatarUrl: id.avatarUrl }
  })
})

onMounted(() => Promise.all([loadPosts(), loadHot(), loadTopics()]))
</script>

<style scoped>
.community-page{max-width:1440px;min-height:calc(100vh - 88px);margin:0 auto;padding:0 24px 48px;color:var(--text-primary)}
.community-hero{position:relative;display:flex;min-height:150px;margin-bottom:16px;padding:22px 28px;overflow:hidden;border:1px solid var(--border-light);border-radius:16px;background:var(--bg-card);box-shadow:var(--shadow-sm)}
.community-hero::before{position:absolute;inset:0;content:'';background:radial-gradient(circle at 72% 36%,rgba(34, 211, 238,.12),transparent 35%),linear-gradient(110deg,var(--bg-card) 36%,rgba(34, 211, 238,.06));pointer-events:none}
.community-hero::after{position:absolute;inset:0 0 auto;height:1px;content:'';background:linear-gradient(90deg,transparent,rgba(34, 211, 238,.7),transparent);opacity:.75;pointer-events:none}
.hero-copy{position:relative;z-index:2}.hero-kicker{color:var(--accent);font-family:var(--font-mono);font-size:10px;font-weight:700;letter-spacing:1.4px}.hero-copy h1{margin:6px 0 3px;font-family:var(--font-heading);font-size:26px;letter-spacing:0}.hero-copy p{margin:0;color:var(--text-muted);font-size:13px}.hero-stats{display:flex;gap:32px;margin-top:16px}.hero-stats div{display:grid;gap:2px}.hero-stats strong{font-size:16px}.hero-stats span{color:var(--text-muted);font-size:11px}.hero-create{position:absolute;z-index:3;top:20px;right:24px;height:38px;padding-inline:18px}.hero-create svg{width:15px;margin-right:6px}.hero-visual{position:absolute;inset:0 0 0 44%;overflow:hidden}.logo-orbit{position:absolute;top:50%;left:54%;width:220px;height:120px;transform:translate(-50%,-50%)}.logo-orbit::before{position:absolute;inset:-20px;border:1px solid var(--purple);border-radius:50%;content:'';opacity:.24;transform:rotate(-12deg)}.logo-orbit img{position:absolute;width:72px;height:72px;object-fit:contain;filter:drop-shadow(0 10px 20px var(--purple-glow))}.logo-orbit img:first-child{top:20px;left:72px;width:82px;height:82px}.logo-orbit img:nth-child(2){top:0;left:4px;opacity:.68}.logo-orbit img:nth-child(3){right:0;bottom:0;opacity:.68}.court-line{position:absolute;width:500px;height:160px;border:1px solid var(--border-medium);border-radius:50%;opacity:.35}.court-line--one{right:-80px;bottom:-80px}.court-line--two{right:100px;bottom:-120px}
.community-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:14px;border-bottom:1px solid var(--border-light)}.community-tabs{min-width:0;flex:1}:deep(.community-tabs .el-tabs__header){margin:0}:deep(.community-tabs .el-tabs__nav-wrap::after){display:none}:deep(.community-tabs .el-tabs__active-bar){background:var(--accent)}:deep(.community-tabs .el-tabs__item){height:48px;color:var(--text-muted)}:deep(.community-tabs .el-tabs__item.is-active){color:var(--accent)}.toolbar-actions{display:flex;align-items:center;gap:8px}.sort-select{width:126px}.topic-filter{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border-radius:var(--radius-sm);background:var(--accent-lighter);color:var(--accent);font-size:12px}.topic-filter button{border:0;background:transparent;color:inherit;cursor:pointer;font-size:16px}
.community-layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:20px}.community-main{min-width:0}.post-card{display:grid;grid-template-columns:116px minmax(0,1fr);gap:20px;margin-bottom:10px;padding:18px 20px;border:1px solid var(--border-light);border-radius:14px;background:var(--bg-card);cursor:pointer;transition:border-color .18s ease,transform .22s var(--ease-out-quart,ease),box-shadow .22s var(--ease-out-quart,ease)}.post-card:hover,.post-card:focus-visible{border-color:rgba(34, 211, 238,.32);box-shadow:var(--shadow-md),0 10px 24px rgba(34, 211, 238,.08);outline:none;transform:translateY(-3px)}.post-author-block{display:flex;align-items:flex-start;gap:9px;padding-right:16px;border-right:1px solid var(--border-light)}.author-avatar{display:grid;width:38px;height:38px;flex:0 0 38px;place-items:center;border:1px solid var(--border-medium);border-radius:50%;background:var(--accent-lighter);color:var(--accent);font-weight:800;overflow:hidden}.author-avatar img{width:100%;height:100%;object-fit:cover}.post-author-block div{display:grid;min-width:0;gap:3px}.post-author-block strong{overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis}.post-author-block span{color:var(--text-dim);font-size:10px}.post-labels{display:flex;align-items:center;gap:6px;margin-bottom:6px}.post-category-tag,.status-tag{padding:3px 7px;border-radius:var(--radius-sm);font-size:10px;font-weight:700}.tag-discussion{background:var(--cyan-dim);color:var(--cyan)}.tag-news{background:var(--accent-lighter);color:var(--accent)}.tag-debate{background:var(--warning-glow);color:var(--warning)}.tag-prediction{background:var(--purple-dim);color:var(--purple)}.status-tag--top{background:var(--danger-glow);color:var(--danger)}.status-tag--featured{background:var(--warning-glow);color:var(--warning)}.status-tag--hot{background:var(--accent-lighter);color:var(--accent)}.post-content h2{margin:0 0 5px;color:var(--text-primary);font-size:16px;line-height:1.45;transition:color var(--duration-fast)}.post-card:hover h2{color:var(--accent)}.post-content>p{display:-webkit-box;margin:0 0 8px;overflow:hidden;color:var(--text-muted);font-size:12px;line-height:1.6;-webkit-box-orient:vertical;-webkit-line-clamp:2}.post-tags{display:flex;gap:6px;margin-bottom:9px}.post-tags button{padding:0;border:0;background:transparent;color:var(--text-dim);cursor:pointer;font-size:11px}.post-tags button:hover{color:var(--accent)}.post-signals{display:flex;align-items:center;gap:18px;color:var(--text-dim);font-size:11px}.post-signals span{display:inline-flex;align-items:center;gap:5px}.post-signals svg,.sidebar-card header svg{width:13px;height:13px;fill:none;stroke:currentColor;stroke-width:1.8}.post-signals time{margin-left:auto}
.community-sidebar{display:grid;align-content:start;gap:10px}.sidebar-card{overflow:hidden;border:1px solid var(--border-light);border-radius:var(--radius-lg);background:var(--bg-card)}.sidebar-card header{display:flex;align-items:center;gap:8px;padding:14px 15px;border-bottom:1px solid var(--border-light);font-size:13px;font-weight:700}.sidebar-card header svg{color:var(--accent)}.ranking-item{display:flex;width:100%;gap:10px;padding:10px 14px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer}.ranking-item:hover{background:var(--bg-hover)}.rank{display:grid;width:20px;height:20px;flex:0 0 20px;place-items:center;border-radius:var(--radius-sm);background:var(--bg-secondary);color:var(--text-muted);font-size:10px;font-weight:800}.rank.top{background:var(--accent);color:var(--text-inverse)}.ranking-copy{display:grid;min-width:0;gap:3px}.ranking-copy strong{overflow:hidden;color:var(--text-secondary);font-size:11px;font-weight:600;text-overflow:ellipsis;white-space:nowrap}.ranking-copy small{color:var(--text-dim);font-size:9px}.topic-cloud{display:grid;grid-template-columns:1fr 1fr;gap:7px;padding:12px}.topic-cloud button{display:grid;gap:3px;padding:8px;border:1px solid var(--border-light);border-radius:var(--radius-md);background:var(--bg-secondary);color:var(--text-muted);text-align:left;cursor:pointer}.topic-cloud button:hover,.topic-cloud button.active{border-color:var(--accent);color:var(--accent)}.topic-cloud span{overflow:hidden;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.topic-cloud small{color:var(--text-dim);font-size:9px}.sidebar-empty{margin:0;padding:20px;color:var(--text-dim);font-size:11px;text-align:center}.data-grid{display:grid;grid-template-columns:repeat(3,1fr);padding:15px}.data-grid div{display:grid;gap:4px;text-align:center}.data-grid div+div{border-left:1px solid var(--border-light)}.data-grid strong{font-size:14px}.data-grid span{color:var(--text-dim);font-size:9px}.rules-list{display:grid;gap:12px;padding:14px 15px}.rule-item{display:flex;align-items:flex-start;gap:10px;font-size:12px;line-height:1.5;color:var(--text-muted)}.rule-item p{margin:0}.rule-item strong{color:var(--text-primary);font-weight:600}.rule-dot{font-size:13px;line-height:1.4;flex-shrink:0}.pager{display:flex;justify-content:center;margin-top:18px}.post-editor{position:relative;width:100%}.emoji-trigger{position:absolute;right:9px;bottom:30px;display:grid;width:30px;height:30px;padding:0;place-items:center;border:1px solid var(--border-light);border-radius:var(--radius-md);background:var(--bg-card);color:var(--text-muted);cursor:pointer}.emoji-trigger:hover{border-color:var(--accent);color:var(--accent)}.emoji-trigger svg{width:16px;fill:none;stroke:currentColor;stroke-width:1.8}
@media(max-width:900px){.community-layout{grid-template-columns:1fr}.community-sidebar{grid-template-columns:repeat(3,1fr)}.hero-visual{opacity:.45}.post-card{grid-template-columns:96px minmax(0,1fr)}}
@media(max-width:700px){.community-page{padding-inline:14px}.community-hero{min-height:0;padding:18px 20px}.hero-create{top:20px;right:18px}.hero-visual{display:none}.hero-stats{gap:24px}.community-toolbar{align-items:flex-end;flex-direction:column;gap:0}.community-tabs{width:100%}.toolbar-actions{width:100%;justify-content:flex-end;padding:8px 0}.post-card{grid-template-columns:1fr;padding:16px}.post-author-block{align-items:center;padding:0 0 10px;border-right:0;border-bottom:1px solid var(--border-light)}.post-signals{gap:12px;flex-wrap:wrap}.post-signals time{width:100%;margin:0}.community-sidebar{grid-template-columns:1fr}.logo-orbit{display:none}}
</style>
