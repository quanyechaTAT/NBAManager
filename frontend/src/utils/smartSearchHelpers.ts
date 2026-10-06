import { sanitizeHtml } from '@/utils/sanitize'

export function renderMarkdown(content: string, isStreaming = false): string {
  if (!content) return isStreaming ? '<span class="typing-cursor"></span>' : ''

  // 1. Sanitize HTML first to prevent any XSS attack
  let text = sanitizeHtml(content)

  // 2. Fenced code blocks ```lang\ncode\n``` - store in placeholders to protect raw newlines
  const codeBlocks: string[] = []
  text = text.replace(/```(?:[a-zA-Z0-9_-]+)?\r?\n([\s\S]*?)```/g, (_m, code) => {
    const placeholder = `___AI_CODE_BLOCK_${codeBlocks.length}___`
    codeBlocks.push(`<pre class="ai-code-block"><code>${code.trim()}</code></pre>`)
    return placeholder
  })

  // 3. Inline code `code`
  text = text.replace(/`([^`\r\n]+)`/g, '<code class="ai-inline-code">$1</code>')

  // 4. Headings
  text = text.replace(/^### (.*$)/gim, '<h4 class="ai-heading-3">$1</h4>')
  text = text.replace(/^## (.*$)/gim, '<h3 class="ai-heading-2">$1</h3>')
  text = text.replace(/^# (.*$)/gim, '<h2 class="ai-heading-1">$1</h2>')

  // 5. Blockquotes
  text = text.replace(/^> (.*$)/gim, '<blockquote class="ai-blockquote">$1</blockquote>')

  // 6. Horizontal rule
  text = text.replace(/^---$/gim, '<hr class="ai-divider" />')

  // 7. Bold **text**
  text = text.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>')

  // 8. Bullet list items
  text = text.replace(/^[*-] (.*$)/gim, '<li class="ai-list-item">$1</li>')

  // 9. Numbered list items
  text = text.replace(/^\d+\.\s+(.*$)/gim, '<li class="ai-list-item-num">$1</li>')

  // 10. Wrap consecutive <li> into <ul>
  text = text.replace(/((?:<li class="ai-list-item(?:-num)?">.*?<\/li>\s*)+)/g, '<ul class="ai-list">$1</ul>')

  // 11. Normalize newlines around block tags and convert remaining newlines to <br>
  text = text.replace(/(<\/(?:h2|h3|h4|blockquote|pre|ul|hr)>)\r?\n+/g, '$1')
  text = text.replace(/\r?\n+(<(?:h2|h3|h4|blockquote|pre|ul|hr))/g, '$1')
  text = text.replace(/\r?\n/g, '<br>')

  // 12. Restore protected code blocks
  codeBlocks.forEach((block, idx) => {
    text = text.replace(`___AI_CODE_BLOCK_${idx}___`, block)
  })

  if (isStreaming) {
    text += '<span class="typing-cursor"></span>'
  }

  return text
}

export function generateFollowUps(content: string): string[] {
  const lower = content.toLowerCase()
  const suggestions: string[] = []

  if (lower.includes('球员') || lower.includes('得分') || lower.includes('命中率') || lower.includes('效率') || lower.includes('助攻') || lower.includes('篮板')) {
    suggestions.push('对比这名球员和同位置顶级球星')
    suggestions.push('查看近五个赛季高阶效率趋势')
  }
  if (lower.includes('球队') || lower.includes('战绩') || lower.includes('战术') || lower.includes('防守') || lower.includes('进攻')) {
    suggestions.push('分析该球队季后赛战术对位胜率')
    suggestions.push('查看同分区主要竞争对手攻防净值')
  }
  if (lower.includes('比赛') || lower.includes('总决赛') || lower.includes('转折') || lower.includes('逆转') || lower.includes('关键时刻')) {
    suggestions.push('深度复盘比赛关键时刻战术转折')
    suggestions.push('调取双方首发与替补阵容轮换得分')
  }

  if (!suggestions.length) {
    suggestions.push('结合战术体系详细展开说明')
    suggestions.push('这项表现的关键数据来源与依据是什么？')
    suggestions.push('对比联盟同位置历史平均水平')
  }

  return suggestions.slice(0, 3)
}

export function groupConversations<T extends { time: number }>(convs: T[]): { label: string; items: T[] }[] {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const buckets = new Map<string, T[]>()

  const sorted = [...convs].sort((a, b) => Number(b.time) - Number(a.time))
  for (const conv of sorted) {
    const timestamp = Number(conv.time)
    const label = timestamp >= todayStart
      ? '今天'
      : timestamp >= todayStart - 86400000
        ? '昨天'
        : timestamp >= todayStart - 7 * 86400000
          ? '近 7 天'
          : '更早'
    if (!buckets.has(label)) buckets.set(label, [])
    buckets.get(label)!.push(conv)
  }

  const order = ['今天', '昨天', '近 7 天', '更早']
  const result: { label: string; items: T[] }[] = []
  for (const key of order) {
    if (buckets.has(key)) {
      result.push({ label: key, items: buckets.get(key)! })
    }
  }
  return result
}

export function filterGroupedConversations<T extends { title: string }>(
  grouped: { label: string; items: T[] }[],
  query: string
): { label: string; items: T[] }[] {
  const trimmed = query.trim().toLowerCase()
  if (!trimmed) return grouped
  return grouped
    .map(group => ({
      label: group.label,
      items: group.items.filter(item => item.title.toLowerCase().includes(trimmed))
    }))
    .filter(group => group.items.length > 0)
}

export function prepareRetryTurn<T extends { role: 'user' | 'assistant'; content: string; error?: string }>(
  messages: T[]
): { remainingMessages: T[]; textToRetry: string | null } {
  const copy = [...messages]
  if (copy.length > 0 && copy[copy.length - 1].role === 'assistant' && (copy[copy.length - 1].error || !copy[copy.length - 1].content.trim())) {
    copy.pop()
  }
  const lastUserIdx = copy.map(m => m.role).lastIndexOf('user')
  if (lastUserIdx === -1) {
    return { remainingMessages: copy, textToRetry: null }
  }
  const textToRetry = copy[lastUserIdx].content
  copy.splice(lastUserIdx, 1)
  return { remainingMessages: copy, textToRetry }
}

export async function copyText(text: string): Promise<boolean> {
  if (typeof window === 'undefined') return false
  if (navigator?.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch { /* fallback below */ }
  }
  try {
    const el = document.createElement('textarea')
    el.value = text
    el.style.position = 'fixed'
    el.style.left = '-9999px'
    el.style.top = '0'
    el.setAttribute('readonly', '')
    document.body.appendChild(el)
    el.select()
    const success = document.execCommand('copy')
    document.body.removeChild(el)
    return success
  } catch {
    return false
  }
}
