<template>
  <div class="page">
    <ProfileHero
      :username="auth.username ?? ''"
      :avatar-url="avatarUrl"
      :initial="auth.username?.charAt(0)?.toUpperCase() ?? '?'"
      :is-admin="auth.isAdmin"
      :uploading="avatarUploading"
      @avatar-click="showAvatarPreview = true"
      @upload-click="triggerAvatarUpload"
      @reset-avatar="resetAvatar"
    />
    <input ref="avatarInput" type="file" accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif" style="display:none" @change="onAvatarChange" />

    <!-- 头像放大预览 -->
    <Teleport to="body">
      <Transition name="headshot-fade">
        <div v-if="showAvatarPreview" class="headshot-overlay" @click.self="showAvatarPreview = false">
          <div class="headshot-dialog">
            <div class="headshot-dialog-header">
              <span class="headshot-dialog-title">{{ auth.username }} 的头像</span>
              <button class="headshot-dialog-close" aria-label="关闭" @click="showAvatarPreview = false">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div class="headshot-dialog-body">
              <img v-if="avatarUrl" :src="avatarUrl" class="headshot-preview-img" />
              <div v-else class="avatar-placeholder-large">
                {{ auth.username?.charAt(0)?.toUpperCase() }}
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 导航条 -->
    <nav class="profile-nav" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.name"
        class="profile-nav__item"
        :class="{ 'is-active': activeTab === tab.name }"
        role="tab"
        :aria-selected="activeTab === tab.name"
        @click="activeTab = tab.name"
      >
        <ProfileTabLabel :icon="tab.icon" :label="tab.label" />
      </button>
    </nav>

    <!-- 内容面板 -->
    <div class="profile-content">
      <!-- 修改密码 -->
      <section v-if="activeTab === 'password'" class="profile-panel">
        <div class="profile-panel__inner">
          <h3 class="profile-panel__title">修改密码</h3>
          <p class="profile-panel__desc">更新您的账户密码，建议使用强密码。</p>
          <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="100px" class="pwd-form">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdLoading" @click="changePassword">保存</el-button>
            </el-form-item>
          </el-form>
        </div>
      </section>

      <!-- 关注的球员 -->
      <section v-if="activeTab === 'players'" class="profile-panel">
        <div v-loading="favLoading">
          <div class="fav-grid">
            <div v-for="p in followedPlayers" :key="p.id" class="fav-item" @click="router.push({ path: '/players/detail', query: { id: String(p.id) } })">
              <div class="fav-avatar">
                <img v-if="p.nbaPlayerId" :src="`https://cdn.nba.com/headshots/nba/latest/260x190/${p.nbaPlayerId}.png`" class="fav-avatar-img" @error="onAvatarError" />
                <span v-else>{{ p.name?.charAt(0) }}</span>
              </div>
              <div class="fav-info">
                <span class="fav-name">{{ p.name }}</span>
                <span class="fav-sub">{{ p.teamName }} · {{ p.position }}</span>
              </div>
            </div>
          </div>
          <AppEmpty v-if="!favLoading && followedPlayers.length === 0" description="暂无关注球员" />
        </div>
      </section>

      <!-- 关注的球队 -->
      <section v-if="activeTab === 'teams'" class="profile-panel">
        <div v-loading="favLoading">
          <div class="fav-grid">
            <div v-for="t in followedTeams" :key="t.id" class="fav-item" @click="router.push({ path: '/teams/detail', query: { name: t.name } })">
              <div class="fav-avatar fav-avatar--team">
                <img v-if="t.logoUrl" :src="t.logoUrl" class="fav-avatar-img" @error="(e: Event) => (e.target as HTMLImageElement).style.display='none'" />
                <span v-else>{{ t.name?.charAt(0) }}</span>
              </div>
              <div class="fav-info">
                <span class="fav-name">{{ t.name }}</span>
                <span class="fav-sub">{{ t.city }} · {{ t.conference }}</span>
              </div>
            </div>
          </div>
          <AppEmpty v-if="!favLoading && followedTeams.length === 0" description="暂无关注球队" />
        </div>
      </section>

      <!-- 收藏的帖子 -->
      <section v-if="activeTab === 'posts'" class="profile-panel">
        <div v-loading="favLoading">
          <div v-for="p in favoritedPosts" :key="p.id" class="fav-item fav-item--row" @click="router.push({ path: '/community/post', query: { id: String(p.id) } })">
            <div class="fav-avatar fav-avatar--post">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </div>
            <div class="fav-info">
              <span class="fav-name" :title="p.title">{{ p.title }}</span>
              <span class="fav-sub">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                {{ p.likeCount }}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                {{ p.commentCount }}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ p.viewCount }}
              </span>
            </div>
          </div>
          <AppEmpty v-if="!favLoading && favoritedPosts.length === 0" description="暂无收藏帖子" />
        </div>
      </section>

      <!-- 收藏的资讯 -->
      <section v-if="activeTab === 'favorites'" class="profile-panel">
        <div v-loading="newsLoading">
          <div v-for="n in favoritedNews" :key="n.id" class="fav-item fav-item--row" @click="router.push({ path: '/news', query: { newsId: String(n.id) } })">
            <div class="fav-info">
              <span class="fav-name" :title="n.title">{{ n.title }}</span>
              <span class="fav-sub">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                {{ n.viewCount }}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                {{ n.favoriteCount }}
              </span>
            </div>
          </div>
          <AppEmpty v-if="!newsLoading && favoritedNews.length === 0" description="暂无收藏资讯" />
        </div>
      </section>

      <!-- 浏览历史 -->
      <section v-if="activeTab === 'history'" class="profile-panel">
        <el-tabs v-model="historyTab" class="inner-tabs">
          <el-tab-pane label="资讯浏览历史" name="newsHistory">
            <div v-loading="historyLoading">
              <div class="history-actions" v-if="browsedNews.length > 0">
                <el-button type="danger" plain size="small" @click="clearHistory('NEWS')">清空资讯历史</el-button>
              </div>
              <div v-for="n in browsedNews" :key="n.id" class="fav-item fav-item--row" @click="router.push({ path: '/news', query: { newsId: String(n.id) } })">
                <div class="fav-info">
                  <span class="fav-name" :title="n.title">{{ n.title }}</span>
                  <span class="fav-sub">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    {{ n.viewCount }}
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                    {{ n.favoriteCount }}
                  </span>
                </div>
                <button class="history-delete" aria-label="删除记录" title="删除" @click.stop="removeHistory('NEWS', n.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                </button>
              </div>
              <AppEmpty v-if="!historyLoading && browsedNews.length === 0" description="暂无资讯浏览历史" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="帖子浏览历史" name="postHistory">
            <div v-loading="historyLoading">
              <div class="history-actions" v-if="browsedPosts.length > 0">
                <el-button type="danger" plain size="small" @click="clearHistory('POST')">清空帖子历史</el-button>
              </div>
              <div v-for="p in browsedPosts" :key="p.id" class="fav-item fav-item--row" @click="router.push({ path: '/community/post', query: { id: String(p.id) } })">
                <div class="fav-info">
                  <span class="fav-name" :title="p.title">{{ p.title }}</span>
                  <span class="fav-sub">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    {{ p.likeCount }}
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                    {{ p.commentCount }}
                  </span>
                </div>
                <button class="history-delete" aria-label="删除记录" title="删除" @click.stop="removeHistory('POST', p.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                </button>
              </div>
              <AppEmpty v-if="!historyLoading && browsedPosts.length === 0" description="暂无帖子浏览历史" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { changePasswordApi, uploadAvatarApi, resetAvatarApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { fetchTeams } from '@/api/team'
import { fetchPlayers } from '@/api/player'
import { fetchFollowedPlayerIds, fetchFollowedTeamIds, fetchFavoritedPostIds, fetchFavoritedNewsIds } from '@/api/favorite'
import { fetchBrowsedNewsIds, fetchBrowsedPostIds, deleteBrowseRecord, clearBrowseHistory } from '@/api/browseHistory'
import { fetchNews } from '@/api/news'
import { fetchPosts } from '@/api/community'
import type { Player, Team, GameNews } from '@/api/types'
import type { Post } from '@/api/community'
import AppEmpty from '@/components/common/AppEmpty.vue'
import ProfileHero from '@/components/profile/ProfileHero.vue'
import ProfileTabLabel from '@/components/profile/ProfileTabLabel.vue'
import { fetchAllPages } from '@/utils/fetchAllPages'

const router = useRouter()
const auth = useAuthStore()

const tabs = [
  { name: 'password', label: '修改密码', icon: 'lock' },
  { name: 'players', label: '关注的球员', icon: 'user' },
  { name: 'teams', label: '关注的球队', icon: 'shield' },
  { name: 'posts', label: '收藏的帖子', icon: 'file-text' },
  { name: 'favorites', label: '收藏的资讯', icon: 'bookmark' },
  { name: 'history', label: '浏览历史', icon: 'clock' },
]

const activeTab = ref('password')
const showAvatarPreview = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const favLoading = ref(false)
const newsLoading = ref(false)
const historyLoading = ref(false)
const avatarInput = ref<HTMLInputElement>()
const avatarUploading = ref(false)
const avatarUrl = computed(() => auth.avatarUrl)

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_r: any, value: string, callback: any) => {
      if (value !== pwdForm.newPassword) callback(new Error('两次密码不一致'))
      else callback()
    }, trigger: 'blur' }
  ],
}

