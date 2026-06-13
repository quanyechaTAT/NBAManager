<template>
  <el-popover placement="top" :width="280" trigger="click" :show-arrow="false" popper-class="share-popover">
    <template #reference>
      <el-button class="share-trigger" plain size="small" @click="handleShare">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
          <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
        </svg>
        分享
      </el-button>
    </template>

    <div class="share-panel">
      <div class="share-panel-title">分享到</div>
      <div class="share-options">
        <button class="share-option" @click="copyLink">
          <div class="share-option-icon share-option-icon--link">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
            </svg>
          </div>
          <span>{{ copied ? '已复制!' : '复制链接' }}</span>
        </button>
        <button class="share-option share-option--disabled" disabled>
          <div class="share-option-icon share-option-icon--wechat">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.582.582 0 0 1-.023-.156.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-7.062-6.122zm-2.735 2.87c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982z"/>
            </svg>
          </div>
          <span>微信</span>
        </button>
        <button class="share-option share-option--disabled" disabled>
          <div class="share-option-icon share-option-icon--weibo">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M10.098 20.323c-3.977.391-7.414-1.406-7.672-4.02-.259-2.609 2.759-5.047 6.74-5.441 3.979-.394 7.413 1.404 7.671 4.018.259 2.6-2.759 5.049-6.739 5.443zm-2.646-7.655c-3.054.51-2.863 4.291-.024 5.451 2.987 1.212 6.289-.184 6.419-3.258.123-2.881-2.957-3.796-6.395-2.193zM20.695 7.679c-.375-1.137-1.574-1.681-2.704-1.441-1.144.243-1.91 1.36-1.676 2.503.236 1.133 1.442 1.842 2.599 1.551 1.149-.291 1.872-1.42 1.781-2.613z"/>
              <path d="M17.636 8.183c-.141-.433-.593-.658-1.023-.554-.43.105-.698.548-.562.977.048.135.067.276.053.416-.141.945-.959 1.687-2.13 1.695-1.179.008-2.143-.777-2.15-1.755-.006-.83.664-1.522 1.583-1.601.316-.027.637.018.917.131-.194-.586-.354-1.19-.343-1.511.038-1.072.866-1.924 1.95-1.938 1.084-.015 1.961.823 1.981 1.896.01.548-.2 1.145-.413 1.69l.105.174c.473.725.619 1.608.448 2.468l-.026.002z"/>
            </svg>
          </div>
          <span>微博</span>
        </button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  url: string
  title: string
  description?: string
}>()

const copied = ref(false)

async function handleShare() {
  if (navigator.share) {
    try {
      await navigator.share({
        title: props.title,
        text: props.description || props.title,
        url: props.url,
      })
    } catch {
      // User cancelled or error - open popover instead
    }
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(props.url)
    copied.value = true
    ElMessage.success('链接已复制到剪贴板')
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    // Fallback for older browsers
    const textarea = document.createElement('textarea')
    textarea.value = props.url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copied.value = true
    ElMessage.success('链接已复制到剪贴板')
    setTimeout(() => { copied.value = false }, 2000)
  }
}
</script>

<style scoped>
.share-trigger {
  border-radius: var(--radius-md) !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.3px;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}

.share-panel {
  padding: 4px;
}

.share-panel-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  margin-bottom: 12px;
  letter-spacing: 0.3px;
}

.share-options {
  display: flex;
  gap: 8px;
}

.share-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  flex: 1;
  font-size: 11px;
  font-family: var(--font-body);
  font-weight: 600;
}

.share-option:hover:not(.share-option--disabled) {
  background: var(--bg-hover);
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--accent-glow);
}

.share-option--disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.share-option-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-smooth);
}

.share-option-icon--link {
  background: var(--purple-dim);
  color: var(--purple);
}

.share-option:hover:not(.share-option--disabled) .share-option-icon--link {
  background: var(--purple);
  color: #fff;
  box-shadow: 0 4px 12px var(--purple-glow);
}

.share-option-icon--wechat {
  background: rgba(7, 193, 96, 0.1);
  color: #07C160;
}

.share-option-icon--weibo {
  background: rgba(230, 22, 45, 0.1);
  color: #E6162D;
}
</style>
