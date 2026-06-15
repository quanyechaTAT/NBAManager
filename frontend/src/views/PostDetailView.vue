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
      <!-- 返回 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="router.back()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回
        </el-button>
      </div>

      <div v-loading="loading" v-if="post">
        <!-- 帖子内容 -->
        <el-card shadow="never" class="post-card">
          <div class="post-header">
            <el-tag :type="categoryType(post.category)" size="small">{{ categoryLabel(post.category) }}</el-tag>
            <span class="post-time">{{ formatTime(post.createTime) }}</span>
          </div>
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-content">{{ post.content }}</div>
          <div class="post-tags" v-if="post.tags">
            <el-tag v-for="tag in post.tags.split(',')" :key="tag" size="small" type="info" effect="plain" class="tag-item">{{ tag.trim() }}</el-tag>
          </div>
          <div class="post-actions">
            <el-button :type="post.likedByMe ? 'primary' : ''" plain size="small" @click="toggleLike">
              {{ post.likedByMe ? '👍 已赞' : '👍 点赞' }} ({{ post.likeCount }})
            </el-button>
            <el-button v-if="auth.token" :type="isFavorited ? 'danger' : ''" plain size="small" @click="toggleFavoritePost">
              {{ isFavorited ? '★ 已收藏' : '☆ 收藏' }}
            </el-button>
            <ShareButton :url="postUrl" :title="post.title" :description="post.content" />
            <span class="post-stats">👁 {{ post.viewCount }} · 💬 {{ post.commentCount }} · ❤️ {{ post.likeCount }} · ⭐ {{ post.favoriteCount || 0 }}</span>
            <div class="post-manage" v-if="auth.isAdmin || isPostAuthor">
              <el-button link type="primary" size="small" @click="editPost">编辑</el-button>
              <el-button link type="danger" size="small" @click="deletePostItem">删除</el-button>
            </div>
          </div>
        </el-card>

        <!-- 评论区 -->
        <div class="comment-section">
          <h3>💬 评论 ({{ comments.length }})</h3>

          <!-- 发表评论 -->
          <div class="comment-input">
            <el-input v-model="newComment" type="textarea" :rows="3" placeholder="写下你的评论..." maxlength="2000" show-word-limit />
            <div class="comment-input-actions">
              <div class="emoji-trigger" @click="showEmojiPicker = !showEmojiPicker">
                <span class="emoji-icon">😀</span>
              </div>
              <EmojiPicker :visible="showEmojiPicker" @select="insertEmoji" @close="showEmojiPicker = false" />
              <el-button type="primary" size="small" :loading="commentSubmitting" @click="submitComment" :disabled="!newComment.trim()">发表评论</el-button>
            </div>
          </div>

          <!-- 评论列表 -->
          <div v-for="comment in comments" :key="comment.id" class="comment-item" :class="{ 'comment-pinned': comment.pinned }">
            <div class="comment-main">
              <div class="comment-body">
                <div class="comment-meta">
                  <span v-if="comment.pinned" class="pin-badge">📌 置顶</span>
                  <span class="comment-user">{{ comment.username }}</span>
                  <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-indicators">
                  <span v-if="comment.likedByAuthor" class="indicator indicator--like">❤️ 作者赞过</span>
                  <span v-if="comment.repliedByAuthor" class="indicator indicator--reply">💬 作者回复过</span>
                </div>
                <div class="comment-actions">
                  <el-button :type="comment.likedByMe ? 'primary' : ''" link size="small" @click="toggleCommentLikeItem(comment)">
                    {{ comment.likedByMe ? '👍 已赞' : '👍' }} {{ comment.likeCount || '' }}
                  </el-button>
                  <el-button link size="small" @click="replyTo(comment.id)">回复</el-button>
                  <el-button v-if="isPostAuthor" link size="small" @click="togglePinItem(comment.id)">
                    {{ comment.pinned ? '取消置顶' : '置顶' }}
                  </el-button>
                  <el-button v-if="auth.isAdmin || isPostAuthor" link size="small" type="danger" @click="deleteCommentItem(comment.id)">删除</el-button>
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
                  <span v-if="reply.likedByAuthor" class="indicator indicator--like">❤️ 作者赞过</span>
                  <span v-if="reply.repliedByAuthor" class="indicator indicator--reply">💬 作者回复过</span>
                </div>
                <div class="comment-actions">
                  <el-button :type="reply.likedByMe ? 'primary' : ''" link size="small" @click="toggleCommentLikeItem(reply)">
                    {{ reply.likedByMe ? '👍 已赞' : '👍' }} {{ reply.likeCount || '' }}
                  </el-button>
                  <el-button v-if="auth.isAdmin || isPostAuthor" link size="small" type="danger" @click="deleteCommentItem(reply.id)">删除</el-button>
                </div>
              </div>
            </div>

            <!-- 回复输入框 -->
            <div v-if="replyingTo === comment.id" class="reply-input">
              <el-input v-model="replyContent" type="textarea" :rows="2" placeholder="回复..." maxlength="1000" size="small" />
              <div class="reply-actions">
                <div class="emoji-trigger" @click="showReplyEmojiPicker = !showReplyEmojiPicker">
                  <span class="emoji-icon">😀</span>
                </div>
                <EmojiPicker :visible="showReplyEmojiPicker" @select="insertReplyEmoji" @close="showReplyEmojiPicker = false" />
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
              <button class="emoji-trigger" @click.prevent="showEditEmojiPicker = !showEditEmojiPicker" title="插入表情">😊</button>
            </div>
            <EmojiPicker :visible="showEditEmojiPicker" @select="insertEditEmoji" @close="showEditEmojiPicker = false" />
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
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPost, fetchComments, createComment, deleteComment, deletePost, updatePost, togglePostLike, toggleCommentLike, toggleCommentPin } from '@/api/community'
import EmojiPicker from '@/components/EmojiPicker.vue'
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
const showEmojiPicker = ref(false)
const showReplyEmojiPicker = ref(false)
const showEditEmojiPicker = ref(false)
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
const categoryTypeMap: Record<string, string> = { DISCUSSION: '', NEWS: 'success', DEBATE: 'warning', PREDICTION: 'info' }
function categoryLabel(c: string) { return categoryMap[c] || c }
function categoryType(c: string) { return (categoryTypeMap[c] || '') as any }

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
  } catch (e: any) {
    console.error('收藏帖子失败:', e?.response?.data || e?.message || e)
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
    // 重新加载评论以刷新作者赞过/回复过标识
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

function insertEmoji(emoji: string) {
  newComment.value += emoji
  showEmojiPicker.value = false
}

function insertReplyEmoji(emoji: string) {
  replyContent.value += emoji
  showReplyEmojiPicker.value = false
}

function insertEditEmoji(emoji: string) {
  editForm.value.content += emoji
  showEditEmojiPicker.value = false
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
.page { max-width: 900px; min-height: calc(100vh - 108px); position: relative; border-radius: var(--radius-lg); animation: pageFadeIn 0.4s var(--ease-smooth) forwards; opacity: 0; transform: translateY(12px); }
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.back-row { margin-bottom: 16px; }
.back-btn { color: var(--text-muted) !important; font-size: 13px !important; }

.post-card { background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; margin-bottom: 20px; position: relative; overflow: hidden; }
.post-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: linear-gradient(90deg, var(--purple), var(--accent)); border-radius: var(--radius-xl) var(--radius-xl) 0 0; }
.post-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.post-time { font-size: 12px; color: var(--text-dim); }
.post-title { margin: 0 0 16px; font-size: 24px; color: var(--text-primary); font-weight: 700; line-height: 1.4; font-family: var(--font-heading); letter-spacing: 0.3px; }
.post-content { font-size: 15px; color: var(--text-secondary); line-height: 1.9; white-space: pre-wrap; word-break: break-word; margin-bottom: 16px; letter-spacing: 0.2px; }
.post-tags { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; }
.tag-item { border-radius: 12px !important; }
.post-actions { display: flex; align-items: center; gap: 16px; padding-top: 16px; border-top: 1px solid var(--border-light); }
.post-stats { font-size: 13px; color: var(--text-dim); margin-left: auto; }
.post-manage { display: flex; gap: 4px; margin-left: 8px; }

.comment-section h3 { margin: 0 0 16px; font-size: 16px; color: var(--text-primary); }
.comment-input { margin-bottom: 20px; }
.comment-input-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 8px; position: relative; }
.emoji-trigger { cursor: pointer; padding: 4px; border-radius: var(--radius-sm); transition: all var(--duration-fast) var(--ease-smooth); }
.emoji-trigger:hover { background: var(--bg-hover); transform: scale(1.1); }
.emoji-icon { font-size: 20px; }

