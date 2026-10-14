<template>
  <article ref="element" class="discovery-card">
    <a :href="router.resolve(item.target).href" class="discovery-link" @click.prevent="open">
      <img v-if="item.imageUrl && !imageFailed" :src="item.imageUrl" alt="" loading="lazy" @error="imageFailed = true" />
      <div class="discovery-copy">
        <div class="discovery-meta"><span>{{ discoveryLabels[item.type] }}</span><time v-if="item.publishedAt">{{ formatDate(item.publishedAt) }}</time></div>
        <h3><template v-for="(part, index) in highlight(item.title)" :key="index"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></h3>
        <p>{{ item.summary }}</p>
        <small v-if="item.reason">{{ item.reason }}</small>
      </div>
    </a>
    <button v-if="dismissible" class="discovery-dismiss" type="button" :aria-label="`不感兴趣：${item.title}`" title="不感兴趣" @click="$emit('dismiss', item)">×</button>
  </article>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { discoveryLabels, type DiscoveryItem } from '@/api/discovery'
import { attribution, observeDiscoveryExposure, sendDiscoveryEvent } from '@/utils/discoveryTelemetry'
const props = defineProps<{ item: DiscoveryItem; requestId: string; position: number; query?: string; dismissible?: boolean }>()
defineEmits<{ dismiss: [DiscoveryItem] }>()
const router = useRouter()
const element = ref<HTMLElement>()
const imageFailed = ref(false)
let dispose = () => {}
const context = () => attribution(props.requestId, props.item.itemKey, props.position)
function observe() { dispose(); if (element.value) dispose = observeDiscoveryExposure(element.value, context()) }
onMounted(observe)
watch(() => [props.requestId, props.item.itemKey], observe)
onBeforeUnmount(() => dispose())
function open() { void sendDiscoveryEvent(context(), 'CLICK'); void router.push(props.item.target) }
function formatDate(value: string) { return new Date(value).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) }
function highlight(value: string) {
  const query = props.query?.trim() || ''
  const index = query ? value.toLocaleLowerCase().indexOf(query.toLocaleLowerCase()) : -1
  return index < 0 ? [{ text: value, hit: false }] : [
    { text: value.slice(0, index), hit: false }, { text: value.slice(index, index + query.length), hit: true },
    { text: value.slice(index + query.length), hit: false },
  ]
}
</script>
<style scoped>
.discovery-card{position:relative;min-width:0;border:1px solid var(--border-light);border-radius:14px;background:var(--bg-card);transition:border-color .2s,transform .2s}
.discovery-card:hover{border-color:var(--accent);transform:translateY(-2px)}.discovery-link{display:flex;gap:16px;padding:20px 38px 20px 20px;color:inherit;text-decoration:none;height:100%;box-sizing:border-box}.discovery-link>img{width:88px;height:88px;object-fit:cover;border-radius:10px;flex-shrink:0}.discovery-copy{min-width:0;flex:1}.discovery-meta{display:flex;align-items:center;gap:10px;font-size:11px;color:var(--text-muted)}.discovery-meta>span{color:var(--accent);font-weight:600}.discovery-copy h3{font-size:16px;line-height:1.55;margin:8px 0;font-weight:650;overflow-wrap:anywhere}.discovery-copy p{font-size:13px;line-height:1.7;color:var(--text-muted);margin:0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.discovery-copy small{display:block;margin-top:12px;font-size:11px;color:var(--text-muted)}.discovery-dismiss{position:absolute;right:9px;top:9px;width:26px;height:26px;border:0;background:transparent;color:var(--text-muted);font-size:20px;cursor:pointer;border-radius:50%}.discovery-dismiss:hover{background:var(--bg-input);color:var(--text-primary)}mark{background:var(--accent-lighter);color:var(--accent);border-radius:3px}@media(max-width:600px){.discovery-link{padding:16px 32px 16px 16px;gap:12px}.discovery-link>img{width:56px;height:56px}.discovery-copy h3{font-size:15px}}
</style>
