<template>
  <div class="page">
    <div class="page-inner">
      <!-- 返回 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="router.back()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回社区
        </el-button>
      </div>

      <div v-loading="loading" v-if="post">
        <!-- 帖子内容 -->
        <div class="post-card">
          <div class="post-card-top"></div>
          <div class="post-header">
            <div class="post-header-left">
              <span class="post-category-tag" :class="categoryClass(post.category)">{{ categoryLabel(post.category) }}</span>
              <span v-if="post.isTop" class="top-badge">置顶</span>
              <span v-if="post.isFeatured" class="featured-badge">精华</span>
            </div>
            <span class="post-time">{{ formatTime(post.createTime) }}</span>
          </div>
          <!-- 作者信息 -->
          <div class="post-author-bar" v-if="post.username">
            <div class="author-avatar">{{ post.username?.charAt(0)?.toUpperCase() }}</div>
            <div class="author-info">
              <span class="author-name">{{ post.username }}</span>
              <span class="author-time">发布于 {{ formatTime(post.createTime) }}</span>
            </div>
          </div>
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-content">{{ post.content }}</div>
          <div class="post-tags" v-if="post.tags">
            <el-tag v-for="tag in post.tags.split(',')" :key="tag" size="small" type="info" effect="plain" class="tag-item">{{ tag.trim() }}</el-tag>
          </div>
          <div class="post-actions">
            <button class="action-btn" :class="{ active: post.likedByMe }" @click="toggleLike">
              <svg viewBox="0 0 24 24" fill="none" :stroke="post.likedByMe ? 'var(--accent)' : 'currentColor'" stroke-width="2" width="16" height="16"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
              {{ post.likeCount || '' }}
            </button>
            <button v-if="auth.token" class="action-btn" :class="{ active: isFavorited }" @click="toggleFavoritePost">
              <svg viewBox="0 0 24 24" :fill="isFavorited ? 'var(--accent)' : 'none'" stroke="currentColor" stroke-width="2" width="16" height="16"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </button>
            <ShareButton :url="postUrl" :title="post.title" :description="post.content" />
            <div class="post-stats">
              <span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ post.viewCount }}
              </span>
              <span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                {{ post.commentCount }}
              </span>
            </div>
            <div class="post-manage" v-if="auth.isAdmin || isPostAuthor">
              <el-button link type="primary" size="small" @click="editPost">编辑</el-button>
              <el-button link type="danger" size="small" @click="deletePostItem">删除</el-button>
            </div>
          </div>
        </div>

        <!-- 评论区 -->
        <div class="comment-section">
          <div class="comment-section-header">
            <h3>评论</h3>
            <span class="comment-count">{{ comments.length }}</span>
          </div>

          <!-- 发表评论 -->
          <div class="comment-input">
            <el-input v-model="newComment" type="textarea" :rows="3" placeholder="写下你的评论..." maxlength="2000" show-word-limit />
            <div class="comment-input-actions">
              <div ref="commentEmojiBtn" class="emoji-trigger" @click="emoji.open(commentEmojiBtn!, e => newComment += e)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
              </div>
              <el-button type="primary" size="small" :loading="commentSubmitting" @click="submitComment" :disabled="!newComment.trim()">发表评论</el-button>
            </div>
          </div>

          <!-- 评论列表 -->
          <div v-for="comment in comments" :key="comment.id" class="comment-item" :class="{ 'comment-pinned': comment.pinned }">
            <div class="comment-main">
              <div class="comment-body">
                <div class="comment-meta">
                  <span v-if="comment.pinned" class="pin-badge">置顶</span>
                  <span class="comment-user">{{ comment.username }}</span>
                  <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-indicators">
                  <span v-if="comment.likedByAuthor" class="indicator indicator--author">
                    <svg viewBox="0 0 24 24" fill="var(--accent)" stroke="none" width="12" height="12"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    作者赞过
                  </span>
                  <span v-if="comment.repliedByAuthor" class="indicator indicator--author">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                    作者回复过
                  </span>
                </div>
                <div class="comment-actions">
                  <button class="comment-action-btn" :class="{ active: comment.likedByMe }" @click="toggleCommentLikeItem(comment)">
                    <svg viewBox="0 0 24 24" fill="none" :stroke="comment.likedByMe ? 'var(--accent)' : 'currentColor'" stroke-width="2" width="14" height="14"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    {{ comment.likeCount || '' }}
                  </button>
                  <button class="comment-action-btn" @click="replyTo(comment.id)">回复</button>
                  <button v-if="isPostAuthor" class="comment-action-btn" @click="togglePinItem(comment.id)">
                    {{ comment.pinned ? '取消置顶' : '置顶' }}
                  </button>
                  <button v-if="auth.isAdmin || isPostAuthor" class="comment-action-btn danger" @click="deleteCommentItem(comment.id)">删除</button>
                </div>
              </div>
            </div>

            <!-- 子评论 -->
            <div v-if="comment.replies && comment.replies.length > 0" class="comment-replies">
              <div v-for="reply in comment.replies" :key="reply.id" class="comment-reply">
                <div class="comment-meta">
                  <span class="comment-user">{{ reply.username }}</span>
                  <span class="comment-time">{{ formatTime(reply.createTime) }}</span>
                </div>
                <p class="comment-text">{{ reply.content }}</p>
                <div class="comment-indicators">
                  <span v-if="reply.likedByAuthor" class="indicator indicator--author">
                    <svg viewBox="0 0 24 24" fill="var(--accent)" stroke="none" width="12" height="12"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    作者赞过
                  </span>
                  <span v-if="reply.repliedByAuthor" class="indicator indicator--author">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                    作者回复过
                  </span>
                </div>
                <div class="comment-actions">
                  <button class="comment-action-btn" :class="{ active: reply.likedByMe }" @click="toggleCommentLikeItem(reply)">
                    <svg viewBox="0 0 24 24" fill="none" :stroke="reply.likedByMe ? 'var(--accent)' : 'currentColor'" stroke-width="2" width="14" height="14"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    {{ reply.likeCount || '' }}
                  </button>
                  <button v-if="auth.isAdmin || isPostAuthor" class="comment-action-btn danger" @click="deleteCommentItem(reply.id)">删除</button>
                </div>
              </div>
            </div>

            <!-- 回复输入框 -->
            <div v-if="replyingTo === comment.id" class="reply-input">
              <el-input v-model="replyContent" type="textarea" :rows="2" placeholder="回复..." maxlength="1000" size="small" />
              <div class="reply-actions">
                <div ref="replyEmojiBtn" class="emoji-trigger" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
                </div>
                <el-button size="small" @click="replyingTo = null">取消</el-button>
                <el-button type="primary" size="small" :loading="commentSubmitting" @click="submitReply(comment.id)">回复</el-button>
              </div>
            </div>
          </div>

          <el-empty v-if="comments.length === 0" description="暂无评论，来说两句吧" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 编辑帖子弹窗 -->
    <el-dialog v-model="editVisible" title="编辑帖子" width="600px" destroy-on-close class="dialog-light" :append-to-body="true" :center="true">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="200" show-word-limit placeholder="请输入帖子标题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" style="width: 100%">
            <el-option label="讨论" value="DISCUSSION" />
            <el-option label="新闻" value="NEWS" />
            <el-option label="辩论" value="DEBATE" />
            <el-option label="预测" value="PREDICTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <div style="position: relative;">
            <el-input v-model="editForm.content" type="textarea" :rows="8" maxlength="5000" show-word-limit placeholder="分享你的观点..." />
            <div class="textarea-toolbar">
              <button ref="editEmojiBtn" class="emoji-trigger" @click.prevent="emoji.open(editEmojiBtn!, e => editForm.content += e)" title="插入表情">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
              </button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" placeholder="用逗号分隔，如：湖人,季后赛" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>

  <EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPost, fetchComments, createComment, deleteComment, deletePost, updatePost, togglePostLike, toggleCommentLike, toggleCommentPin } from '@/api/community'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'
