import { describe, expect, it } from 'vitest'
import {
  renderMarkdown,
  generateFollowUps,
  groupConversations,
  filterGroupedConversations,
  prepareRetryTurn,
} from './smartSearchHelpers'

describe('smartSearchHelpers', () => {
  describe('renderMarkdown', () => {
    it('handles empty content and streaming cursor', () => {
      expect(renderMarkdown('', false)).toBe('')
      expect(renderMarkdown('', true)).toBe('<span class="typing-cursor"></span>')
    })

    it('renders bold, inline code, headings, lists and blockquotes correctly', () => {
      const md = '### 战术核心\n**波士顿凯尔特人** 本赛季进攻效率达 `122.4`。\n- 进攻净值领跑全联盟\n- 限制对手三分命中率 Top 3\n> 数据来源：NBA Official Stats'
      const html = renderMarkdown(md)
      expect(html).toContain('<h4 class="ai-heading-3">战术核心</h4>')
      expect(html).toContain('<b>波士顿凯尔特人</b>')
      expect(html).toContain('<code class="ai-inline-code">122.4</code>')
      expect(html).toContain('<ul class="ai-list">')
      expect(html).toContain('<li class="ai-list-item">进攻净值领跑全联盟</li>')
      expect(html).toContain('<blockquote class="ai-blockquote">数据来源：NBA Official Stats</blockquote>')
    })

    it('renders fenced code blocks', () => {
      const md = '代码示例：\n```python\nimport pandas as pd\ndf = pd.DataFrame()\n```'
      const html = renderMarkdown(md)
      expect(html).toContain('<pre class="ai-code-block"><code>import pandas as pd\ndf = pd.DataFrame()</code></pre>')
    })

    it('sanitizes script tags from markdown content', () => {
      const malicious = '前言<script>alert("xss")</script>**正常内容**'
      const html = renderMarkdown(malicious)
      expect(html).not.toContain('<script>')
      expect(html).not.toContain('alert')
      expect(html).toContain('<b>正常内容</b>')
    })

    it('appends typing cursor during streaming', () => {
      const html = renderMarkdown('正在分析比赛数据...', true)
      expect(html).toContain('正在分析比赛数据...')
      expect(html).toContain('<span class="typing-cursor"></span>')
    })
  })

  describe('generateFollowUps', () => {
    it('generates player-specific follow-ups when player is mentioned', () => {
      const followUps = generateFollowUps('勒布朗詹姆斯本场比赛得到 30 分 10 篮板 8 助攻，投篮命中率 55%')
      expect(followUps.length).toBeGreaterThan(0)
      expect(followUps.some(f => f.includes('球员') || f.includes('效率'))).toBe(true)
    })

    it('generates team/tactics follow-ups when team is mentioned', () => {
      const followUps = generateFollowUps('凯尔特人全队战术防守效率优异，战绩高居东部榜首')
      expect(followUps.length).toBeGreaterThan(0)
      expect(followUps.some(f => f.includes('球队') || f.includes('对位') || f.includes('净值'))).toBe(true)
    })

    it('generates game turning-point follow-ups when match is mentioned', () => {
      const followUps = generateFollowUps('第四节关键时刻雷霆打出 12-2 攻击波逆转比赛')
      expect(followUps.length).toBeGreaterThan(0)
      expect(followUps.some(f => f.includes('转折') || f.includes('阵容'))).toBe(true)
    })

    it('provides sensible fallback suggestions when no keywords matched', () => {
      const followUps = generateFollowUps('你好，请介绍一下规则')
      expect(followUps).toHaveLength(3)
      expect(followUps[0]).toContain('战术体系')
    })
  })

  describe('groupConversations', () => {
    it('groups conversations into 今天, 昨天, 近 7 天, 更早 correctly', () => {
      const now = Date.now()
      const convs = [
        { id: '1', title: '今天对话', time: now - 1000 },
        { id: '2', title: '昨天对话', time: now - 86400000 * 1.2 },
        { id: '3', title: '5天前对话', time: now - 86400000 * 5 },
        { id: '4', title: '2周前对话', time: now - 86400000 * 14 },
      ]
      const grouped = groupConversations(convs)
      expect(grouped.length).toBeGreaterThanOrEqual(2)
      const labels = grouped.map(g => g.label)
      expect(labels).toContain('今天')
      expect(labels).toContain('更早')
    })
  })

  describe('filterGroupedConversations', () => {
    it('filters conversations matching query case-insensitively', () => {
      const grouped = [
        {
          label: '今天',
          items: [
            { id: '1', title: '湖人 vs 勇士 战术复盘' },
            { id: '2', title: '掘金约基奇高阶数据' },
          ],
        },
        {
          label: '昨天',
          items: [
            { id: '3', title: '雷霆防守体系分析' },
          ],
        },
      ]

      const filtered = filterGroupedConversations(grouped, '湖人')
      expect(filtered).toHaveLength(1)
      expect(filtered[0].label).toBe('今天')
      expect(filtered[0].items).toHaveLength(1)
      expect(filtered[0].items[0].title).toContain('湖人')

      const emptyFiltered = filterGroupedConversations(grouped, '不存在的关键词')
      expect(emptyFiltered).toHaveLength(0)

      const blankFiltered = filterGroupedConversations(grouped, '   ')
      expect(blankFiltered).toEqual(grouped)
    })
  })

  describe('prepareRetryTurn', () => {
    it('removes failed assistant message and last user message to prevent double user bubbles', () => {
      const messages: { role: 'user' | 'assistant'; content: string; error?: string }[] = [
        { role: 'user', content: '之前的问题' },
        { role: 'assistant', content: '之前的回答' },
        { role: 'user', content: '失败的问题' },
        { role: 'assistant', content: '', error: '网络连接失败' },
      ]

      const { remainingMessages, textToRetry } = prepareRetryTurn(messages)
      expect(textToRetry).toBe('失败的问题')
      expect(remainingMessages).toHaveLength(2)
      expect(remainingMessages[0].content).toBe('之前的问题')
      expect(remainingMessages[1].content).toBe('之前的回答')
    })

    it('handles case with no user message gracefully', () => {
      const messages: { role: 'user' | 'assistant'; content: string; error?: string }[] = []
      const { remainingMessages, textToRetry } = prepareRetryTurn(messages)
      expect(textToRetry).toBeNull()
      expect(remainingMessages).toHaveLength(0)
    })
  })
})
