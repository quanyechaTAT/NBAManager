<template>
  <div class="page animated-bg">
    <div class="bg-particles">
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
      <div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <div class="section-head">
        <div>
          <h2>💬 社区</h2>
          <p>NBA球迷讨论区，分享观点、参与辩论</p>
        </div>
        <el-button type="primary" @click="openCreate">✍️ 发帖</el-button>
      </div>

      <!-- 分类Tab -->
      <el-tabs v-model="activeCategory" @tab-change="onCategoryChange" class="community-tabs">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="讨论" name="DISCUSSION" />
        <el-tab-pane label="新闻" name="NEWS" />
        <el-tab-pane label="辩论" name="DEBATE" />
        <el-tab-pane label="预测" name="PREDICTION" />
      </el-tabs>

      <el-row :gutter="20">
        <!-- 帖子列表 -->
        <el-col :span="17">
          <div v-loading="loading">
            <div v-for="post in posts" :key="post.id" class="post-item" @click="goToPost(post.id)">
              <div class="post-header">
                <span class="post-category-tag" :class="categoryClass(post.category)">
                  <span class="tag-icon">{{ categoryIcon(post.category) }}</span>
                  {{ categoryLabel(post.category) }}
                </span>
                <el-tag v-if="post.isTop" type="danger" size="small" effect="dark">置顶</el-tag>
              </div>
              <h3 class="post-title">{{ post.title }}</h3>
              <p class="post-excerpt">{{ post.content.slice(0, 120) }}{{ post.content.length > 120 ? '...' : '' }}</p>
              <div class="post-footer">
                <span class="post-meta">👁 {{ post.viewCount }}</span>
                <span class="post-meta">👍 {{ post.likeCount }}</span>
                <span class="post-meta">💬 {{ post.commentCount }}</span>
                <span class="post-meta">⭐ {{ post.favoriteCount || 0 }}</span>
                <span class="post-time">{{ formatTime(post.createTime) }}</span>
              </div>
            </div>
            <el-empty v-if="!loading && posts.length === 0" description="暂无帖子" />
          </div>
          <div class="pager" v-if="total > 0">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="total"
              v-model:current-page="page"
              :page-size="10"
              @current-change="loadPosts"
            />
          </div>
        </el-col>

        <!-- 热帖侧栏 -->
        <el-col :span="7">
          <el-card shadow="never" class="hot-card">
            <template #header>
              <span class="hot-title">🔥 热门帖子</span>
            </template>
            <div v-for="(post, i) in hotPosts" :key="post.id" class="hot-item" @click="goToPost(post.id)">
              <span class="hot-rank" :class="{ 'hot-rank-top': i < 3 }">{{ i + 1 }}</span>
              <div class="hot-info">
                <span class="hot-name">{{ post.title.slice(0, 30) }}</span>
                <div class="hot-stats">
                  <span class="hot-views">👁 {{ post.viewCount }}</span>
                  <span class="hot-likes">👍 {{ post.likeCount }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="hotPosts.length === 0" description="暂无热帖" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 发帖弹窗 -->
    <el-dialog v-model="createVisible" title="发布帖子" width="600px" destroy-on-close class="dialog-light">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入帖子标题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="讨论" value="DISCUSSION" />
            <el-option label="新闻" value="NEWS" />
            <el-option label="辩论" value="DEBATE" />
            <el-option label="预测" value="PREDICTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <div style="position: relative;">
            <el-input v-model="form.content" type="textarea" :rows="8" maxlength="5000" show-word-limit placeholder="分享你的观点..." />
            <div class="textarea-toolbar">
              <button class="emoji-trigger" @click.prevent="showEmojiPicker = !showEmojiPicker; emojiTarget = 'content'" title="插入表情">😊</button>
            </div>
            <EmojiPicker :visible="showEmojiPicker && emojiTarget === 'content'" @select="insertEmoji" @close="showEmojiPicker = false" />
          </div>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="用逗号分隔，如：湖人,季后赛" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPosts, fetchHotPosts, createPost } from '@/api/community'
import type { Post, PostRequest } from '@/api/community'
import EmojiPicker from '@/components/EmojiPicker.vue'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const showEmojiPicker = ref(false)
const emojiTarget = ref<'title' | 'content'>('content')
const posts = ref<Post[]>([])
const hotPosts = ref<Post[]>([])
const total = ref(0)
const page = ref(1)
const activeCategory = ref('ALL')
const createVisible = ref(false)
const form = ref<PostRequest>({ title: '', content: '', category: 'DISCUSSION', tags: '' })

