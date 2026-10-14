<template>
  <section class="discovery-admin">
    <header><div><span>DISCOVERY</span><h3>搜索与推荐</h3></div><button :disabled="loading" @click="refresh">刷新状态</button></header>
    <p v-if="error" class="error" role="status">{{ error }}</p>
    <template v-if="status">
      <div class="metrics"><div><strong>{{ status.indexedItems.toLocaleString() }}</strong><span>已索引内容</span></div><div><strong>{{ status.pendingEmbeddings.toLocaleString() }}</strong><span>待生成向量</span></div><div><strong>{{ modelLabel }}</strong><span>当前推荐策略</span></div></div>
      <p class="hint">数据不足时使用规则基线。模型通过离线评估后，先影子运行，再逐步开启 5%、20%、50% 流量。</p>
      <div class="actions"><button v-for="action in actions" :key="action.kind" :disabled="busy" @click="enqueue(action.kind)">{{ action.label }}</button></div>
      <div v-for="model in status.models" :key="model.version" class="model"><div><strong>{{ model.version }}</strong><small>{{ model.status === 'CANDIDATE' ? '离线评估通过' : '评估未通过' }} · {{ metrics(model.metrics_json) }}</small></div><template v-if="model.status === 'CANDIDATE'"><button v-for="traffic in [0, 5, 20, 50]" :key="traffic" :disabled="busy" @click="enqueue('PUBLISH', model.version, traffic)">{{ traffic ? `${traffic}%` : '影子运行' }}</button></template></div>
      <details :open="jobs.some(job => ['QUEUED','RUNNING'].includes(job.status))"><summary>最近任务（{{ jobs.length }}）</summary><div v-for="job in jobs" :key="job.id" class="job"><div><strong>{{ jobLabel(job.kind) }}</strong><span>{{ job.status }} · {{ job.progress }}%</span><small>{{ detail(job.detail_json) }}</small></div><button v-if="['QUEUED','RUNNING'].includes(job.status)" :disabled="job.cancel_requested" @click="cancel(job.id)">{{ job.cancel_requested ? '取消中' : '取消' }}</button></div></details>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { discoveryStatus, discoveryJobs, createDiscoveryJob, cancelDiscoveryJob, type DiscoveryStatus, type DiscoveryJob } from '@/api/discovery'
const status = ref<DiscoveryStatus | null>(null), jobs = ref<DiscoveryJob[]>([]), loading = ref(false), busy = ref(false), error = ref('')
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false
const actions = [{ kind: 'SNAPSHOT', label: '补齐全文索引' }, { kind: 'REINDEX', label: '重建并切换索引' }, { kind: 'ITEMCF', label: '更新协同推荐' }, { kind: 'TRAIN', label: '训练与评估' }, { kind: 'ROLLBACK_MODEL', label: '回滚推荐模型' }, { kind: 'ROLLBACK_INDEX', label: '回滚索引' }]
function parse(value: string): Record<string, unknown> { try { return JSON.parse(value) } catch { return {} } }
const modelLabel = computed(() => { const value = status.value?.controls.find(row => row.control_key === 'model'); const model = parse(value?.value_json || '{}'); return model.version ? `${model.traffic || 0}% 模型流量` : '规则基线' })
function jobLabel(kind: string) { return actions.find(action => action.kind === kind)?.label || (kind === 'PUBLISH' ? '发布模型' : kind) }
function metrics(value: string) { const data = parse(value); return `NDCG@10 ${Number(data.testNdcg10 || 0).toFixed(3)} / 基线 ${Number(data.testBaselineNdcg10 || 0).toFixed(3)}` }
function detail(value: string) { const data = parse(value); return data.error ? String(data.error) : data.status === 'INSUFFICIENT_DATA' ? '真实曝光数据尚未达到训练门槛，继续使用基线。' : data.version ? String(data.version) : '' }
async function refresh() {
  clearTimeout(timer); loading.value = true
  try { const [state, tasks] = await Promise.all([discoveryStatus(), discoveryJobs()]); if (disposed) return; status.value = state.data; jobs.value = tasks.data; error.value = '' }
  catch { if (!disposed) error.value = '搜推状态暂时无法加载，请检查服务后重试。' }
  finally { loading.value = false; if (!disposed && jobs.value.some(job => ['QUEUED','RUNNING'].includes(job.status))) timer = setTimeout(refresh, 5000) }
}
async function enqueue(kind: string, version?: string, traffic?: number) { busy.value = true; try { await createDiscoveryJob(kind, version, traffic); await refresh() } catch { error.value = '任务提交失败，请重试。' } finally { busy.value = false } }
async function cancel(id: string) { try { await cancelDiscoveryJob(id); await refresh() } catch { error.value = '取消请求失败，请重试。' } }
onMounted(refresh)
onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
</script>
<style scoped>
.discovery-admin{position:relative;margin:24px 0;padding:24px;background:var(--bg-card);border:1px solid var(--border-light);border-radius:16px}.discovery-admin header{display:flex;justify-content:space-between;align-items:center}.discovery-admin header span{font-size:10px;letter-spacing:1.6px;color:var(--accent)}h3{margin:6px 0 18px;font-size:21px}.metrics{display:flex;gap:36px;flex-wrap:wrap}.metrics>div{display:grid;gap:6px}.metrics strong{font-size:23px}.metrics span,.hint,.model small,.job small{font-size:12px;color:var(--text-muted);line-height:1.7}.hint{margin:18px 0}.actions{display:flex;gap:8px;flex-wrap:wrap}button{border:1px solid var(--border-light);background:var(--bg-input);color:var(--text-primary);border-radius:7px;padding:8px 12px;cursor:pointer;font-size:12px}button:disabled{opacity:.5;cursor:wait}.model,.job{display:flex;gap:10px;align-items:center;flex-wrap:wrap;padding:14px 0;border-top:1px solid var(--border-light);font-size:12px}.model>div,.job>div{flex:1;min-width:180px;display:grid;gap:6px}.model{margin-top:18px}.job span{color:var(--accent)}details{margin-top:18px}summary{cursor:pointer;font-size:13px;color:var(--text-muted);margin-bottom:12px}.error{color:var(--text-muted);font-size:13px}
</style>
