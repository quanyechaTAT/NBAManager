-- 第 5 轮 P2：notifications 增加 post_id（跳转用帖子定位）
--
-- 背景：第 4 轮把 COMMENT_LIKE 的 relatedId 从 postId 改为 commentId（去重键需要），
-- 前端按 relatedId 跳转帖子的行为随之失效。新增 post_id 冗余列：
-- COMMENT_LIKE 存 commentId（relatedId，去重键）+ postId（跳转用）；
-- 其余帖类通知 relatedId 本就是 postId，post_id 存同值；
-- 存量行 post_id 为 NULL，前端按"有 postId 用 postId，否则回退 relatedId"处理
-- （存量 COMMENT_LIKE 的 relatedId 恰好还是 postId，天然兼容，跳转不回归）。
--
-- 幂等：INFORMATION_SCHEMA 守卫，列已存在时跳过（对已有库重复执行安全）。
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'post_id'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE notifications ADD COLUMN post_id BIGINT NULL AFTER related_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