import ShareButton from '@/components/ShareButton.vue'
import { toggleFavorite, fetchFavoritedPostIds } from '@/api/favorite'
import { recordBrowse } from '@/api/browseHistory'
import { useAuthStore } from '@/stores/auth'
import type { Post, Comment, PostRequest } from '@/api/community'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')
const replyContent = ref('')
const replyingTo = ref<number | null>(null)
const commentSubmitting = ref(false)
const isFavorited = ref(false)
const emoji = useEmojiPicker()
const commentEmojiBtn = ref<HTMLElement | null>(null)
const replyEmojiBtn = ref<HTMLElement | null>(null)
const editEmojiBtn = ref<HTMLElement | null>(null)
const editVisible = ref(false)
const editSubmitting = ref(false)
const editForm = ref<PostRequest>({ title: '', content: '', category: 'DISCUSSION', tags: '' })

const postId = computed(() => Number(route.query.id) || 0)
const currentUserId = computed(() => {
  try {
    const token = auth.token
    if (!token) return null
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.userId ?? null
  } catch { return null }
})
const isPostAuthor = computed(() => post.value?.userId != null && currentUserId.value === post.value.userId)
const postUrl = computed(() => `${window.location.origin}/community/post?id=${postId.value}`)

const categoryMap: Record<string, string> = { DISCUSSION: '讨论', NEWS: '新闻', DEBATE: '辩论', PREDICTION: '预测' }
const categoryClassMap: Record<string, string> = {
  DISCUSSION: 'tag-discussion', NEWS: 'tag-news', DEBATE: 'tag-debate', PREDICTION: 'tag-prediction',
}
function categoryLabel(c: string) { return categoryMap[c] || c }
function categoryClass(c: string) { return categoryClassMap[c] || '' }