const categoryMap: Record<string, string> = {
  DISCUSSION: '讨论', NEWS: '新闻', DEBATE: '辩论', PREDICTION: '预测',
}
const categoryClassMap: Record<string, string> = {
  DISCUSSION: 'tag-discussion', NEWS: 'tag-news', DEBATE: 'tag-debate', PREDICTION: 'tag-prediction',
}
const categoryIconMap: Record<string, string> = {
  DISCUSSION: '💬', NEWS: '📰', DEBATE: '⚔️', PREDICTION: '🔮',
}

function categoryLabel(c: string) { return categoryMap[c] || c }
function categoryClass(c: string) { return categoryClassMap[c] || '' }
function categoryIcon(c: string) { return categoryIconMap[c] || '📌' }

function formatTime(t: string) {
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

async function loadPosts() {
  loading.value = true
  try {
    const params: any = { page: page.value - 1, size: 10 }
    if (activeCategory.value !== 'ALL') params.category = activeCategory.value
    const { data } = await fetchPosts(params)
    posts.value = data.content
    total.value = data.totalElements
  } catch {
    ElMessage.error('加载帖子失败')
  } finally {
    loading.value = false
  }
}

async function loadHot() {
  try {
    const { data } = await fetchHotPosts({ page: 0, size: 10 })
    hotPosts.value = data.content
  } catch { /* ignore */ }
}

function onCategoryChange() { page.value = 1; loadPosts() }

function goToPost(id: number) { router.push({ path: '/community/post', query: { id: String(id) } }) }

function openCreate() { createVisible.value = true; showEmojiPicker.value = false }

function insertEmoji(emoji: string) {
  if (emojiTarget.value === 'title') {
    form.value.title += emoji
  } else {
    form.value.content += emoji
  }
  showEmojiPicker.value = false
}

async function submitPost() {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  submitting.value = true
  try {
    await createPost(form.value)
    ElMessage.success('发布成功')
    createVisible.value = false
    form.value = { title: '', content: '', category: 'DISCUSSION', tags: '' }
    page.value = 1
    loadPosts()
    loadHot()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => { loadPosts(); loadHot() })
</script>

<style scoped>
.page { max-width: 1200px; min-height: calc(100vh - 108px); position: relative; border-radius: var(--radius-lg); animation: pageFadeIn 0.4s var(--ease-smooth) forwards; opacity: 0; transform: translateY(12px); }
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.section-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border-light); position: relative; }
.section-head::after { content: ''; position: absolute; bottom: -1px; left: 0; width: 40px; height: 2px; background: linear-gradient(90deg, var(--purple), transparent); border-radius: 1px; }
.section-head h2 { margin: 0 0 6px; color: var(--text-primary); font-family: var(--font-heading); font-size: 20px; font-weight: 700; }
.section-head p { margin: 0; color: var(--text-muted); font-size: 13px; }

.community-tabs { margin-bottom: 16px; }
:deep(.community-tabs .el-tabs__header) { margin: 0; }
:deep(.community-tabs .el-tabs__nav-wrap::after) { background: var(--border-light); }
:deep(.community-tabs .el-tabs__active-bar) { background: var(--purple); }
:deep(.community-tabs .el-tabs__item) { color: var(--text-muted); }
:deep(.community-tabs .el-tabs__item.is-active) { color: var(--purple); }

.post-item { padding: 16px; margin-bottom: 12px; border-radius: var(--radius-lg); background: var(--bg-card); border: 1px solid var(--border-light); cursor: pointer; transition: all var(--duration-fast) var(--ease-smooth); position: relative; overflow: hidden; }
.post-item::before { content: ''; position: absolute; top: 0; left: 0; width: 3px; height: 0; background: linear-gradient(180deg, var(--purple), var(--accent)); transition: height var(--duration-normal) var(--ease-smooth); border-radius: 0 2px 2px 0; }
.post-item:hover { border-color: var(--purple); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(108, 92, 231, 0.15); }
.post-item:hover::before { height: 100%; }
.post-header { display: flex; gap: 6px; margin-bottom: 8px; align-items: center; }

/* 帖子分类标签样式 */
.post-category-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
  transition: all var(--duration-fast) var(--ease-smooth);
}
.tag-icon { font-size: 13px; }

