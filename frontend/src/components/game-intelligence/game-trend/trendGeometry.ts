export type TrendGeometryPoint = {
  x: number
  y: number
}

export function findNearestHoverPoint<T extends TrendGeometryPoint>(
  points: T[],
  pointerX: number,
  pointerY: number,
  threshold = 30,
): T | null {
  let nearestPoint: T | null = null
  let nearestPointDistance = Number.POSITIVE_INFINITY

  points.forEach((point) => {
    const distance = Math.hypot(point.x - pointerX, point.y - pointerY)
    if (distance < nearestPointDistance) {
      nearestPointDistance = distance
      nearestPoint = point
    }
  })

  if (nearestPoint && nearestPointDistance <= threshold) return nearestPoint

  let nearestSegmentStart: T | null = null
  let nearestSegmentDistance = Number.POSITIVE_INFINITY

  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index]
    const end = points[index + 1]
    const distance = distanceToSegment(pointerX, pointerY, start, end)
    if (distance < nearestSegmentDistance) {
      nearestSegmentDistance = distance
      nearestSegmentStart = start
    }
  }

  return nearestSegmentStart && nearestSegmentDistance <= threshold ? nearestSegmentStart : null
}

function distanceToSegment(
  pointerX: number,
  pointerY: number,
  start: TrendGeometryPoint,
  end: TrendGeometryPoint,
): number {
  const segmentX = end.x - start.x
  const segmentY = end.y - start.y
  const lengthSquared = segmentX * segmentX + segmentY * segmentY

  if (lengthSquared === 0) return Math.hypot(pointerX - start.x, pointerY - start.y)

  const projection = Math.max(
    0,
    Math.min(1, ((pointerX - start.x) * segmentX + (pointerY - start.y) * segmentY) / lengthSquared),
  )
  const projectedX = start.x + projection * segmentX
  const projectedY = start.y + projection * segmentY

  return Math.hypot(pointerX - projectedX, pointerY - projectedY)
}

export function smoothPath(points: TrendGeometryPoint[]): string {
  if (points.length === 0) return ''
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`

  // Deduplicate points with identical x (keep last y value for each x)
  const deduped: TrendGeometryPoint[] = [points[0]]
  for (let i = 1; i < points.length; i += 1) {
    if (points[i].x === deduped[deduped.length - 1].x) {
      deduped[deduped.length - 1] = points[i]
    } else {
      deduped.push(points[i])
    }
  }

  if (deduped.length === 1) return `M ${deduped[0].x} ${deduped[0].y}`
  if (deduped.length === 2) return `M ${deduped[0].x} ${deduped[0].y} L ${deduped[1].x} ${deduped[1].y}`

  const n = deduped.length

  // Gentle moving window smoothing for dense event series (n > 4) to eliminate staircase plateaus
  // while strictly preserving endpoints and actual momentum hills and valleys
  let smoothedY: number[]
  if (n > 4) {
    smoothedY = new Array(n)
    smoothedY[0] = deduped[0].y
    smoothedY[n - 1] = deduped[n - 1].y

    for (let i = 1; i < n - 1; i += 1) {
      const prev2 = i > 1 ? deduped[i - 2].y : deduped[0].y
      const prev1 = deduped[i - 1].y
      const curr = deduped[i].y
      const next1 = deduped[i + 1].y
      const next2 = i < n - 2 ? deduped[i + 2].y : deduped[n - 1].y
      // 5-point Gaussian-style kernel: [0.08, 0.22, 0.40, 0.22, 0.08]
      smoothedY[i] = prev2 * 0.08 + prev1 * 0.22 + curr * 0.4 + next1 * 0.22 + next2 * 0.08
    }
  } else {
    smoothedY = deduped.map((p) => p.y)
  }

  const pts = deduped.map((p, i) => ({ x: p.x, y: smoothedY[i] }))

  // Natural Catmull-Rom / Cardinal spline with controlled tension
  const tension = 0.55 // Soft natural curvature without extreme overshooting
  const commands = [`M ${deduped[0].x} ${deduped[0].y}`]

  for (let i = 0; i < n - 1; i += 1) {
    const p0 = i > 0 ? pts[i - 1] : pts[0]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = i < n - 2 ? pts[i + 2] : pts[n - 1]

    const cp1x = p1.x + ((p2.x - p0.x) * tension) / 6
    const cp1y = p1.y + ((p2.y - p0.y) * tension) / 6
    const cp2x = p2.x - ((p3.x - p1.x) * tension) / 6
    const cp2y = p2.y - ((p3.y - p1.y) * tension) / 6

    const localMinY = Math.min(p0.y, p1.y, p2.y, p3.y) - 12
    const localMaxY = Math.max(p0.y, p1.y, p2.y, p3.y) + 12
    const safeCp1y = Math.max(localMinY, Math.min(localMaxY, cp1y))
    const safeCp2y = Math.max(localMinY, Math.min(localMaxY, cp2y))

    const targetX = i === n - 2 ? deduped[n - 1].x : Number(p2.x.toFixed(2))
    const targetY = i === n - 2 ? deduped[n - 1].y : Number(p2.y.toFixed(2))

    commands.push(
      `C ${Number(cp1x.toFixed(2))} ${Number(safeCp1y.toFixed(2))}, ${Number(cp2x.toFixed(2))} ${Number(safeCp2y.toFixed(2))}, ${targetX} ${targetY}`,
    )
  }

  return commands.join(' ')
}

export function areaPath(points: TrendGeometryPoint[], baseline: number): string {
  if (points.length < 2) return ''
  const line = smoothPath(points)
  const first = points[0]
  const last = points[points.length - 1]
  return `${line} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`
}
