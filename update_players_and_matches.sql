-- NBA 2025-26赛季球员和对战记录更新脚本
-- 更新时间：2026年5月30日

-- ==================== 球员数据更新 ====================

-- 清空现有球员数据（可选，如果需要完全重置）
-- TRUNCATE TABLE players;

-- 湖人队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '勒布朗·詹姆斯', id, '小前锋', 25.7, 7.3, 8.3, 1.3 FROM teams WHERE name = '湖人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '勒布朗·詹姆斯' AND team_id = (SELECT id FROM teams WHERE name = '湖人'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '安东尼·戴维斯', id, '大前锋', 24.7, 12.6, 3.5, 1.2 FROM teams WHERE name = '湖人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '安东尼·戴维斯' AND team_id = (SELECT id FROM teams WHERE name = '湖人'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '奥斯汀·里夫斯', id, '分卫', 18.2, 4.5, 6.1, 0.9 FROM teams WHERE name = '湖人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '奥斯汀·里夫斯' AND team_id = (SELECT id FROM teams WHERE name = '湖人'));

-- 勇士队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '斯蒂芬·库里', id, '控卫', 26.4, 4.5, 5.1, 0.9 FROM teams WHERE name = '勇士'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '斯蒂芬·库里' AND team_id = (SELECT id FROM teams WHERE name = '勇士'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '安德鲁·维金斯', id, '小前锋', 17.8, 4.8, 2.5, 1.0 FROM teams WHERE name = '勇士'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '安德鲁·维金斯' AND team_id = (SELECT id FROM teams WHERE name = '勇士'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '德雷蒙德·格林', id, '大前锋', 8.5, 6.8, 5.2, 1.1 FROM teams WHERE name = '勇士'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '德雷蒙德·格林' AND team_id = (SELECT id FROM teams WHERE name = '勇士'));

-- 凯尔特人队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '杰森·塔图姆', id, '小前锋', 26.9, 8.1, 4.9, 1.0 FROM teams WHERE name = '凯尔特人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '杰森·塔图姆' AND team_id = (SELECT id FROM teams WHERE name = '凯尔特人'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '杰伦·布朗', id, '分卫', 23.0, 5.5, 3.6, 1.1 FROM teams WHERE name = '凯尔特人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '杰伦·布朗' AND team_id = (SELECT id FROM teams WHERE name = '凯尔特人'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '德里克·怀特', id, '控卫', 15.2, 4.2, 4.8, 0.8 FROM teams WHERE name = '凯尔特人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '德里克·怀特' AND team_id = (SELECT id FROM teams WHERE name = '凯尔特人'));

-- 雄鹿队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '扬尼斯·阿德托昆博', id, '大前锋', 30.4, 11.5, 6.5, 1.2 FROM teams WHERE name = '雄鹿'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '扬尼斯·阿德托昆博' AND team_id = (SELECT id FROM teams WHERE name = '雄鹿'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '达米安·利拉德', id, '控卫', 24.3, 4.4, 7.0, 1.0 FROM teams WHERE name = '雄鹿'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '达米安·利拉德' AND team_id = (SELECT id FROM teams WHERE name = '雄鹿'));

-- 掘金队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '尼古拉·约基奇', id, '中锋', 26.4, 12.4, 9.0, 1.4 FROM teams WHERE name = '掘金'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '尼古拉·约基奇' AND team_id = (SELECT id FROM teams WHERE name = '掘金'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '贾马尔·穆雷', id, '控卫', 21.2, 4.1, 6.5, 0.9 FROM teams WHERE name = '掘金'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '贾马尔·穆雷' AND team_id = (SELECT id FROM teams WHERE name = '掘金'));

-- 太阳队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '凯文·杜兰特', id, '小前锋', 27.1, 6.6, 5.0, 0.8 FROM teams WHERE name = '太阳'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '凯文·杜兰特' AND team_id = (SELECT id FROM teams WHERE name = '太阳'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '德文·布克', id, '分卫', 27.1, 4.5, 6.9, 0.9 FROM teams WHERE name = '太阳'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '德文·布克' AND team_id = (SELECT id FROM teams WHERE name = '太阳'));

-- 雷霆队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '谢伊·吉尔杰斯-亚历山大', id, '控卫', 30.1, 5.5, 6.2, 2.0 FROM teams WHERE name = '雷霆'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '谢伊·吉尔杰斯-亚历山大' AND team_id = (SELECT id FROM teams WHERE name = '雷霆'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '切特·霍姆格伦', id, '中锋', 16.5, 7.8, 2.4, 0.8 FROM teams WHERE name = '雷霆'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '切特·霍姆格伦' AND team_id = (SELECT id FROM teams WHERE name = '雷霆'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '杰伦·威廉姆斯', id, '小前锋', 21.0, 5.5, 5.0, 1.2 FROM teams WHERE name = '雷霆'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '杰伦·威廉姆斯' AND team_id = (SELECT id FROM teams WHERE name = '雷霆'));

-- 马刺队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '维克托·文班亚马', id, '中锋', 24.5, 10.8, 3.9, 1.2 FROM teams WHERE name = '马刺'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '维克托·文班亚马' AND team_id = (SELECT id FROM teams WHERE name = '马刺'));

-- 独行侠队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '卢卡·东契奇', id, '控卫', 28.5, 8.0, 8.8, 1.2 FROM teams WHERE name = '独行侠'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '卢卡·东契奇' AND team_id = (SELECT id FROM teams WHERE name = '独行侠'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '凯里·欧文', id, '分卫', 25.6, 5.2, 5.2, 1.1 FROM teams WHERE name = '独行侠'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '凯里·欧文' AND team_id = (SELECT id FROM teams WHERE name = '独行侠'));

-- 尼克斯队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '杰伦·布伦森', id, '控卫', 28.7, 3.5, 6.7, 0.9 FROM teams WHERE name = '尼克斯'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '杰伦·布伦森' AND team_id = (SELECT id FROM teams WHERE name = '尼克斯'));

-- 76人队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '乔尔·恩比德', id, '中锋', 27.9, 11.2, 5.5, 1.2 FROM teams WHERE name = '76人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '乔尔·恩比德' AND team_id = (SELECT id FROM teams WHERE name = '76人'));

INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '泰瑞斯·马克西', id, '控卫', 25.9, 3.7, 6.2, 1.0 FROM teams WHERE name = '76人'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '泰瑞斯·马克西' AND team_id = (SELECT id FROM teams WHERE name = '76人'));

-- 森林狼队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '安东尼·爱德华兹', id, '分卫', 25.9, 5.4, 5.1, 1.3 FROM teams WHERE name = '森林狼'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '安东尼·爱德华兹' AND team_id = (SELECT id FROM teams WHERE name = '森林狼'));

