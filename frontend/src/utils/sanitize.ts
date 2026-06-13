/**
 * 简易 HTML 消毒器 — 过滤危险标签和属性，保留安全的富文本标签
 */
export function sanitizeHtml(html: string): string {
  if (!html) return ''

  let clean = html
      // 移除危险标签及其内容
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
      .replace(/<object[\s\S]*?<\/object>/gi, '')
      .replace(/<embed[\s\S]*?\/?>/gi, '')
      // 移除事件处理器
      .replace(/\s+on\w+\s*=\s*["'][^"']*["']/gi, '')
      .replace(/\s+on\w+\s*=\s*\S+/gi, '')
      // 移除 javascript: 协议
      .replace(/href\s*=\s*["']javascript:[^"']*["']/gi, 'href="#"')
      .replace(/src\s*=\s*["']javascript:[^"']*["']/gi, '')

  return clean
}
