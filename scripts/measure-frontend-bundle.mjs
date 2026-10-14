import { readFile, writeFile } from 'node:fs/promises'
import { resolve, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

// Counts only assets directly referenced by HTML, not the full route/network graph.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(root, 'frontend', 'dist')
const html = await readFile(join(dist, 'index.html'), 'utf8')
const urls = [...new Set([...html.matchAll(/(?:src|href)="([^"?#]+\.(?:js|css))"/g)].map(match => match[1]))]
const assets = await Promise.all(urls.map(async url => {
  const bytes = await readFile(join(dist, url.replace(/^\//, '')))
  return { url, bytes: bytes.length, gzipBytes: gzipSync(bytes).length }
}))
const sum = (extension, field) => assets.filter(asset => asset.url.endsWith(extension))
  .reduce((total, asset) => total + asset[field], 0)
const result = {
  recordedAt: new Date().toISOString(), assets,
  initialJsBytes: sum('.js', 'bytes'), initialJsGzipBytes: sum('.js', 'gzipBytes'),
  initialCssBytes: sum('.css', 'bytes'), initialCssGzipBytes: sum('.css', 'gzipBytes'),
}
const json = JSON.stringify(result, null, 2) + '\n'
if (process.argv[2]) await writeFile(resolve(process.argv[2]), json)
process.stdout.write(json)
