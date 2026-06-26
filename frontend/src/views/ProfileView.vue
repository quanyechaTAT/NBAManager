<template>
  <div class="page animated-bg">
    <div class="bg-particles">
      <div class="particle"></div><div class="particle"></div>
      <div class="particle"></div><div class="particle"></div>
    </div>
    <div class="bg-grid"></div>

    <div class="page-inner stagger-in">
      <!-- 用户信息卡片 -->
      <el-card shadow="never" class="profile-card">
        <div class="profile-header">
          <div class="profile-avatar-wrapper" @click="showAvatarPreview = true">
            <div class="profile-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" />
              <span v-else>{{ auth.username?.charAt(0)?.toUpperCase() }}</span>
            </div>
            <div class="avatar-zoom-hint">🔍</div>
          </div>
          <div class="profile-info">
            <div class="profile-name-row">
              <h2>{{ auth.username }}</h2>
              <el-button size="small" plain @click="triggerAvatarUpload" class="change-avatar-btn">
                📷 更换头像
              </el-button>
            </div>
            <el-tag type="info" size="small">用户</el-tag>
            <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarChange" />
          </div>
        </div>
      </el-card>

      <!-- 头像放大预览 -->
      <Teleport to="body">
        <Transition name="headshot-fade">
          <div v-if="showAvatarPreview" class="headshot-overlay" @click.self="showAvatarPreview = false">
            <div class="headshot-dialog">
              <div class="headshot-dialog-header">
                <span class="headshot-dialog-title">{{ auth.username }} 的头像</span>
                <button class="headshot-dialog-close" @click="showAvatarPreview = false">✕</button>
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

      <el-tabs v-model="activeTab" class="profile-tabs">
        <!-- 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <el-card shadow="never" class="tab-card">
            <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="100px">
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
          </el-card>
        </el-tab-pane>

        <!-- 关注的球员 -->
        <el-tab-pane label="关注的球员" name="players">
          <el-card shadow="never" class="tab-card">
            <div v-loading="favLoading">
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
              <el-empty v-if="!favLoading && followedPlayers.length === 0" description="暂无关注球员" />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 关注的球队 -->
        <el-tab-pane label="关注的球队" name="teams">
          <el-card shadow="never" class="tab-card">
            <div v-loading="favLoading">
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
              <el-empty v-if="!favLoading && followedTeams.length === 0" description="暂无关注球队" />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 收藏的帖子 -->
        <el-tab-pane label="收藏的帖子" name="posts">
          <el-card shadow="never" class="tab-card">
            <div v-loading="favLoading">
              <div v-for="p in favoritedPosts" :key="p.id" class="fav-item" @click="router.push({ path: '/community/post', query: { id: String(p.id) } })">
                <div class="fav-avatar fav-avatar--post">💬</div>
                <div class="fav-info">
                  <span class="fav-name">{{ p.title }}</span>
                  <span class="fav-sub">👍 {{ p.likeCount }} · 💬 {{ p.commentCount }} · 👁 {{ p.viewCount }}</span>
                </div>
              </div>
              <el-empty v-if="!favLoading && favoritedPosts.length === 0" description="暂无收藏帖子" />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 收藏的资讯 -->
        <el-tab-pane label="收藏的资讯" name="favorites">
          <el-card shadow="never" class="tab-card">
            <div v-loading="newsLoading">
              <div v-for="n in favoritedNews" :key="n.id" class="fav-item" @click="router.push({ path: '/news', query: { newsId: String(n.id) } })">
                <div class="fav-info" style="flex:1">
                  <span class="fav-name">{{ n.title }}</span>
                  <span class="fav-sub">👁 {{ n.viewCount }} · ❤️ {{ n.favoriteCount }}</span>
                </div>
              </div>
              <el-empty v-if="!newsLoading && favoritedNews.length === 0" description="暂无收藏资讯" />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 浏览历史 -->
        <el-tab-pane label="浏览历史" name="history">
          <el-card shadow="never" class="tab-card">
            <el-tabs v-model="historyTab" class="inner-tabs">
              <el-tab-pane label="资讯浏览历史" name="newsHistory">
                <div v-loading="historyLoading">
                  <div class="history-actions" v-if="browsedNews.length > 0">
                    <el-button type="danger" plain size="small" @click="clearHistory('NEWS')">清空资讯历史</el-button>
                  </div>
                  <div v-for="n in browsedNews" :key="n.id" class="fav-item" @click="router.push({ path: '/news', query: { newsId: String(n.id) } })">
                    <div class="fav-info" style="flex:1">
                      <span class="fav-name">{{ n.title }}</span>
                      <span class="fav-sub">👁 {{ n.viewCount }} · ❤️ {{ n.favoriteCount }}</span>
                    </div>
                    <el-button type="danger" link size="small" @click.stop="removeHistory('NEWS', n.id)">删除</el-button>
                  </div>
                  <el-empty v-if="!historyLoading && browsedNews.length === 0" description="暂无资讯浏览历史" />
                </div>
              </el-tab-pane>
              <el-tab-pane label="帖子浏览历史" name="postHistory">
                <div v-loading="historyLoading">
                  <div class="history-actions" v-if="browsedPosts.length > 0">
                    <el-button type="danger" plain size="small" @click="clearHistory('POST')">清空帖子历史</el-button>
                  </div>
                  <div v-for="p in browsedPosts" :key="p.id" class="fav-item" @click="router.push({ path: '/community/post', query: { id: String(p.id) } })">
                    <div class="fav-info" style="flex:1">
                      <span class="fav-name">{{ p.title }}</span>
                      <span class="fav-sub">👍 {{ p.likeCount }} · 💬 {{ p.commentCount }}</span>
                    </div>
                    <el-button type="danger" link size="small" @click.stop="removeHistory('POST', p.id)">删除</el-button>
                  </div>
                  <el-empty v-if="!historyLoading && browsedPosts.length === 0" description="暂无帖子浏览历史" />
                </div>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { changePasswordApi } from '@/api/auth'
