import { findNearestHoverPoint, smoothPath } from './trendGeometry.js'

type Point = { x: number; y: number; eventOrder: number }

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message)
}

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`)
  }
}

const points: Point[] = [
  { x: 100, y: 120, eventOrder: 1 },
  { x: 180, y: 180, eventOrder: 2 },
  { x: 260, y: 110, eventOrder: 3 },
]

const nearPoint = findNearestHoverPoint(points, 104, 123, 30)
assert(nearPoint !== null, 'returns a point when pointer is near a data point')
assertEqual(nearPoint.eventOrder, 1, 'returns the nearest event point')

const nearLine = findNearestHoverPoint(points, 142, 150, 30)
assert(nearLine !== null, 'returns a point when pointer is near the line segment between points')
assertEqual(nearLine.eventOrder, 1, 'uses the nearest endpoint for segment hover preview')

const blankArea = findNearestHoverPoint(points, 104, 250, 30)
assertEqual(blankArea, null, 'does not show preview when x is close but y is far from the line')

const farPoint = findNearestHoverPoint(points, 420, 250, 30)
assertEqual(farPoint, null, 'does not show preview in blank chart area')

const path = smoothPath(points)
assert(path.startsWith('M 100 120'), 'smooth path starts at first point')
assert(path.includes(' C '), 'smooth path uses cubic curves for premium line quality')
assert(path.endsWith('260 110'), 'smooth path ends at last point')