.comment-item { padding: 16px 0; border-bottom: 1px solid var(--border-light); transition: all var(--duration-fast) var(--ease-smooth); }
.comment-item:hover { background: rgba(108, 92, 231, 0.02); border-radius: var(--radius-md); padding-left: 8px; }
.comment-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.comment-user { font-size: 13px; font-weight: 600; color: var(--purple); transition: color var(--duration-fast) var(--ease-smooth); }
.comment-item:hover .comment-user { color: var(--purple-light); }
.comment-time { font-size: 12px; color: var(--text-dim); }
.comment-text { margin: 0 0 8px; font-size: 14px; color: var(--text-secondary); line-height: 1.6; }
.comment-actions { display: flex; gap: 8px; align-items: center; }

.comment-pinned { border-left: 3px solid var(--purple); padding-left: 12px; }
.pin-badge { font-size: 11px; color: var(--purple); font-weight: 600; background: var(--purple-dim); padding: 2px 8px; border-radius: 4px; margin-right: 6px; box-shadow: 0 0 8px var(--purple-glow); }

.comment-indicators { display: flex; gap: 10px; margin-bottom: 6px; }
.indicator { font-size: 11px; color: var(--text-dim); padding: 2px 8px; border-radius: 4px; background: var(--bg-hover); }
.indicator--like { color: #f56c6c; background: rgba(245, 108, 108, 0.1); }
.indicator--reply { color: var(--purple); background: var(--purple-dim); }

.comment-replies { margin-left: 32px; padding-left: 16px; border-left: 2px solid var(--purple-dim); }
.comment-reply { padding: 10px 0; border-bottom: 1px dashed var(--border-light); }
.comment-reply:last-child { border-bottom: none; }

.reply-input { margin-top: 10px; margin-left: 32px; }
.reply-actions { display: flex; gap: 8px; justify-content: flex-end; align-items: center; margin-top: 6px; position: relative; }
</style>
