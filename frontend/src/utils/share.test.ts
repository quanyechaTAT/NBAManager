import { describe, expect, it } from 'vitest'
import { buildShareText, buildWeiboShareUrl, getSharePreviewDescription, normalizeShareUrl } from './share'

describe('share utilities', () => {
  it('normalizes relative and absolute URLs', () => {
    expect(normalizeShareUrl('/news?id=12', 'https://app.example.com/base/path'))
      .toBe('https://app.example.com/news?id=12')
    expect(normalizeShareUrl('https://nba.example.com/news/12', 'https://app.example.com'))
      .toBe('https://nba.example.com/news/12')
  })

  it('builds concise share text without duplicating the title', () => {
    expect(buildShareText({
      title: 'Cavs vs TBD',
      description: 'A short preview',
      url: 'https://app.example.com/news?id=12',
    })).toBe('Cavs vs TBD\nA short preview\nhttps://app.example.com/news?id=12')
    expect(buildShareText({
      title: 'Same title',
      description: 'Same title',
      url: 'https://app.example.com/news?id=13',
    })).toBe('Same title\nhttps://app.example.com/news?id=13')
  })

  it('omits duplicate descriptions and builds the expected Weibo URL', () => {
    expect(getSharePreviewDescription('Same title', 'Same title')).toBe('')
    expect(getSharePreviewDescription('Same title', '  Same title  ')).toBe('')
    expect(getSharePreviewDescription('Same title', 'A useful summary')).toBe('A useful summary')

    const weiboUrl = new URL(buildWeiboShareUrl({
      title: 'Cavs vs TBD',
      description: 'A short preview',
      url: 'https://app.example.com/news?id=12',
    }))
    expect(weiboUrl.origin + weiboUrl.pathname).toBe('https://service.weibo.com/share/share.php')
    expect(weiboUrl.searchParams.get('url')).toBe('https://app.example.com/news?id=12')
    expect(weiboUrl.searchParams.get('title')).toBe('Cavs vs TBD - A short preview')
  })
})