/* 讨论 - 蓝色 */
.tag-discussion {
  background: rgba(59, 130, 246, 0.12);
  color: #60a5fa;
  border: 1px solid rgba(59, 130, 246, 0.25);
}
.tag-discussion:hover {
  background: rgba(59, 130, 246, 0.2);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.15);
}

/* 新闻 - 绿色 */
.tag-news {
  background: rgba(0, 230, 118, 0.1);
  color: var(--accent);
  border: 1px solid rgba(0, 230, 118, 0.2);
}
.tag-news:hover {
  background: rgba(0, 230, 118, 0.18);
  box-shadow: 0 0 12px rgba(0, 230, 118, 0.15);
}

/* 辩论 - 橙色 */
.tag-debate {
  background: rgba(255, 152, 0, 0.12);
  color: #ffa726;
  border: 1px solid rgba(255, 152, 0, 0.25);
}
.tag-debate:hover {
  background: rgba(255, 152, 0, 0.2);
  box-shadow: 0 0 12px rgba(255, 152, 0, 0.15);
}

/* 预测 - 紫色 */
.tag-prediction {
  background: rgba(139, 92, 246, 0.12);
  color: var(--purple-light);
  border: 1px solid rgba(139, 92, 246, 0.25);
}
.tag-prediction:hover {
  background: rgba(139, 92, 246, 0.2);
  box-shadow: 0 0 12px rgba(139, 92, 246, 0.15);
}

.post-title { margin: 0 0 8px; font-size: 16px; color: var(--text-primary); font-weight: 600; line-height: 1.4; transition: color var(--duration-fast) var(--ease-smooth); }
.post-item:hover .post-title { color: var(--purple-light); }
.post-excerpt { margin: 0 0 10px; font-size: 13px; color: var(--text-muted); line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.post-footer { display: flex; gap: 16px; align-items: center; font-size: 12px; color: var(--text-dim); padding-top: 10px; border-top: 1px solid var(--border-light); margin-top: 8px; }
.post-meta { display: inline-flex; align-items: center; gap: 3px; transition: color var(--duration-fast) var(--ease-smooth); }
.post-meta:hover { color: var(--text-secondary); }
.post-time { margin-left: auto; }

.hot-card { background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; position: relative; overflow: hidden; }
.hot-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: linear-gradient(90deg, var(--danger), var(--warning)); border-radius: var(--radius-xl) var(--radius-xl) 0 0; }
:deep(.hot-card .el-card__header) { padding: 12px 16px; border-bottom: 1px solid var(--border-light); }
.hot-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.hot-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--border-light); cursor: pointer; transition: all var(--duration-fast) var(--ease-smooth); }
.hot-item:hover { padding-left: 8px; background: var(--bg-hover); border-radius: var(--radius-sm); }
.hot-item:last-child { border-bottom: none; }
.hot-item:hover .hot-name { color: var(--purple); }
.hot-rank { width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; background: var(--bg-secondary); color: var(--text-dim); flex-shrink: 0; transition: all var(--duration-fast) var(--ease-smooth); }
.hot-item:hover .hot-rank { transform: scale(1.15); }
.hot-rank-top { background: var(--purple); color: #fff; }
.hot-info { flex: 1; min-width: 0; display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.hot-name { font-size: 13px; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hot-stats { display: flex; gap: 8px; }
.hot-views { font-size: 11px; color: var(--text-dim); }
.hot-likes { font-size: 11px; color: var(--text-dim); }

.pager { margin-top: 16px; display: flex; justify-content: center; }

/* 帖子卡片入场动画 */
.post-item { animation: postSlideIn 0.4s var(--ease-smooth) forwards; opacity: 0; transform: translateY(12px); }
.post-item:nth-child(1) { animation-delay: 0.05s; }
.post-item:nth-child(2) { animation-delay: 0.1s; }
.post-item:nth-child(3) { animation-delay: 0.15s; }
.post-item:nth-child(4) { animation-delay: 0.2s; }
.post-item:nth-child(5) { animation-delay: 0.25s; }
@keyframes postSlideIn { to { opacity: 1; transform: translateY(0); } }

.textarea-toolbar {
  position: absolute;
  bottom: 30px;
  right: 8px;
  z-index: 10;
}
.emoji-trigger {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.emoji-trigger:hover { border-color: var(--accent); transform: scale(1.1); }
[data-theme="light"] .emoji-trigger { background: #FFFFFF; border-color: #E8E5E0; }
[data-theme="light"] .emoji-trigger:hover { border-color: #E85D26; }
</style>
