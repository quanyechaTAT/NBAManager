import { describe, expect, it } from 'vitest'
import { findNearestHoverPoint, smoothPath } from './trendGeometry'

type Point = { x: number; y: number; eventOrder: number }

const points: Point[] = [
  { x: 100, y: 120, eventOrder: 1 },
  { x: 180, y: 180, eventOrder: 2 },
  { x: 260, y: 110, eventOrder: 3 },
]

describe('trendGeometry', () => {
  it('finds the nearest point or line endpoint inside the hit radius', () => {
    expect(findNearestHoverPoint(points, 104, 123, 30)?.eventOrder).toBe(1)
    expect(findNearestHoverPoint(points, 142, 150, 30)?.eventOrder).toBe(1)
  })

  it('does not show a preview in blank chart space', () => {
    expect(findNearestHoverPoint(points, 104, 250, 30)).toBeNull()
    expect(findNearestHoverPoint(points, 420, 250, 30)).toBeNull()
  })

  it('builds a smooth path spanning the first and last points', () => {
    const path = smoothPath(points)
    expect(path).toMatch(/^M 100 120/)
    expect(path).toContain(' C ')
    expect(path).toMatch(/260 110$/)
  })
})