-- 骑士队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '多诺万·米切尔', id, '分卫', 26.6, 4.3, 6.1, 1.8 FROM teams WHERE name = '骑士'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '多诺万·米切尔' AND team_id = (SELECT id FROM teams WHERE name = '骑士'));

-- 活塞队球员
INSERT INTO players (name, team_id, position, points_per_game, rebounds_per_game, assists_per_game, steals_per_game)
SELECT '凯德·坎宁安', id, '控卫', 24.0, 6.2, 9.3, 1.1 FROM teams WHERE name = '活塞'
AND NOT EXISTS (SELECT 1 FROM players WHERE name = '凯德·坎宁安' AND team_id = (SELECT id FROM teams WHERE name = '活塞'));

-- ==================== 对战记录更新 ====================

-- 西部强强对话
INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('雷霆', '马刺', 118, 112, DATE_SUB(CURDATE(), INTERVAL 60 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('马刺', '雷霆', 105, 110, DATE_SUB(CURDATE(), INTERVAL 45 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('雷霆', '马刺', 125, 108, DATE_SUB(CURDATE(), INTERVAL 10 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('雷霆', '掘金', 122, 118, DATE_SUB(CURDATE(), INTERVAL 55 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('掘金', '雷霆', 115, 108, DATE_SUB(CURDATE(), INTERVAL 35 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('雷霆', '湖人', 130, 120, DATE_SUB(CURDATE(), INTERVAL 50 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('湖人', '雷霆', 112, 125, DATE_SUB(CURDATE(), INTERVAL 20 DAY), '2025-26', 'FINISHED');

-- 湖人 vs 勇士 (经典对决)
INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('湖人', '勇士', 112, 108, DATE_SUB(CURDATE(), INTERVAL 55 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('勇士', '湖人', 105, 98, DATE_SUB(CURDATE(), INTERVAL 40 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('湖人', '勇士', 118, 110, DATE_SUB(CURDATE(), INTERVAL 15 DAY), '2025-26', 'FINISHED');

-- 凯尔特人 vs 尼克斯
INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('凯尔特人', '尼克斯', 120, 108, DATE_SUB(CURDATE(), INTERVAL 56 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('尼克斯', '凯尔特人', 115, 118, DATE_SUB(CURDATE(), INTERVAL 36 DAY), '2025-26', 'FINISHED');

-- 凯尔特人 vs 骑士
INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('凯尔特人', '骑士', 125, 118, DATE_SUB(CURDATE(), INTERVAL 44 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('骑士', '凯尔特人', 110, 115, DATE_SUB(CURDATE(), INTERVAL 14 DAY), '2025-26', 'FINISHED');

-- 活塞 vs 骑士
INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('活塞', '骑士', 118, 112, DATE_SUB(CURDATE(), INTERVAL 54 DAY), '2025-26', 'FINISHED');

INSERT INTO match_records (home_team, away_team, home_score, away_score, match_date, season, status)
VALUES ('骑士', '活塞', 108, 115, DATE_SUB(CURDATE(), INTERVAL 24 DAY), '2025-26', 'FINISHED');

-- 验证更新结果
SELECT '球员统计' as info, COUNT(*) as count FROM players
UNION ALL
SELECT '对战记录', COUNT(*) FROM match_records;
