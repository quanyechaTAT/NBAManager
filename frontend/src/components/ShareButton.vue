<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="top"
    :width="320"
    trigger="click"
    :show-arrow="false"
    popper-class="share-popover"
  >
    <template #reference>
      <el-button class="share-trigger" plain size="small">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
          <circle cx="18" cy="5" r="3" />
          <circle cx="6" cy="12" r="3" />
          <circle cx="18" cy="19" r="3" />
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
        </svg>
        分享
      </el-button>
    </template>

    <div class="share-panel">
      <div class="share-panel-title">分享资讯</div>
      <div class="share-preview">
        <div class="share-preview-title">{{ title }}</div>
        <div v-if="previewDescription" class="share-preview-desc">{{ previewDescription }}</div>
      </div>
      <div class="share-options">
        <button class="share-option" type="button" @click="copyLink">
          <span class="share-option-icon share-option-icon--link">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
            </svg>
          </span>
          <span>{{ copied ? '已复制' : '复制链接' }}</span>
        </button>

        <button class="share-option" type="button" @click="shareWithSystem">
          <span class="share-option-icon share-option-icon--system">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M12 16V4" />
              <path d="m7 9 5-5 5 5" />
              <path d="M20 16v4H4v-4" />
            </svg>
          </span>
          <span>系统分享</span>
        </button>

        <button class="share-option" type="button" @click="shareToWeibo">
          <span class="share-option-icon share-option-icon--weibo">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M10.098 20.323c-3.977.391-7.414-1.406-7.672-4.02-.259-2.609 2.759-5.047 6.74-5.441 3.979-.394 7.413 1.404 7.671 4.018.259 2.6-2.759 5.049-6.739 5.443zm-2.646-7.655c-3.054.51-2.863 4.291-.024 5.451 2.987 1.212 6.289-.184 6.419-3.258.123-2.881-2.957-3.796-6.395-2.193zM20.695 7.679c-.375-1.137-1.574-1.681-2.704-1.441-1.144.243-1.91 1.36-1.676 2.503.236 1.133 1.442 1.842 2.599 1.551 1.149-.291 1.872-1.42 1.781-2.613z" />
              <path d="M17.636 8.183c-.141-.433-.593-.658-1.023-.554-.43.105-.698.548-.562.977.048.135.067.276.053.416-.141.945-.959 1.687-2.13 1.695-1.179.008-2.143-.777-2.15-1.755-.006-.83.664-1.522 1.583-1.601.316-.027.637.018.917.131-.194-.586-.354-1.19-.343-1.511.038-1.072.866-1.924 1.95-1.938 1.084-.015 1.961.823 1.981 1.896.01.548-.2 1.145-.413 1.69l.105.174c.473.725.619 1.608.448 2.468l-.026.002z" />
            </svg>
          </span>
          <span>微博</span>
        </button>

        <button class="share-option" type="button" @click="copyForWechat">
          <span class="share-option-icon share-option-icon--wechat">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348z" />
              <path d="M16.938 8.858c-4.131.285-7.062 2.912-7.062 6.122 0 3.382 3.086 6.113 6.886 6.113.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.49.49 0 0 1 .178-.554C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-7.062-6.122z" />
            </svg>
          </span>
          <span>微信</span>
        </button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { buildShareText, buildWeiboShareUrl, getSharePreviewDescription, normalizeShareUrl } from '@/utils/share'

const props = defineProps<{
  url: string
  title: string
  description?: string
}>()

const copied = ref(false)
const popoverVisible = ref(false)

const absoluteUrl = computed(() => normalizeShareUrl(props.url))
const sharePayload = computed(() => ({
  title: props.title,
  description: props.description,
  url: absoluteUrl.value,
}))
const previewDescription = computed(() => getSharePreviewDescription(props.title, props.description))

