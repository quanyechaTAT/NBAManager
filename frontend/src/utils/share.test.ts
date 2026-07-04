import assert from 'node:assert/strict'
import { buildShareText, buildWeiboShareUrl, getSharePreviewDescription, normalizeShareUrl } from './share'

assert.equal(
  normalizeShareUrl('/news?id=12', 'https://app.example.com/base/path'),
  'https://app.example.com/news?id=12',
)

assert.equal(
  normalizeShareUrl('https://nba.example.com/news/12', 'https://app.example.com'),
  'https://nba.example.com/news/12',
)

assert.equal(
  buildShareText({
    title: 'Cavs vs TBD',
    description: 'A short preview',
    url: 'https://app.example.com/news?id=12',
  }),
  'Cavs vs TBD\nA short preview\nhttps://app.example.com/news?id=12',
)

assert.equal(
  buildShareText({
    title: 'Same title',
    description: 'Same title',
    url: 'https://app.example.com/news?id=13',
  }),
  'Same title\nhttps://app.example.com/news?id=13',
)

assert.equal(getSharePreviewDescription('Same title', 'Same title'), '')
assert.equal(getSharePreviewDescription('Same title', '  Same title  '), '')
assert.equal(getSharePreviewDescription('Same title', 'A useful summary'), 'A useful summary')

const weiboUrl = new URL(buildWeiboShareUrl({
  title: 'Cavs vs TBD',
  description: 'A short preview',
  url: 'https://app.example.com/news?id=12',
}))

assert.equal(weiboUrl.origin + weiboUrl.pathname, 'https://service.weibo.com/share/share.php')
assert.equal(weiboUrl.searchParams.get('url'), 'https://app.example.com/news?id=12')
assert.equal(weiboUrl.searchParams.get('title'), 'Cavs vs TBD - A short preview')
