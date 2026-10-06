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

  it('handles empty and single-point series gracefully', () => {
    expect(smoothPath([])).toBe('')
    expect(smoothPath([{ x: 50, y: 80 }])).toBe('M 50 80')
  })

  it('prevents wild control point overshoot on steep momentum swings', () => {
    const swingPoints = [
      { x: 0, y: 100 },
      { x: 50, y: 100 },
      { x: 100, y: 300 }, // sudden 200px drop (opponent big run)
      { x: 150, y: 300 },
    ]
    const path = smoothPath(swingPoints)
    expect(path).toContain(' C ')
    // Ensure all control point y values stay bounded
    const matches = path.matchAll(/C ([\d.-]+) ([\d.-]+), ([\d.-]+) ([\d.-]+), ([\d.-]+) ([\d.-]+)/g)
    for (const match of matches) {
      const cp1y = Number(match[2])
      const cp2y = Number(match[4])
      expect(cp1y).toBeGreaterThanOrEqual(80)
      expect(cp1y).toBeLessThanOrEqual(320)
      expect(cp2y).toBeGreaterThanOrEqual(80)
      expect(cp2y).toBeLessThanOrEqual(320)
    }
  })
})