const followedPlayers = ref<Player[]>([])
const followedTeams = ref<Team[]>([])
const favoritedPosts = ref<Post[]>([])
const favoritedNews = ref<GameNews[]>([])
const browsedNews = ref<GameNews[]>([])
const browsedPosts = ref<Post[]>([])
const historyTab = ref('newsHistory')

async function changePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdLoading.value = true
  try {
    const resp = await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    // 后端改密即换发携带新密码标记的 token：本地同步，避免当前会话被 401 踢出
    const fresh = (resp as unknown as { data?: { token?: string } }).data
    if (fresh?.token) auth.updateToken(fresh.token)
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally {
    pwdLoading.value = false
  }
}

const ALLOWED_MIME = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
const ALLOWED_EXT = new Set(['jpg', 'jpeg', 'png', 'webp', 'gif'])

function triggerAvatarUpload() {
  const input = avatarInput.value
  if (!input || avatarUploading.value) return
  input.value = ''
  input.click()
}

async function resetAvatar() {
  avatarUploading.value = true
  try {
    await resetAvatarApi()
    auth.setAvatar('')
    ElMessage.success('已恢复默认头像')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  } finally {
    avatarUploading.value = false
  }
}

async function onAvatarChange(e: Event) {
  const input = e.currentTarget as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 验证扩展名
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_EXT.has(ext)) {
    ElMessage.warning('仅支持 JPG、PNG、WEBP、GIF 格式')
    input.value = ''
    return
  }

  // 验证 MIME
  if (!ALLOWED_MIME.has(file.type)) {
    ElMessage.warning('头像文件类型不合法')
    input.value = ''
    return
  }

  // 验证大小
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    input.value = ''
    return
  }

  avatarUploading.value = true
  try {
    const { data } = await uploadAvatarApi(file)
    auth.setAvatar(data.avatarUrl)
    ElMessage.success('头像已更新')
  } catch (err: any) {
    const status = err?.response?.status
    const msg = err?.response?.data?.message || err?.response?.data?.error
    if (status === 413) {
      ElMessage.error('头像大小不能超过 2MB')
    } else if (status === 400) {
      ElMessage.error(msg || '头像文件不合法')
    } else if (status === 500) {
      ElMessage.error('头像保存失败，请稍后重试')
    } else if (!err?.response) {
      ElMessage.error('无法连接服务器')
    } else {
      ElMessage.error(msg || '头像上传失败')
    }
    console.error('头像上传失败:', err)
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

async function loadFollowedPlayers() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFollowedPlayerIds()
    if (ids.length === 0) { followedPlayers.value = []; return }
    const all = await fetchAllPages<Player>(p => fetchPlayers({ page: p, size: 100 }).then(r => r.data))
    followedPlayers.value = all.filter(p => ids.includes(p.id))
  } catch (e) { console.error('加载关注球员失败:', e) }
  finally { favLoading.value = false }
}

