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
  const dxs: number[] = []
  const dys: number[] = []
  const slopes: number[] = []

  for (let i = 0; i < n - 1; i += 1) {
    const dx = deduped[i + 1].x - deduped[i].x
    const dy = deduped[i + 1].y - deduped[i].y
    dxs.push(dx)
    dys.push(dy)
    slopes.push(dx === 0 ? 0 : dy / dx)
  }

  // Fritsch-Carlson monotone tangents
  const tangents: number[] = [slopes[0]]
  for (let i = 1; i < n - 1; i += 1) {
    if (slopes[i - 1] * slopes[i] <= 0) {
      tangents.push(0)
    } else {
      tangents.push((slopes[i - 1] + slopes[i]) / 2)
    }
  }
  tangents.push(slopes[n - 2])

  // Enforce monotonicity constraint (Fritsch-Carlson)
  for (let i = 0; i < n - 1; i += 1) {
    if (Math.abs(slopes[i]) < 1e-10) {
      tangents[i] = 0
      tangents[i + 1] = 0
      continue
    }

    const a = tangents[i] / slopes[i]
    const b = tangents[i + 1] / slopes[i]
    const magnitude = a * a + b * b
    if (magnitude > 9) {
      const tau = 3 / Math.sqrt(magnitude)
      tangents[i] = tau * a * slopes[i]
      tangents[i + 1] = tau * b * slopes[i]
    }
  }

  // Build cubic bezier path
  const commands = [`M ${deduped[0].x} ${deduped[0].y}`]
  for (let i = 0; i < n - 1; i += 1) {
    const dx = dxs[i] / 3
    commands.push(
      `C ${deduped[i].x + dx} ${deduped[i].y + tangents[i] * dx}, ${deduped[i + 1].x - dx} ${deduped[i + 1].y - tangents[i + 1] * dx}, ${deduped[i + 1].x} ${deduped[i + 1].y}`,
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
