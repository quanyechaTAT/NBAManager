/**
 * NBA 球队主色映射表
 * 包含 30 支 NBA 球队的主色（primary）、副色（secondary）和可选强调色（accent）
 */

export interface TeamColors {
  /** 主色 */
  primary: string
  /** 副色 */
  secondary: string
  /** 强调色（可选） */
  accent?: string
}

/** 球队颜色映射表 */
const teamColorMap: Record<string, TeamColors> = {
  // 东部联盟
  'Boston Celtics': { primary: '#007A33', secondary: '#BA9653', accent: '#FFFFFF' },
  'Brooklyn Nets': { primary: '#000000', secondary: '#FFFFFF' },
  'New York Knicks': { primary: '#006BB6', secondary: '#F58426', accent: '#BEC0C2' },
  'Philadelphia 76ers': { primary: '#006BB6', secondary: '#ED174C', accent: '#002B5C' },
  'Toronto Raptors': { primary: '#CE1141', secondary: '#000000', accent: '#A1A1A4' },
  'Chicago Bulls': { primary: '#CE1141', secondary: '#000000' },
  'Cleveland Cavaliers': { primary: '#860038', secondary: '#FDBB30', accent: '#041E42' },
  'Detroit Pistons': { primary: '#C8102E', secondary: '#1D42BA', accent: '#BEC0C2' },
  'Indiana Pacers': { primary: '#002D62', secondary: '#FDBB30', accent: '#BEC0C2' },
  'Milwaukee Bucks': { primary: '#00471B', secondary: '#EEE1C6', accent: '#0077C0' },
  'Atlanta Hawks': { primary: '#E03A3E', secondary: '#C1D32F', accent: '#000000' },
  'Charlotte Hornets': { primary: '#1D1160', secondary: '#00788C', accent: '#A1A1A4' },
  'Miami Heat': { primary: '#98002E', secondary: '#F9A01B', accent: '#000000' },
  'Orlando Magic': { primary: '#0077C0', secondary: '#000000', accent: '#C4CED4' },
  'Washington Wizards': { primary: '#002B5C', secondary: '#E31837', accent: '#C4CED4' },

  // 西部联盟
  'Denver Nuggets': { primary: '#0E2240', secondary: '#FEC524', accent: '#8B2131' },
  'Minnesota Timberwolves': { primary: '#0C2340', secondary: '#236192', accent: '#78BE20' },
  'Oklahoma City Thunder': { primary: '#007AC1', secondary: '#EF6100', accent: '#002D62' },
  'Portland Trail Blazers': { primary: '#E03A3E', secondary: '#000000' },
  'Utah Jazz': { primary: '#002B5C', secondary: '#00471B', accent: '#F9A01B' },
  'Golden State Warriors': { primary: '#1D428A', secondary: '#FFC72C', accent: '#006BB6' },
  'Los Angeles Clippers': { primary: '#C8102E', secondary: '#1D428A', accent: '#BEC0C2' },
  'Los Angeles Lakers': { primary: '#552583', secondary: '#FDB927', accent: '#000000' },
  'Phoenix Suns': { primary: '#1D1160', secondary: '#E56020', accent: '#63727A' },
  'Sacramento Kings': { primary: '#5A2D81', secondary: '#63727A', accent: '#000000' },
  'Dallas Mavericks': { primary: '#00538C', secondary: '#002B5E', accent: '#B8C4CA' },
  'Houston Rockets': { primary: '#CE1141', secondary: '#000000', accent: '#C4CED4' },
  'Memphis Grizzlies': { primary: '#5D76A9', secondary: '#12173F', accent: '#FDB927' },
  'New Orleans Pelicans': { primary: '#0C2340', secondary: '#C8102E', accent: '#B4975A' },
  'San Antonio Spurs': { primary: '#C4CED4', secondary: '#000000', accent: '#000000' }
}

/**
 * 获取球队颜色
 * @param teamName 球队全名
 * @returns 球队颜色对象，未找到时返回默认颜色
 */