async function loadFollowedTeams() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFollowedTeamIds()
    if (ids.length === 0) { followedTeams.value = []; return }
    const all = await fetchAllPages<Team>(p => fetchTeams({ page: p, size: 100 }).then(r => r.data))
    followedTeams.value = all.filter(t => ids.includes(t.id))
  } catch (e) { console.error('加载关注球队失败:', e) }
  finally { favLoading.value = false }
}

async function loadFavoritedPosts() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFavoritedPostIds()
    if (ids.length === 0) { favoritedPosts.value = []; return }
    const all = await fetchAllPages<Post>(p => fetchPosts({ page: p, size: 100 }).then(r => r.data))
    favoritedPosts.value = all.filter(p => ids.includes(p.id))
  } catch (e) { console.error('加载收藏帖子失败:', e) }
  finally { favLoading.value = false }
}

async function loadFavoritedNews() {
  newsLoading.value = true
  try {
    const { data: ids } = await fetchFavoritedNewsIds()
    if (ids.length === 0) { favoritedNews.value = []; return }
    const all = await fetchAllPages<GameNews>(p => fetchNews({ page: p, size: 100 }).then(r => r.data))
    favoritedNews.value = all.filter(n => ids.includes(n.id))
  } catch (e) { console.error('加载收藏资讯失败:', e) }
  finally { newsLoading.value = false }
}