import { fetchTeams } from '@/api/team'
import { fetchPlayers } from '@/api/player'
import { fetchFollowedPlayerIds, fetchFollowedTeamIds, fetchFavoritedPostIds, fetchFavoritedNewsIds } from '@/api/favorite'
import { fetchBrowsedNewsIds, fetchBrowsedPostIds, deleteBrowseRecord, clearBrowseHistory } from '@/api/browseHistory'
import { fetchNews } from '@/api/news'
import { fetchPosts } from '@/api/community'
import type { Player, Team, GameNews } from '@/api/types'
import type { Post } from '@/api/community'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('password')
const showAvatarPreview = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const favLoading = ref(false)
const newsLoading = ref(false)
const historyLoading = ref(false)
const avatarInput = ref<HTMLInputElement>()
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
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
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

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

function onAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = (ev) => {
    const url = ev.target?.result as string
    auth.setAvatar(url)
    ElMessage.success('头像已更新')
  }
  reader.readAsDataURL(file)
}

async function loadFollowedPlayers() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFollowedPlayerIds()
    if (ids.length === 0) { followedPlayers.value = []; return }
    const { data } = await fetchPlayers({ page: 0, size: 1000 })
    followedPlayers.value = data.content.filter(p => ids.includes(p.id))
  } catch (e) { console.error('加载关注球员失败:', e) }
  finally { favLoading.value = false }
}

async function loadFollowedTeams() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFollowedTeamIds()
    if (ids.length === 0) { followedTeams.value = []; return }
    const { data } = await fetchTeams({ page: 0, size: 1000 })
    followedTeams.value = data.content.filter(t => ids.includes(t.id))
  } catch (e) { console.error('加载关注球队失败:', e) }
  finally { favLoading.value = false }
}

async function loadFavoritedPosts() {
  favLoading.value = true
  try {
    const { data: ids } = await fetchFavoritedPostIds()
    if (ids.length === 0) { favoritedPosts.value = []; return }
    const { data } = await fetchPosts({ page: 0, size: 1000 })
    favoritedPosts.value = data.content.filter(p => ids.includes(p.id))
  } catch (e) { console.error('加载收藏帖子失败:', e) }
  finally { favLoading.value = false }
}