export function getTeamColor(teamName: string): TeamColors {
  const colors = teamColorMap[teamName]
  if (colors) {
    return colors
  }

  // 尝试模糊匹配（例如 "Celtics" -> "Boston Celtics"）
  const normalizedName = teamName.toLowerCase()
  for (const [key, value] of Object.entries(teamColorMap)) {
    if (key.toLowerCase().includes(normalizedName) || normalizedName.includes(key.toLowerCase().split(' ').pop() || '')) {
      return value
    }
  }

  // 默认颜色
  return {
    primary: '#0891B2',
    secondary: '#8B5CF6'
  }
}

/**
 * 获取球队主色
 * @param teamName 球队全名
 * @returns 主色 hex 值
 */
export function getTeamPrimaryColor(teamName: string): string {
  return getTeamColor(teamName).primary
}

/** 球队代码 / 中文队名 → 颜色（由英文全称映射派生，支持中文队名查询） */
const CODE_COLORS: Record<string, TeamColors> = (() => {
  const NAME_TO_CODE: Record<string, string> = {
    'Boston Celtics': 'bos', 'Brooklyn Nets': 'bkn', 'New York Knicks': 'nyk',
    'Philadelphia 76ers': 'phi', 'Toronto Raptors': 'tor',
    'Chicago Bulls': 'chi', 'Cleveland Cavaliers': 'cle', 'Detroit Pistons': 'det',
    'Indiana Pacers': 'ind', 'Milwaukee Bucks': 'mil',
    'Atlanta Hawks': 'atl', 'Charlotte Hornets': 'cha', 'Miami Heat': 'mia',
    'Orlando Magic': 'orl', 'Washington Wizards': 'was',
    'Denver Nuggets': 'den', 'Minnesota Timberwolves': 'min', 'Oklahoma City Thunder': 'okc',
    'Portland Trail Blazers': 'por', 'Utah Jazz': 'utah',
    'Golden State Warriors': 'gs', 'Los Angeles Clippers': 'lac', 'Los Angeles Lakers': 'lal',
    'Phoenix Suns': 'phx', 'Sacramento Kings': 'sac',
    'Dallas Mavericks': 'dal', 'Memphis Grizzlies': 'mem', 'New Orleans Pelicans': 'no',
    'Houston Rockets': 'hou', 'San Antonio Spurs': 'sas',
  }
  const CHINESE_TO_CODE: Record<string, string> = {
    '凯尔特人': 'bos', '篮网': 'bkn', '尼克斯': 'nyk',
    '76人': 'phi', '猛龙': 'tor',
    '公牛': 'chi', '骑士': 'cle', '活塞': 'det', '步行者': 'ind', '雄鹿': 'mil',
    '老鹰': 'atl', '黄蜂': 'cha', '热火': 'mia', '魔术': 'orl', '奇才': 'was',
    '掘金': 'den', '森林狼': 'min', '雷霆': 'okc', '开拓者': 'por', '爵士': 'utah',
    '勇士': 'gs', '快船': 'lac', '湖人': 'lal', '太阳': 'phx', '国王': 'sac',
    '独行侠': 'dal', '灰熊': 'mem', '鹈鹕': 'no', '火箭': 'hou', '马刺': 'sas',
  }
  const map: Record<string, TeamColors> = {}
  for (const [full, colors] of Object.entries(teamColorMap)) {
    const code = NAME_TO_CODE[full]
    if (code) map[code] = colors
  }
  for (const [cn, code] of Object.entries(CHINESE_TO_CODE)) {
    if (map[code]) map[cn] = map[code]
  }
  return map
})()

/**
 * 按任意名称（中文队名 / 英文全称 / 简称）获取球队品牌色
 * @returns 颜色对象，未知球队返回 undefined
 */
export function getTeamBrandColor(name: string): TeamColors | undefined {
  if (!name) return undefined
  if (CODE_COLORS[name]) return CODE_COLORS[name]
  const colors = teamColorMap[name]
  if (colors) return colors
  // 英文简称模糊匹配
  const lower = name.toLowerCase()
  for (const [key, value] of Object.entries(teamColorMap)) {
    if (key.toLowerCase().includes(lower)) return value
  }
  return undefined
}

/**
 * 获取球队副色
 * @param teamName 球队全名
 * @returns 副色 hex 值
 */
export function getTeamSecondaryColor(teamName: string): string {
  return getTeamColor(teamName).secondary
}

/** 导出所有球队颜色映射 */
export const teamColors = teamColorMap
