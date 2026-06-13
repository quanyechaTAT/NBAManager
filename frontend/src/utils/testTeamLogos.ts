import { getTeamLogo } from './teamLogos'

// 测试常见的球队名称格式
const testTeamNames = [
  // 中文名称
  '湖人', '勇士', '凯尔特人', '76人', '热火',
  // 英文简称
  'Lakers', 'Warriors', 'Celtics', '76ers', 'Heat',
  // 英文全称
  'Los Angeles Lakers', 'Golden State Warriors', 'Boston Celtics',
  // 待定
  '待定', 'TBD'
]

console.log('=== 球队Logo映射测试 ===')
testTeamNames.forEach(name => {
  const logo = getTeamLogo(name)
  console.log(`${name}: ${logo ? '✓ ' + logo : '✗ 未找到'}`)
})

export default {}
