<template>
  <div class="page">
    <div class="page-inner">
      <div class="section-head">
        <div>
          <h2>社区</h2>
          <p>NBA 球迷讨论区，分享观点、参与辩论</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14" style="margin-right:4px"><path d="M12 5v14M5 12h14"/></svg>
          发帖
        </el-button>
      </div>

      <!-- 分类 Tab -->
      <el-tabs v-model="activeCategory" @tab-change="onCategoryChange" class="community-tabs">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="讨论" name="DISCUSSION" />
        <el-tab-pane label="新闻" name="NEWS" />
        <el-tab-pane label="辩论" name="DEBATE" />
        <el-tab-pane label="预测" name="PREDICTION" />
      </el-tabs>

      <div class="community-layout">
        <!-- 帖子列表 -->
        <div class="community-main">
          <div v-loading="loading">
            <div v-for="post in posts" :key="post.id" class="post-item" @click="goToPost(post.id)">
              <div class="post-header">
                <span class="post-category-tag" :class="categoryClass(post.category)">
                  {{ categoryLabel(post.category) }}
                </span>
                <el-tag v-if="post.isTop" type="danger" size="small" effect="dark">置顶</el-tag>
                <el-tag v-if="post.isFeatured" type="warning" size="small" effect="dark">精华</el-tag>
                <span v-if="isHot(post)" class="hot-badge">热</span>
              </div>
              <h3 class="post-title">{{ post.title }}</h3>
              <p class="post-excerpt">{{ post.content.slice(0, 120) }}{{ post.content.length > 120 ? '...' : '' }}</p>
              <div class="post-footer">
                <span v-if="post.username" class="post-author">{{ post.username }}</span>
                <span class="post-signal">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  {{ post.viewCount }}
                </span>
                <span class="post-signal">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                  {{ post.likeCount }}
                </span>
                <span class="post-signal">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                  {{ post.commentCount }}
                </span>
                <span class="post-signal">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                  {{ post.favoriteCount ?? 0 }}
                </span>
                <span class="post-time">{{ formatTime(post.lastReplyTime || post.createTime) }}</span>
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
        </div>

        <!-- 热帖侧栏 -->
        <div class="community-sidebar">
          <div class="hot-card">
            <div class="hot-card-header">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
              <span>热帖排行</span>
            </div>
            <div class="hot-card-body">
              <div v-for="(post, i) in hotPosts" :key="post.id" class="hot-item" @click="goToPost(post.id)">
                <span class="hot-rank" :class="{ 'hot-rank-top': i < 3 }">{{ i + 1 }}</span>
                <div class="hot-info">
                  <span class="hot-name">{{ post.title.slice(0, 28) }}{{ post.title.length > 28 ? '...' : '' }}</span>
                  <div class="hot-stats">
                    <span>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                      {{ post.viewCount }}
                    </span>
                    <span>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                      {{ post.likeCount }}
                    </span>
                    <span>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      {{ post.commentCount }}
                    </span>
                  </div>
                </div>
              </div>
              <el-empty v-if="hotPosts.length === 0" description="暂无热帖" :image-size="60" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 发帖弹窗 -->
    <el-dialog v-model="createVisible" title="发布帖子" width="600px" destroy-on-close class="dialog-light" :append-to-body="true" :center="true">
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
              <button ref="contentEmojiBtn" class="emoji-trigger" @click.prevent="emojiTarget = 'content'; emoji.open(contentEmojiBtn!, e => form.content += e)" title="插入表情">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
              </button>
            </div>
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
    <EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPosts, fetchHotPosts, createPost } from '@/api/community'
import type { Post, PostRequest } from '@/api/community'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const emoji = useEmojiPicker()
const emojiTarget = ref<'title' | 'content'>('content')
const contentEmojiBtn = ref<HTMLElement | null>(null)
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

function categoryLabel(c: string) { return categoryMap[c] || c }
function categoryClass(c: string) { return categoryClassMap[c] || '' }

function isHot(post: Post) {
  return (post.viewCount > 100 || post.likeCount > 20 || post.commentCount > 10) && !post.isTop && !post.isFeatured
}

function formatTime(t: string) {
  if (!t) return ''
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
function openCreate() { createVisible.value = true; emoji.close() }

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
.page { max-width: 1200px; margin: 0 auto; padding: 0 24px; min-height: calc(100vh - 108px); position: relative; animation: pageFadeIn 0.3s ease forwards; opacity: 0; transform: translateY(8px); }
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

.section-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border-light); }
.section-head h2 { margin: 0 0 4px; color: var(--text-primary); font-family: var(--font-heading); font-size: 20px; font-weight: 700; }
.section-head p { margin: 0; color: var(--text-muted); font-size: 13px; }