function formatTime(t: string) {
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN')
}

async function loadPost() {
  if (!postId.value) return
  loading.value = true
  try {
    const { data } = await fetchPost(postId.value)
    post.value = data
    if (auth.token) {
      recordBrowse('POST', postId.value).catch(() => {})
    }
  } catch { ElMessage.error('加载帖子失败') }
  finally { loading.value = false }
}

async function loadComments() {
  if (!postId.value) return
  try {
    const { data } = await fetchComments(postId.value)
    comments.value = data
  } catch { /* ignore */ }
}

async function toggleLike() {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await togglePostLike(postId.value)
    if (post.value) {
      const liked = data.liked
      post.value.likedByMe = liked
      post.value.likeCount += liked ? 1 : -1
    }
  } catch { /* ignore */ }
}

async function toggleFavoritePost() {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    const { data } = await toggleFavorite('POST', postId.value)
    isFavorited.value = data.favorited
    ElMessage.success(data.favorited ? '已收藏' : '已取消收藏')
  } catch {
    ElMessage.error('收藏失败，请重试')
  }
}

async function checkFavoriteStatus() {
  if (!auth.token) return
  try {
    const { data } = await fetchFavoritedPostIds()
    isFavorited.value = data.includes(postId.value)
  } catch { /* ignore */ }
}

async function submitComment() {
  if (!newComment.value.trim()) return
  commentSubmitting.value = true
  try {
    await createComment({ postId: postId.value, content: newComment.value })
    newComment.value = ''
    ElMessage.success('评论成功')
    loadComments()
    if (post.value) post.value.commentCount++
  } catch { ElMessage.error('评论失败') }
  finally { commentSubmitting.value = false }
}

function replyTo(parentId: number) { replyingTo.value = parentId; replyContent.value = '' }

