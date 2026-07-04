export interface SharePayload {
  title: string
  description?: string
  url: string
}

export function normalizeShareUrl(url: string, baseUrl = window.location.href): string {
  return new URL(url, baseUrl).href
}

export function buildShareText(payload: SharePayload): string {
  return Array.from(new Set([payload.title, payload.description, payload.url]
    .map((item) => item?.trim())
    .filter(Boolean)))
    .join('\n')
}

export function getSharePreviewDescription(title: string, description?: string): string {
  const normalizedTitle = title.trim()
  const normalizedDescription = description?.trim() || ''

  if (!normalizedDescription || normalizedDescription === normalizedTitle) {
    return ''
  }

  return normalizedDescription
}

export function buildWeiboShareUrl(payload: SharePayload): string {
  const shareUrl = new URL('https://service.weibo.com/share/share.php')
  const title = Array.from(new Set([payload.title, payload.description].map((item) => item?.trim()).filter(Boolean))).join(' - ')

  shareUrl.searchParams.set('url', payload.url)
  shareUrl.searchParams.set('title', title)

  return shareUrl.href
}
