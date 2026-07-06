import type { TimelineEvent } from '@/api/types'

/**
 * Get the best available description for a timeline event.
 * Priority: descriptionCn > description > descriptionEn
 */
export function getTimelineDescription(event: TimelineEvent): string {
  return event.descriptionCn || event.description || event.descriptionEn || ''
}

/**
 * Clean Markdown noise from AI-generated text for display.
 * Removes **, *, ###, bullet markers at line start, code fences.
 * Preserves facts: scores, times, player names, team names.
 */
export function displayAiText(text?: string | null): string {
  if (!text) return ''
  let s = text
  // Remove code fences
  s = s.replace(/```[\s\S]*?```/g, '')
  // Remove bold/italic markers
  s = s.replace(/\*{1,3}([^*]+)\*{1,3}/g, '$1')
  // Remove heading markers at line start
  s = s.replace(/^#{1,6}\s+/gm, '')
  // Remove bullet markers at line start (*, -, •)
  s = s.replace(/^[\s]*[*\-•]\s+/gm, '')
  // Collapse multiple blank lines
  s = s.replace(/\n{3,}/g, '\n\n')
  return s.trim()
}
