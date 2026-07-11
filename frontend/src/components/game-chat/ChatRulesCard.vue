<template>
  <div class="rules-card">
    <div class="rules-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
      <span class="rules-title">聊天室规则</span>
    </div>

    <ol class="rules-list">
      <li>请文明发言，尊重他人</li>
      <li>禁止发布广告、色情等违规内容</li>
      <li>禁止恶意刷屏、攻击他人</li>
    </ol>

    <button class="rules-link" @click="showDialog = true">查看详情</button>
  </div>

  <el-dialog
    v-model="showDialog"
    title="聊天室规则"
    width="480px"
    class="rules-dialog"
    :append-to-body="true"
    destroy-on-close
  >
    <div class="dialog-rules-content">
      <p><strong>1.</strong> 请文明发言，尊重他人，不使用侮辱性语言。</p>
      <p><strong>2.</strong> 禁止发布广告、色情、暴力等违规内容。</p>
      <p><strong>3.</strong> 禁止恶意刷屏、重复发送相同内容。</p>
      <p><strong>4.</strong> 禁止人身攻击、地域歧视等行为。</p>
      <p><strong>5.</strong> 请尊重比赛，理性讨论，享受篮球。</p>
      <p><strong>6.</strong> 违反规则者将被禁言处理。</p>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const showDialog = ref(false)
</script>

<style scoped>
.rules-card {
  background: var(--chat-panel, var(--bg-panel));
  border: 1px solid var(--chat-border, var(--border-light));
  border-radius: 16px;
  padding: 16px;
}

.rules-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--chat-accent, var(--accent));
}

.rules-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--chat-text, var(--text-primary));
  font-family: var(--font-heading);
}

.rules-list {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--chat-muted, var(--text-secondary));
}

.rules-list li {
  margin-bottom: 2px;
}

.rules-link {
  display: inline-block;
  border: none;
  background: none;
  color: var(--chat-accent, var(--accent));
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  text-decoration: none;
}

.rules-link:hover {
  text-decoration: underline;
}
</style>

<style>
/* Dialog styles (unscoped for theme compatibility) */
.rules-dialog .el-dialog {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
}

.rules-dialog .el-dialog__header {
  border-bottom: 1px solid var(--border-light) !important;
}

.rules-dialog .el-dialog__title {
  color: var(--text-primary) !important;
  font-family: var(--font-heading) !important;
  font-weight: 700 !important;
}

.rules-dialog .el-dialog__body {
  color: var(--text-secondary) !important;
}

.dialog-rules-content p {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.dialog-rules-content strong {
  color: var(--accent);
}
</style>