async function loadFavoritedNews() {
  newsLoading.value = true
  try {
    const { data: ids } = await fetchFavoritedNewsIds()
    if (ids.length === 0) { favoritedNews.value = []; return }
    const { data } = await fetchNews({ page: 0, size: 1000 })
    favoritedNews.value = data.content.filter(n => ids.includes(n.id))
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
      const { data } = await fetchNews({ page: 0, size: 1000 })
      browsedNews.value = data.content.filter(n => newsIds.includes(n.id))
    } else {
      browsedNews.value = []
    }
    if (postIds.length > 0) {
      const { data } = await fetchPosts({ page: 0, size: 1000 })
      browsedPosts.value = data.content.filter(p => postIds.includes(p.id))
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
.page { max-width: 900px; margin: 0 auto; padding: 0 24px; min-height: calc(100vh - 108px); position: relative; border-radius: var(--radius-lg); animation: pageFadeIn 0.4s var(--ease-smooth) forwards; opacity: 0; transform: translateY(12px); }
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }
.profile-card { background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; margin-bottom: 20px; position: relative; overflow: hidden; }
.profile-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: linear-gradient(90deg, var(--purple), var(--accent), var(--cyan)); border-radius: var(--radius-xl) var(--radius-xl) 0 0; }
.profile-header { display: flex; align-items: center; gap: 20px; }
.profile-avatar-wrapper { position: relative; cursor: pointer; }
.profile-avatar { width: 72px; height: 72px; border-radius: 50%; background: linear-gradient(135deg, var(--purple), #5A4BD1); display: flex; align-items: center; justify-content: center; font-size: 28px; font-weight: 700; color: #fff; flex-shrink: 0; overflow: hidden; border: 3px solid var(--border-light); transition: all var(--duration-normal) var(--ease-smooth); }
.profile-avatar-wrapper:hover .profile-avatar { border-color: var(--purple); box-shadow: 0 0 20px var(--purple-glow); transform: scale(1.05); }
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-zoom-hint {
  position: absolute;
  bottom: 2px;
  right: 2px;
  font-size: 14px;
  background: rgba(0,0,0,0.6);
  border-radius: 50%;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-smooth);
}
.profile-avatar-wrapper:hover .avatar-zoom-hint { opacity: 1; }
.profile-name-row { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.profile-info h2 { margin: 0; font-size: 22px; color: var(--text-primary); font-family: var(--font-heading); font-weight: 700; letter-spacing: 0.3px; }
.change-avatar-btn { font-size: 12px !important; }

/* 头像预览弹窗 */
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
  box-shadow: 0 32px 100px rgba(0, 0, 0, 0.8);
  overflow: hidden;
  animation: headshotZoomIn 0.3s var(--ease-spring) forwards;
  transform: scale(0.95);
}
@keyframes headshotZoomIn { to { transform: scale(1); } }
.headshot-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: linear-gradient(135deg, var(--accent-lighter), transparent);
}
.headshot-dialog-title { font-family: var(--font-heading); font-size: 18px; font-weight: 700; color: var(--text-primary); }
.headshot-dialog-close {
  width: 32px; height: 32px; border-radius: 50%;
  border: 1px solid var(--border-light); background: var(--bg-hover);
  color: var(--text-secondary); font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}
.headshot-dialog-close:hover { background: var(--danger); border-color: var(--danger); color: #fff; transform: rotate(90deg); }
.headshot-dialog-body { padding: 24px; display: flex; justify-content: center; align-items: center; }
.headshot-preview-img { max-width: 400px; max-height: 500px; object-fit: contain; border-radius: var(--radius-lg); }
.avatar-placeholder-large {
  width: 200px; height: 200px; border-radius: 50%;
  background: linear-gradient(135deg, var(--purple), #5A4BD1);
  display: flex; align-items: center; justify-content: center;
  font-size: 80px; font-weight: 700; color: #fff;
}
.headshot-fade-enter-active { transition: opacity 0.25s var(--ease-smooth); }
.headshot-fade-leave-active { transition: opacity 0.2s var(--ease-smooth); }
.headshot-fade-enter-from, .headshot-fade-leave-to { opacity: 0; }
.profile-tabs { margin-bottom: 16px; }
:deep(.profile-tabs .el-tabs__header) { margin: 0; }
:deep(.profile-tabs .el-tabs__active-bar) { background: var(--purple); height: 3px; border-radius: 2px; }
:deep(.profile-tabs .el-tabs__item.is-active) { color: var(--purple); font-weight: 600; }
.tab-card { background: var(--bg-card) !important; border: 1px solid var(--border-light) !important; border-radius: var(--radius-xl) !important; position: relative; overflow: hidden; }
.tab-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: linear-gradient(90deg, var(--purple), var(--accent)); border-radius: var(--radius-xl) var(--radius-xl) 0 0; }
.fav-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-bottom: 1px solid var(--border-light); cursor: pointer; transition: all var(--duration-fast) var(--ease-smooth); border-radius: var(--radius-md); margin-bottom: 4px; }
.fav-item:last-child { border-bottom: none; }
.fav-item:hover { background: var(--bg-hover); transform: translateX(4px); }
.fav-item:hover .fav-name { color: var(--purple); }
.fav-avatar { width: 44px; height: 44px; border-radius: 50%; background: var(--purple-dim); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; color: var(--purple); flex-shrink: 0; overflow: hidden; border: 2px solid var(--border-light); transition: all var(--duration-fast) var(--ease-smooth); }
.fav-item:hover .fav-avatar { border-color: var(--purple); box-shadow: 0 0 12px var(--purple-glow); }
.fav-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.fav-avatar--team { background: var(--cyan-dim); color: var(--cyan); }
.fav-avatar--post { background: var(--green-dim); color: var(--green); font-size: 18px; }
.fav-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.fav-name { font-size: 14px; color: var(--text-primary); font-weight: 500; transition: color var(--duration-fast) var(--ease-smooth); }
.fav-sub { font-size: 12px; color: var(--text-dim); letter-spacing: 0.2px; }
.history-actions { display: flex; justify-content: flex-end; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border-light); }
</style>