async function writeClipboard(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

async function copyText(text: string, message: string) {
  try {
    await writeClipboard(text)
    copied.value = true
    ElMessage.success(message)
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    ElMessage.error('复制失败，请手动复制链接')
  }
}

async function copyLink() {
  await copyText(absoluteUrl.value, '资讯链接已复制')
}

async function copyForWechat() {
  await copyText(buildShareText(sharePayload.value), '分享内容已复制，可粘贴到微信发送')
}

async function shareWithSystem() {
  const payload = sharePayload.value

  if (!navigator.share) {
    await copyText(buildShareText(payload), '当前浏览器不支持系统分享，已复制分享内容')
    return
  }

  try {
    await navigator.share({
      title: payload.title,
      text: payload.description || payload.title,
      url: payload.url,
    })
    popoverVisible.value = false
  } catch {
    // User cancelled native share; keep the popover open without noisy error.
  }
}

function shareToWeibo() {
  window.open(buildWeiboShareUrl(sharePayload.value), '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.share-trigger {
  --el-button-bg-color: rgba(var(--bg-card-rgb), 0.78) !important;
  --el-button-border-color: rgba(var(--border-light-rgb), 0.95) !important;
  --el-button-text-color: var(--text-secondary) !important;
  --el-button-hover-bg-color: rgba(34, 211, 238, 0.12) !important;
  --el-button-hover-border-color: rgba(34, 211, 238, 0.45) !important;
  --el-button-hover-text-color: var(--accent-light) !important;
  --el-button-active-bg-color: rgba(34, 211, 238, 0.18) !important;
  --el-button-active-border-color: rgba(34, 211, 238, 0.6) !important;
  --el-button-active-text-color: var(--accent-light) !important;
  min-height: 34px !important;
  padding: 7px 14px !important;
  border: 1px solid rgba(var(--border-light-rgb), 0.95) !important;
  border-radius: var(--radius-md) !important;
  background: linear-gradient(180deg, rgba(27, 30, 37, 0.72), rgba(14, 16, 19, 0.88)) !important;
  color: var(--text-secondary) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.3px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04) !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}

.share-trigger:hover,
.share-trigger:focus {
  border-color: rgba(34, 211, 238, 0.45) !important;
  background: linear-gradient(180deg, rgba(34, 211, 238, 0.16), rgba(34, 211, 238, 0.08)) !important;
  color: var(--accent-light) !important;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.06) !important;
}

[data-theme="light"] .share-trigger {
  --el-button-bg-color: #fff !important;
  --el-button-border-color: var(--border-medium) !important;
  --el-button-text-color: var(--text-secondary) !important;
  background: #fff !important;
  border-color: var(--border-medium) !important;
  color: var(--text-secondary) !important;
  box-shadow: var(--shadow-xs) !important;
}

.share-panel {
  padding: 4px;
}

.share-panel-title {
  margin-bottom: 10px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.share-preview {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: rgba(var(--bg-card-rgb), 0.5);
}

.share-preview-title {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
  text-wrap: pretty;
}

.share-preview-desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.share-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.share-option {
  display: inline-flex;
  min-width: 0;
  min-height: 72px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.share-option:hover,
.share-option:focus-visible {
  border-color: var(--accent);
  background: var(--bg-hover);
  color: var(--accent);
  box-shadow: 0 4px 12px var(--accent-glow);
  transform: translateY(-2px);
  outline: none;
}

.share-option-icon {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  transition: all var(--duration-fast) var(--ease-smooth);
}

.share-option-icon--link {
  background: var(--purple-dim);
  color: var(--purple);
}

.share-option-icon--system {
  background: rgba(34, 211, 238, 0.12);
  color: var(--accent);
}

.share-option-icon--wechat {
  background: rgba(7, 193, 96, 0.12);
  color: #07c160;
}

.share-option-icon--weibo {
  background: rgba(230, 22, 45, 0.12);
  color: #e6162d;
}

.share-option:hover .share-option-icon,
.share-option:focus-visible .share-option-icon {
  color: var(--accent-contrast, #04202A);
  background: var(--accent);
}

@media (max-width: 420px) {
  .share-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