.community-tabs { margin-bottom: 16px; }
:deep(.community-tabs .el-tabs__header) { margin: 0; }
:deep(.community-tabs .el-tabs__nav-wrap::after) { background: var(--border-light); }
:deep(.community-tabs .el-tabs__active-bar) { background: var(--accent); }
:deep(.community-tabs .el-tabs__item) { color: var(--text-muted); }
:deep(.community-tabs .el-tabs__item.is-active) { color: var(--accent); }

/* 双栏布局 */
.community-layout {
  display: flex;
  gap: 20px;
}
.community-main { flex: 1; min-width: 0; }
.community-sidebar { width: 280px; flex-shrink: 0; }

/* 帖子卡片 */
.post-item {
  padding: 16px 20px;
  margin-bottom: 10px;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.15s ease;
}
.post-item:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-sm);
}
.post-header { display: flex; gap: 6px; margin-bottom: 8px; align-items: center; flex-wrap: wrap; }

/* 分类标签 */
.post-category-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.tag-discussion { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.tag-news { background: var(--accent-lighter); color: var(--accent); }
.tag-debate { background: rgba(245, 158, 11, 0.1); color: #d97706; }
.tag-prediction { background: var(--purple-dim); color: var(--purple); }

/* 热帖徽章 */
.hot-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 3px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.post-title {
  margin: 0 0 6px;
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
  transition: color 0.15s;
}
.post-item:hover .post-title { color: var(--accent); }

.post-excerpt {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: var(--text-dim);
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}
.post-author {
  font-weight: 600;
  color: var(--text-secondary);
}
.post-signal {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.post-signal svg { opacity: 0.5; }
.post-time { margin-left: auto; }

/* 热帖侧栏 */
.hot-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  position: sticky;
  top: 72px;
}
.hot-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.hot-card-header svg { color: var(--accent); }
.hot-card-body { padding: 4px 0; }

.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.hot-item:hover { background: var(--bg-hover); }

.hot-rank {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  background: var(--bg-secondary);
  color: var(--text-dim);
  flex-shrink: 0;
}
.hot-rank-top { background: var(--accent); color: #fff; }

.hot-info { flex: 1; min-width: 0; }
.hot-name {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.hot-item:hover .hot-name { color: var(--accent); }

.hot-stats {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--text-dim);
}
.hot-stats span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.hot-stats svg { opacity: 0.4; }

.pager { margin-top: 16px; display: flex; justify-content: center; }

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
  cursor: pointer;
  transition: all 0.15s;
  color: var(--text-muted);
}
.emoji-trigger:hover { border-color: var(--accent); color: var(--accent); }

/* 移动端 */
@media (max-width: 768px) {
  .community-layout {
    flex-direction: column;
    gap: 16px;
  }
  .community-main {
    width: 100%;
    min-width: 0;
  }
  .community-sidebar {
    width: 100%;
    order: -1;
  }
  .hot-card {
    position: static;
  }
  .hot-card-body {
    display: flex;
    overflow-x: auto;
    gap: 0;
    padding: 0;
    -webkit-overflow-scrolling: touch;
  }
  .hot-item {
    flex-direction: column;
    align-items: flex-start;
    min-width: 160px;
    padding: 10px 12px;
    border-right: 1px solid var(--border-light);
  }
  .hot-name {
    white-space: normal;
    -webkit-line-clamp: 2;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .post-footer {
    flex-wrap: wrap;
    gap: 8px;
  }
  .post-time { margin-left: 0; }
}

/* 浅色主题 */
[data-theme="light"] .post-item:hover {
  border-color: var(--border-medium);
  box-shadow: var(--shadow-md);
}
[data-theme="light"] .post-item:hover .post-title { color: var(--accent); }
[data-theme="light"] .tag-discussion { background: rgba(59, 130, 246, 0.08); color: #2563EB; }
[data-theme="light"] .tag-news { background: rgba(232, 93, 38, 0.08); color: #D14E1F; }
[data-theme="light"] .tag-debate { background: rgba(245, 158, 11, 0.08); color: #B45309; }
[data-theme="light"] .tag-prediction { background: rgba(124, 58, 237, 0.08); color: #6D28D9; }
</style>
