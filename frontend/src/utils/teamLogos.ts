/**
 * NBA球队Logo映射（使用ESPN CDN）
 * teamName → logo URL
 */
const TEAM_LOGO_MAP: Record<string, string> = {
  '活塞': 'https://a.espncdn.com/i/teamlogos/nba/500/det.png',
  '老鹰': 'https://a.espncdn.com/i/teamlogos/nba/500/atl.png',
  '凯尔特人': 'https://a.espncdn.com/i/teamlogos/nba/500/bos.png',
  '76人': 'https://a.espncdn.com/i/teamlogos/nba/500/phi.png',
  '魔术': 'https://a.espncdn.com/i/teamlogos/nba/500/orl.png',
  '猛龙': 'https://a.espncdn.com/i/teamlogos/nba/500/tor.png',
  '骑士': 'https://a.espncdn.com/i/teamlogos/nba/500/cle.png',
  '尼克斯': 'https://a.espncdn.com/i/teamlogos/nba/500/nyk.png',
  '热火': 'https://a.espncdn.com/i/teamlogos/nba/500/mia.png',
  '黄蜂': 'https://a.espncdn.com/i/teamlogos/nba/500/cha.png',
  '奇才': 'https://a.espncdn.com/i/teamlogos/nba/500/was.png',
  '步行者': 'https://a.espncdn.com/i/teamlogos/nba/500/ind.png',
  '篮网': 'https://a.espncdn.com/i/teamlogos/nba/500/bkn.png',
  '公牛': 'https://a.espncdn.com/i/teamlogos/nba/500/chi.png',
  '雄鹿': 'https://a.espncdn.com/i/teamlogos/nba/500/mil.png',
  '湖人': 'https://a.espncdn.com/i/teamlogos/nba/500/lal.png',
  '马刺': 'https://a.espncdn.com/i/teamlogos/nba/500/sas.png',
  '开拓者': 'https://a.espncdn.com/i/teamlogos/nba/500/por.png',
  '太阳': 'https://a.espncdn.com/i/teamlogos/nba/500/phx.png',
  '森林狼': 'https://a.espncdn.com/i/teamlogos/nba/500/min.png',
  '火箭': 'https://a.espncdn.com/i/teamlogos/nba/500/hou.png',
  '掘金': 'https://a.espncdn.com/i/teamlogos/nba/500/den.png',
  '勇士': 'https://a.espncdn.com/i/teamlogos/nba/500/gs.png',
  '快船': 'https://a.espncdn.com/i/teamlogos/nba/500/lac.png',
  '国王': 'https://a.espncdn.com/i/teamlogos/nba/500/sac.png',
  '爵士': 'https://a.espncdn.com/i/teamlogos/nba/500/utah.png',
  '灰熊': 'https://a.espncdn.com/i/teamlogos/nba/500/mem.png',
  '鹈鹕': 'https://a.espncdn.com/i/teamlogos/nba/500/no.png',
  '独行侠': 'https://a.espncdn.com/i/teamlogos/nba/500/dal.png',
  '雷霆': 'https://a.espncdn.com/i/teamlogos/nba/500/okc.png',
}

export function getTeamLogo(teamName: string): string | undefined {
  return TEAM_LOGO_MAP[teamName] || undefined
}