async function submitReply(parentId: number) {
  if (!replyContent.value.trim()) return
  commentSubmitting.value = true
  try {
    await createComment({ postId: postId.value, content: replyContent.value, parentId })
    replyContent.value = ''
    replyingTo.value = null
    ElMessage.success('回复成功')
    loadComments()
  } catch { ElMessage.error('回复失败') }
  finally { commentSubmitting.value = false }
}

async function deleteCommentItem(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' })
    await deleteComment(id)
    ElMessage.success('已删除')
    loadComments()
  } catch { /* cancel */ }
}

async function toggleCommentLikeItem(comment: Comment) {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    await toggleCommentLike(comment.id)
    loadComments()
  } catch { /* ignore */ }
}

async function togglePinItem(commentId: number) {
  if (!isPostAuthor.value && !auth.isAdmin) { ElMessage.warning('只有帖子作者可以置顶评论'); return }
  try {
    await toggleCommentPin(commentId)
    loadComments()
  } catch { /* ignore */ }
}

async function deletePostItem() {
  try {
    await ElMessageBox.confirm('确定删除该帖子？', '提示', { type: 'warning' })
    await deletePost(postId.value)
    ElMessage.success('已删除')
    router.push('/community')
  } catch { /* cancel */ }
}

function editPost() {
  if (!post.value) return
  editForm.value = {
    title: post.value.title,
    content: post.value.content,
    category: post.value.category,
    tags: post.value.tags || ''
  }
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.value.title.trim() || !editForm.value.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  editSubmitting.value = true
  try {
    const { data } = await updatePost(postId.value, editForm.value)
    if (post.value) {
      post.value.title = data.title
      post.value.content = data.content
      post.value.category = data.category
      post.value.tags = data.tags
    }
    editVisible.value = false
    ElMessage.success('编辑成功')
  } catch {
    ElMessage.error('编辑失败')
  } finally {
    editSubmitting.value = false
  }
}

watch(postId, () => {
  if (postId.value) {
    loadPost()
    loadComments()
    checkFavoriteStatus()
  }
}, { immediate: false })

onMounted(() => { loadPost(); loadComments(); checkFavoriteStatus() })
</script>

<style scoped>
.page { max-width: 1200px; margin: 0 auto; padding: 0 20px; min-height: calc(100vh - 108px); position: relative; animation: pageFadeIn 0.3s ease forwards; opacity: 0; transform: translateY(8px); }
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.back-row { margin-bottom: 16px; }
.back-btn { color: var(--text-muted) !important; font-size: 13px !important; }

/* 帖子卡片 */
.post-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  margin-bottom: 24px;
  overflow: hidden;
  position: relative;
}
.post-card-top {
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light));
}
.post-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding: 20px 24px 0; }
.post-header-left { display: flex; align-items: center; gap: 8px; }
.post-category-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.tag-discussion { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.tag-news { background: var(--accent-lighter); color: var(--accent); }
.tag-debate { background: rgba(245, 158, 11, 0.1); color: #d97706; }
.tag-prediction { background: var(--purple-dim); color: var(--purple); }

.top-badge, .featured-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 700;
}
.top-badge { background: var(--danger); color: #fff; }
.featured-badge { background: var(--warning); color: #fff; }

.post-time { font-size: 12px; color: var(--text-dim); }

/* 作者信息栏 */
.post-author-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  margin-bottom: 4px;
}
.author-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent-lighter);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}
.author-info { display: flex; flex-direction: column; gap: 2px; }
.author-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.author-time { font-size: 12px; color: var(--text-dim); }

