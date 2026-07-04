import assert from 'node:assert/strict'
import { formatGameScoreValue } from './gameScore'

assert.equal(formatGameScoreValue(0), '0')
assert.equal(formatGameScoreValue(123), '123')
assert.equal(formatGameScoreValue(null), '-')
assert.equal(formatGameScoreValue(undefined), '-')
