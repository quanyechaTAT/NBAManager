-- NBA 2025-26赛季球队战绩更新脚本
-- 数据来源：ESPN API (https://site.api.espn.com/apis/v2/sports/basketball/nba/standings)
-- 更新时间：2026年5月30日

-- 更新现有球队战绩
UPDATE teams SET wins = 60, losses = 22, city = '底特律', conference = '东部' WHERE name = '活塞';
UPDATE teams SET wins = 46, losses = 36, city = '亚特兰大', conference = '东部' WHERE name = '老鹰';
UPDATE teams SET wins = 56, losses = 26, city = '波士顿', conference = '东部' WHERE name = '凯尔特人';
UPDATE teams SET wins = 45, losses = 37, city = '费城', conference = '东部' WHERE name = '76人';
UPDATE teams SET wins = 45, losses = 37, city = '奥兰多', conference = '东部' WHERE name = '魔术';
UPDATE teams SET wins = 46, losses = 36, city = '多伦多', conference = '东部' WHERE name = '猛龙';
UPDATE teams SET wins = 52, losses = 30, city = '克利夫兰', conference = '东部' WHERE name = '骑士';
UPDATE teams SET wins = 53, losses = 29, city = '纽约', conference = '东部' WHERE name = '尼克斯';
UPDATE teams SET wins = 43, losses = 39, city = '迈阿密', conference = '东部' WHERE name = '热火';
UPDATE teams SET wins = 44, losses = 38, city = '夏洛特', conference = '东部' WHERE name = '黄蜂';
UPDATE teams SET wins = 17, losses = 65, city = '华盛顿', conference = '东部' WHERE name = '奇才';
UPDATE teams SET wins = 19, losses = 63, city = '印第安纳', conference = '东部' WHERE name = '步行者';
UPDATE teams SET wins = 20, losses = 62, city = '布鲁克林', conference = '东部' WHERE name = '篮网';
UPDATE teams SET wins = 31, losses = 51, city = '芝加哥', conference = '东部' WHERE name = '公牛';
UPDATE teams SET wins = 32, losses = 50, city = '密尔沃基', conference = '东部' WHERE name = '雄鹿';

UPDATE teams SET wins = 53, losses = 29, city = '洛杉矶', conference = '西部' WHERE name = '湖人';
UPDATE teams SET wins = 62, losses = 20, city = '圣安东尼奥', conference = '西部' WHERE name = '马刺';
UPDATE teams SET wins = 42, losses = 40, city = '波特兰', conference = '西部' WHERE name = '开拓者';
UPDATE teams SET wins = 45, losses = 37, city = '菲尼克斯', conference = '西部' WHERE name = '太阳';
UPDATE teams SET wins = 49, losses = 33, city = '明尼苏达', conference = '西部' WHERE name = '森林狼';
UPDATE teams SET wins = 52, losses = 30, city = '休斯顿', conference = '西部' WHERE name = '火箭';
UPDATE teams SET wins = 54, losses = 28, city = '丹佛', conference = '西部' WHERE name = '掘金';
UPDATE teams SET wins = 37, losses = 45, city = '金州', conference = '西部' WHERE name = '勇士';
UPDATE teams SET wins = 42, losses = 40, city = '洛杉矶', conference = '西部' WHERE name = '快船';
UPDATE teams SET wins = 22, losses = 60, city = '萨克拉门托', conference = '西部' WHERE name = '国王';
UPDATE teams SET wins = 22, losses = 60, city = '犹他', conference = '西部' WHERE name = '爵士';
UPDATE teams SET wins = 25, losses = 57, city = '孟菲斯', conference = '西部' WHERE name = '灰熊';
UPDATE teams SET wins = 26, losses = 56, city = '新奥尔良', conference = '西部' WHERE name = '鹈鹕';
UPDATE teams SET wins = 26, losses = 56, city = '达拉斯', conference = '西部' WHERE name = '独行侠';
UPDATE teams SET wins = 64, losses = 18, city = '俄克拉荷马城', conference = '西部' WHERE name = '雷霆';

-- 插入不存在的球队（如果需要）
-- 注意：这些INSERT语句只会在球队不存在时执行

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '活塞', '底特律', '东部', 60, 22 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '活塞');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '老鹰', '亚特兰大', '东部', 46, 36 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '老鹰');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '76人', '费城', '东部', 45, 37 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '76人');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '魔术', '奥兰多', '东部', 45, 37 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '魔术');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '猛龙', '多伦多', '东部', 46, 36 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '猛龙');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '骑士', '克利夫兰', '东部', 52, 30 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '骑士');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '尼克斯', '纽约', '东部', 53, 29 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '尼克斯');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '热火', '迈阿密', '东部', 43, 39 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '热火');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '黄蜂', '夏洛特', '东部', 44, 38 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '黄蜂');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '奇才', '华盛顿', '东部', 17, 65 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '奇才');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '步行者', '印第安纳', '东部', 19, 63 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '步行者');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '篮网', '布鲁克林', '东部', 20, 62 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '篮网');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '公牛', '芝加哥', '东部', 31, 51 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '公牛');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '马刺', '圣安东尼奥', '西部', 62, 20 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '马刺');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '开拓者', '波特兰', '西部', 42, 40 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '开拓者');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '森林狼', '明尼苏达', '西部', 49, 33 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '森林狼');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '火箭', '休斯顿', '西部', 52, 30 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '火箭');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '快船', '洛杉矶', '西部', 42, 40 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '快船');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '国王', '萨克拉门托', '西部', 22, 60 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '国王');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '爵士', '犹他', '西部', 22, 60 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '爵士');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '灰熊', '孟菲斯', '西部', 25, 57 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '灰熊');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '鹈鹕', '新奥尔良', '西部', 26, 56 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '鹈鹕');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '独行侠', '达拉斯', '西部', 26, 56 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '独行侠');

INSERT INTO teams (name, city, conference, wins, losses)
SELECT '雷霆', '俄克拉荷马城', '西部', 64, 18 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM teams WHERE name = '雷霆');

-- 验证更新结果
SELECT name AS 球队, city AS 城市, conference AS 联盟, wins AS 胜, losses AS 负
FROM teams
ORDER BY conference, wins DESC;