async function loadBrowseHistory() {
  historyLoading.value = true
  try {
    const [newsIds, postIds] = await Promise.all([
      fetchBrowsedNewsIds().then(res => res.data),
      fetchBrowsedPostIds().then(res => res.data)
    ])
    if (newsIds.length > 0) {
      const newsAll = await fetchAllPages<GameNews>(p => fetchNews({ page: p, size: 100 }).then(r => r.data))
      browsedNews.value = newsAll.filter(n => newsIds.includes(n.id))
    } else {
      browsedNews.value = []
    }
    if (postIds.length > 0) {
      const postsAll = await fetchAllPages<Post>(p => fetchPosts({ page: p, size: 100 }).then(r => r.data))
      browsedPosts.value = postsAll.filter(p => postIds.includes(p.id))
    } else {
      browsedPosts.value = []
    }
  } catch (e) { console.error('加载浏览历史失败:', e) }
  finally { historyLoading.value = false }
}

function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  const src = img.src
  if (src.includes('cdn.nba.com')) {
    const match = src.match(/\/(\d+)\.png/)
    if (match) {
      img.src = `https://ak-static.cms.nba.com/wp-content/uploads/headshots/nba/latest/260x190/${match[1]}.png`
      return
    }
  }
  img.style.display = 'none'
}

async function removeHistory(targetType: string, targetId: number) {
  try {
    await deleteBrowseRecord(targetType, targetId)
    if (targetType === 'NEWS') {
      browsedNews.value = browsedNews.value.filter(n => n.id !== targetId)
    } else {
      browsedPosts.value = browsedPosts.value.filter(p => p.id !== targetId)
    }
    ElMessage.success('已删除')
  } catch { ElMessage.error('删除失败') }
}

async function clearHistory(targetType: string) {
  try {
    await ElMessageBox.confirm(`确定清空${targetType === 'NEWS' ? '资讯' : '帖子'}浏览历史？`, '提示', { type: 'warning' })
    await clearBrowseHistory(targetType)
    if (targetType === 'NEWS') {
      browsedNews.value = []
    } else {
      browsedPosts.value = []
    }
    ElMessage.success('已清空')
  } catch { /* cancel */ }
}

function loadTabData() {
  if (activeTab.value === 'players') loadFollowedPlayers()
  else if (activeTab.value === 'teams') loadFollowedTeams()
  else if (activeTab.value === 'posts') loadFavoritedPosts()
  else if (activeTab.value === 'favorites') loadFavoritedNews()
  else if (activeTab.value === 'history') loadBrowseHistory()
}

watch(activeTab, loadTabData)
onMounted(loadTabData)
</script>

<style scoped>
/* ===== 页面 ===== */
.page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
  min-height: calc(100vh - 108px);
}

/* ===== 导航条 ===== */
.profile-nav {
  display: flex;
  gap: 0;
  margin: 20px 0;
  background: var(--profile-nav-bg);
  border: 1px solid var(--profile-panel-border);
  border-radius: var(--radius-xl);
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.profile-nav::-webkit-scrollbar { display: none; }

.profile-nav__item {
  flex: 1;
  min-width: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px 12px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-body);
  cursor: pointer;
  transition:
    color 0.18s var(--ease-smooth),
    background 0.18s var(--ease-smooth),
    transform 0.18s var(--ease-out-quart, ease);
  position: relative;
  white-space: nowrap;
  border-radius: 10px 10px 0 0;
}

.profile-nav__item:hover {
  color: var(--text-secondary);
  background: var(--bg-hover);
  transform: translateY(-1px);
}

.profile-nav__item.is-active {
  color: var(--accent);
  background: var(--profile-nav-active-bg);
  font-weight: 700;
}

.profile-nav__item.is-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 16px;
  right: 16px;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  border-radius: 1px 1px 0 0;
}

.profile-nav__item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

/* ===== 内容面板 ===== */
.profile-content {
  margin-bottom: 40px;
}

.profile-panel {
  background: var(--profile-panel-bg);
  border: 1px solid var(--profile-panel-border);
  border-radius: var(--radius-xl);
  padding: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition:
    border-color 0.18s ease,
    box-shadow 0.22s var(--ease-out-quart, ease);
}

.profile-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.5), transparent);
  opacity: 0.55;
  pointer-events: none;
}

.profile-panel:hover {
  box-shadow: var(--shadow-md);
}

.profile-panel__inner {
  max-width: 820px;
}

.profile-panel__title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.profile-panel__desc {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--text-muted);
}

.pwd-form {
  max-width: 480px;
}

