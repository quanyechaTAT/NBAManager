/**
 * NBA球队Logo映射（优先使用本地静态资源，兜底ESPN CDN）
 */

// 本地静态资源路径前缀
const LOCAL_PATH = '/images/team-logos/'
// ESPN CDN 兜底前缀
const ESPN_CDN = 'https://a.espncdn.com/i/teamlogos/nba/500/'

/** 球队代码 - 中文名 / 英文名 到文件名的映射 */
const TEAM_FILE_MAP: Record<string, string> = {
  // --- 中文名 ---
  '凯尔特人': 'bos', '篮网': 'bkn', '尼克斯': 'nyk',
  '76人': 'phi', '猛龙': 'tor',
  '公牛': 'chi', '骑士': 'cle', '活塞': 'det', '步行者': 'ind', '雄鹿': 'mil',
  '老鹰': 'atl', '黄蜂': 'cha', '热火': 'mia', '魔术': 'orl', '奇才': 'was',
  '掘金': 'den', '森林狼': 'min', '雷霆': 'okc', '开拓者': 'por', '爵士': 'utah',
  '勇士': 'gs', '快船': 'lac', '湖人': 'lal', '太阳': 'phx', '国王': 'sac',
  '独行侠': 'dal', '灰熊': 'mem', '鹈鹕': 'no', '火箭': 'hou', '马刺': 'sas',

  // --- 英文简称 ---
  'Celtics': 'bos', 'Nets': 'bkn', 'Knicks': 'nyk',
  '76ers': 'phi', 'Raptors': 'tor',
  'Bulls': 'chi', 'Cavaliers': 'cle', 'Pistons': 'det', 'Pacers': 'ind', 'Bucks': 'mil',
  'Hawks': 'atl', 'Hornets': 'cha', 'Heat': 'mia', 'Magic': 'orl', 'Wizards': 'was',
  'Nuggets': 'den', 'Timberwolves': 'min', 'Thunder': 'okc',
  'Trail Blazers': 'por', 'Jazz': 'utah',
  'Warriors': 'gs', 'Clippers': 'lac', 'Lakers': 'lal', 'Suns': 'phx', 'Kings': 'sac',
  'Mavericks': 'dal', 'Grizzlies': 'mem', 'Pelicans': 'no', 'Rockets': 'hou', 'Spurs': 'sas',
}

/** 名称变体 → 标准英文简称 */
const NAME_VARIANTS: Record<string, string> = {
  'Boston Celtics': 'Celtics', 'Brooklyn Nets': 'Nets',
  'New York Knicks': 'Knicks', 'Philadelphia 76ers': '76ers',
  'Toronto Raptors': 'Raptors',
  'Chicago Bulls': 'Bulls', 'Cleveland Cavaliers': 'Cavaliers',
  'Detroit Pistons': 'Pistons', 'Indiana Pacers': 'Pacers',
  'Milwaukee Bucks': 'Bucks', 'Atlanta Hawks': 'Hawks',
  'Charlotte Hornets': 'Hornets', 'Miami Heat': 'Heat',
  'Orlando Magic': 'Magic', 'Washington Wizards': 'Wizards',
  'Denver Nuggets': 'Nuggets', 'Minnesota Timberwolves': 'Timberwolves',
  'Oklahoma City Thunder': 'Thunder', 'Portland Trail Blazers': 'Trail Blazers',
  'Utah Jazz': 'Jazz', 'Golden State Warriors': 'Warriors',
  'LA Clippers': 'Clippers', 'Los Angeles Clippers': 'Clippers',
  'Los Angeles Lakers': 'Lakers', 'LA Lakers': 'Lakers',
  'Phoenix Suns': 'Suns', 'Sacramento Kings': 'Kings',
  'Dallas Mavericks': 'Mavericks', 'Memphis Grizzlies': 'Grizzlies',
  'New Orleans Pelicans': 'Pelicans', 'Houston Rockets': 'Rockets',
  'San Antonio Spurs': 'Spurs',
}

/** 获取球队图标文件名（三字母缩写） */
export function getTeamCode(name: string): string | undefined {
  if (!name) return undefined
  // 1. 直接匹配
  if (TEAM_FILE_MAP[name]) return TEAM_FILE_MAP[name]
  // 2. 名称变体
  const variant = NAME_VARIANTS[name]
  if (variant && TEAM_FILE_MAP[variant]) return TEAM_FILE_MAP[variant]
  // 3. 模糊匹配
  const lower = name.toLowerCase()
  for (const [key, code] of Object.entries(TEAM_FILE_MAP)) {
    if (lower.includes(key.toLowerCase())) return code
  }
  return undefined
}

/**
 * 获取球队Logo URL（优先本地，兜底 ESPN CDN）
 * @param teamName 球队名称（支持中文、英文、全称）
 * @returns Logo URL 或 undefined
 */
export function getTeamLogo(teamName: string): string | undefined {
  if (!teamName || teamName === '待定' || teamName === 'TBD') {
    return undefined
  }

  const code = getTeamCode(teamName)
  if (code) {
    // 优先本地静态资源（在 Vite 中 public 目录下的文件会按原路径提供）
    return `${LOCAL_PATH}${code}.png`
  }

  console.warn(`未找到球队Logo: ${teamName}`)
  return undefined
}

/**
 * 获取球队Logo CDN 兜底 URL（当本地图片加载失败时尝试）
 */
export function getTeamLogoFallback(teamName: string): string | undefined {
  const code = getTeamCode(teamName)
  if (code) return `${ESPN_CDN}${code}.png`
  return undefined
}

/**
 * 批量获取球队Logo
 */
export function getTeamLogos(teamNames: string[]): Record<string, string | undefined> {
  const result: Record<string, string | undefined> = {}
  teamNames.forEach(name => {
    result[name] = getTeamLogo(name)
  })
  return result
}