.post-title { margin: 0 0 16px; padding: 0 24px; font-size: 22px; color: var(--text-primary); font-weight: 700; line-height: 1.4; font-family: var(--font-heading); }
.post-content { font-size: 15px; color: var(--text-secondary); line-height: 1.9; white-space: pre-wrap; word-break: break-word; margin-bottom: 16px; padding: 0 24px; }
.post-tags { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; padding: 0 24px; }
.tag-item {
  --el-tag-bg-color: rgba(30, 41, 59, 0.72) !important;
  --el-tag-border-color: rgba(100, 116, 139, 0.28) !important;
  --el-tag-text-color: var(--text-secondary) !important;
  border-radius: 5px !important;
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.76), rgba(15, 23, 42, 0.86)) !important;
  border-color: rgba(100, 116, 139, 0.28) !important;
  color: var(--text-secondary) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.tag-item:hover {
  --el-tag-border-color: rgba(232, 93, 38, 0.38) !important;
  --el-tag-text-color: var(--accent-light) !important;
  border-color: rgba(232, 93, 38, 0.38) !important;
  color: var(--accent-light) !important;
  background: rgba(232, 93, 38, 0.12) !important;
}

.post-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  border-top: 1px solid var(--border-light);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.action-btn:hover { border-color: var(--border-medium); color: var(--text-primary); }
.action-btn.active { border-color: var(--accent); color: var(--accent); background: var(--accent-lighter); }

.post-stats {
  display: flex;
  gap: 12px;
  margin-left: auto;
  font-size: 12px;
  color: var(--text-dim);
}
.post-stats span { display: inline-flex; align-items: center; gap: 3px; }
.post-stats svg { opacity: 0.4; }
.post-manage { display: flex; gap: 4px; margin-left: 8px; }

/* 评论区 */
.comment-section { margin-top: 8px; }
.comment-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.comment-section-header h3 { margin: 0; font-size: 16px; color: var(--text-primary); font-weight: 600; }
.comment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--accent-lighter);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.comment-input { margin-bottom: 20px; }
.comment-input-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 8px; }
.emoji-trigger {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  color: var(--text-muted);
  transition: all 0.15s;
}
.emoji-trigger:hover { background: var(--bg-hover); color: var(--text-primary); }

.comment-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-light);
  transition: background 0.15s;
}
.comment-item:hover { background: var(--bg-hover); border-radius: var(--radius-sm); }
.comment-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.comment-user { font-size: 13px; font-weight: 600; color: var(--accent); }
.comment-time { font-size: 12px; color: var(--text-dim); }
.comment-text { margin: 0 0 8px; font-size: 14px; color: var(--text-secondary); line-height: 1.6; }
.comment-actions { display: flex; gap: 8px; align-items: center; }

.comment-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-dim);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.comment-action-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.comment-action-btn.active { color: var(--accent); }
.comment-action-btn.danger:hover { color: var(--danger); }

.comment-pinned { border-left: 3px solid var(--accent); padding-left: 12px; }
.pin-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--accent-lighter);
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}

.comment-indicators { display: flex; gap: 8px; margin-bottom: 6px; }
.indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-dim);
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--bg-secondary);
}
.indicator--author { color: var(--accent); background: var(--accent-lighter); }

.comment-replies { margin-left: 32px; padding-left: 16px; border-left: 2px solid var(--border-light); }
.comment-reply { padding: 10px 0; border-bottom: 1px dashed var(--border-light); }
.comment-reply:last-child { border-bottom: none; }

.reply-input { margin-top: 10px; margin-left: 32px; }
.reply-actions { display: flex; gap: 8px; justify-content: flex-end; align-items: center; margin-top: 6px; }

.textarea-toolbar { position: absolute; bottom: 30px; right: 8px; z-index: 10; }

/* 浅色主题 */
[data-theme="light"] .post-card { border-color: var(--border-light); }
[data-theme="light"] .comment-item:hover { background: var(--bg-hover); }
[data-theme="light"] .comment-replies { border-left-color: var(--border-light); }
[data-theme="light"] .indicator--author { color: var(--accent); background: var(--accent-lighter); }
[data-theme="light"] .tag-item {
  --el-tag-bg-color: #F3F0EB !important;
  --el-tag-border-color: #E8E5E0 !important;
  --el-tag-text-color: var(--text-secondary) !important;
  background: #F3F0EB !important;
  border-color: #E8E5E0 !important;
  color: var(--text-secondary) !important;
  box-shadow: none;
}
</style>
