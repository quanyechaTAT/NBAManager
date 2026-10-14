<template>
  <section class="preferences" aria-label="推荐偏好">
    <div><h3>推荐偏好</h3><p>开启后，根据你的阅读、关注与收藏推荐内容。</p></div>
    <el-switch :model-value="personalized" :disabled="busy || !loaded" aria-label="个性化推荐" @change="toggle" />
    <div class="clear"><p>清除已学习的偏好和“不感兴趣”记录，重新开始发现内容。</p><button :disabled="busy || !loaded" @click="clear">清除推荐偏好</button></div>
    <p class="message" role="status">{{ message }}</p>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { discoveryPreferences, updateDiscoveryPreferences, clearDiscoveryPreferences } from '@/api/discovery'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const personalized = ref(true), loaded = ref(false), busy = ref(false), message = ref('')
const changed = () => window.dispatchEvent(new Event('discovery-preferences-changed'))
watch(() => auth.token, async token => {
  loaded.value = false; busy.value = false; message.value = ''
  if (!token) return
  try { const { data } = await discoveryPreferences(); if (token === auth.token) { personalized.value = data.personalized; loaded.value = true } }
  catch { if (token === auth.token) message.value = '偏好暂时无法加载，请稍后重试。' }
}, { immediate: true })
async function toggle(value: boolean | string | number) {
  const token = auth.token
  busy.value = true
  try { const { data } = await updateDiscoveryPreferences(Boolean(value)); if (token !== auth.token) return; personalized.value = data.personalized; message.value = data.personalized ? '已开启个性化推荐' : '已关闭个性化推荐，将展示热门与新内容'; changed() }
  catch { if (token === auth.token) message.value = '设置保存失败，请重试。' }
  finally { if (token === auth.token) busy.value = false }
}
async function clear() {
  const token = auth.token
  busy.value = true
  try { await clearDiscoveryPreferences(); if (token !== auth.token) return; message.value = '推荐偏好已清除，你的关注与收藏仍然保留。'; changed() }
  catch { if (token === auth.token) message.value = '清除失败，请重试。' }
  finally { if (token === auth.token) busy.value = false }
}
</script>
<style scoped>
.preferences{display:grid;grid-template-columns:1fr auto;gap:12px 20px;padding:24px;border:1px solid var(--border-light);border-radius:14px;background:var(--bg-card);margin:20px 0}.preferences h3{font-size:17px;margin:0 0 8px}.preferences p{color:var(--text-muted);font-size:13px;line-height:1.7;margin:0}.clear{grid-column:1/-1;display:flex;align-items:center;gap:20px;justify-content:space-between;border-top:1px solid var(--border-light);padding-top:16px}.clear button{border:1px solid var(--border-light);background:var(--bg-input);color:var(--text-primary);padding:9px 12px;border-radius:8px;white-space:nowrap;cursor:pointer}.message{grid-column:1/-1}@media(max-width:600px){.clear{align-items:flex-start;flex-direction:column}}
</style>