/* ===== 收藏/历史列表 ===== */
.fav-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.fav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  border: 1px solid transparent;
}

.fav-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-light);
}

.fav-item--row {
  border-bottom: 1px solid var(--border-light);
  border-radius: 0;
  margin-bottom: 0;
}

.fav-item--row:last-child {
  border-bottom: none;
}

.fav-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--accent-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
  flex-shrink: 0;
  overflow: hidden;
  border: 2px solid var(--border-light);
  transition: border-color var(--duration-fast) var(--ease-smooth);
}

.fav-item:hover .fav-avatar {
  border-color: var(--accent);
}

.fav-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fav-avatar--team {
  background: var(--cyan-dim);
  color: var(--cyan);
}

.fav-avatar--post {
  background: var(--green-dim);
  color: var(--green);
}

.fav-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.fav-name {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
  transition: color var(--duration-fast) var(--ease-smooth);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fav-item:hover .fav-name {
  color: var(--accent);
}

.fav-sub {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  letter-spacing: 0.2px;
}

.fav-sub svg {
  opacity: 0.6;
}

/* ===== 浏览历史 ===== */
.history-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.history-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-dim);
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--duration-fast) var(--ease-smooth);
  opacity: 0;
}

.fav-item:hover .history-delete {
  opacity: 1;
}

.history-delete:hover {
  background: rgba(239,68,68,0.08);
  border-color: rgba(239,68,68,0.2);
  color: var(--danger);
}

.history-delete:focus-visible {
  opacity: 1;
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.history-delete:focus-visible {
  outline: 2px solid var(--danger);
  outline-offset: 2px;
}

/* ===== 头像预览弹窗 ===== */
.headshot-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
}

.headshot-dialog {
  background: var(--bg-card);
  border: 1px solid var(--border-medium);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-dialog);
  overflow: hidden;
  animation: headshotZoomIn 0.3s var(--ease-spring) forwards;
  transform: scale(0.95);
}

@keyframes headshotZoomIn {
  to { transform: scale(1); }
}

.headshot-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: var(--accent-lighter);
}

.headshot-dialog-title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.headshot-dialog-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.headshot-dialog-close:hover {
  background: var(--danger);
  border-color: var(--danger);
  color: var(--text-inverse);
}

.headshot-dialog-close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.headshot-dialog-body {
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.headshot-preview-img {
  max-width: 400px;
  max-height: 500px;
  object-fit: contain;
  border-radius: var(--radius-lg);
}

.avatar-placeholder-large {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: var(--accent-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80px;
  font-weight: 700;
  color: var(--accent);
}

.headshot-fade-enter-active { transition: opacity 0.25s var(--ease-smooth); }
.headshot-fade-leave-active { transition: opacity 0.2s var(--ease-smooth); }
.headshot-fade-enter-from, .headshot-fade-leave-to { opacity: 0; }

/* ===== 内部 Tabs ===== */
:deep(.inner-tabs .el-tabs__header) {
  margin: 0 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
}

:deep(.inner-tabs .el-tabs__active-bar) {
  background: var(--accent);
  height: 2px;
  border-radius: 1px;
}

:deep(.inner-tabs .el-tabs__item) {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
}

:deep(.inner-tabs .el-tabs__item.is-active) {
  color: var(--accent);
  font-weight: 600;
}

/* ===== 响应式 ===== */
@media (max-width: 1199px) and (min-width: 769px) {
  .fav-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .profile-nav {
    margin: 16px 0;
    border-radius: var(--radius-lg);
  }

  .profile-nav__item {
    min-width: 120px;
    padding: 12px 10px;
    font-size: 12px;
  }

  .profile-panel {
    padding: 16px;
  }

  .fav-grid {
    grid-template-columns: 1fr;
  }

  .pwd-form :deep(.el-form-item) {
    display: block;
    margin-bottom: 16px;
  }

  .pwd-form :deep(.el-form-item__label) {
    display: flex;
    width: auto !important;
    height: auto;
    justify-content: flex-start;
    text-align: left;
    padding: 0 0 6px;
  }

  .pwd-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
    width: 100%;
  }
}

@media (max-width: 480px) {
  .page {
    padding: 12px;
  }

  .profile-panel {
    padding: 14px;
  }

  .fav-item {
    padding: 10px;
  }

  .fav-avatar {
    width: 38px;
    height: 38px;
  }
}

/* ===== Reduced Motion ===== */
@media (prefers-reduced-motion: reduce) {
  .headshot-dialog {
    animation: none;
    transform: scale(1);
  }
  .fav-item:hover {
    transform: none;
  }
}
</style>
