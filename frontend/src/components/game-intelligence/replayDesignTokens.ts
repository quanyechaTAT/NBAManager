/** Game Intelligence Center design tokens */
export const gi = {
  dark: {
    bg: '#0B1120',
    surface: 'rgba(15, 23, 42, 0.86)',
    surfaceStrong: '#111827',
    border: 'rgba(255, 255, 255, 0.08)',
    text: '#F8FAFC',
    muted: '#94A3B8',
    home: '#3B82F6',
    away: '#F97316',
    success: '#10B981',
    danger: '#EF4444',
    warning: '#F59E0B',
    purple: '#8B5CF6',
    cyan: '#06B6D4',
    grid: 'rgba(255,255,255,0.04)',
    glow: 'rgba(59,130,246,0.15)',
  },
  light: {
    bg: '#F8FAFC',
    surface: '#FFFFFF',
    surfaceStrong: '#FFFFFF',
    border: '#E5E7EB',
    text: '#0F172A',
    muted: '#64748B',
    home: '#2563EB',
    away: '#EA580C',
    success: '#059669',
    danger: '#DC2626',
    warning: '#D97706',
    purple: '#7C3AED',
    cyan: '#0891B2',
    grid: 'rgba(0,0,0,0.06)',
    glow: 'rgba(37,99,235,0.1)',
  },
} as const

export type GiTheme = typeof gi.dark

export function useGiTheme(isDark: boolean): GiTheme {
  return isDark ? gi.dark : gi.light
}
