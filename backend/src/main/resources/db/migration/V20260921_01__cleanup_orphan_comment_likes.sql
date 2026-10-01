-- F5 一次性数据修复：清理孤儿 comment_likes
--
-- 背景：CommentService.delete() 过去只删 comments 行与 posts.comment_count，
-- 没有清理 comment_likes，导致"评论已删、点赞行残留"的孤儿数据
-- （压测报告 §九 P4：删除评论后 comment_likes 残留；comments 维度
--  SUM(like_count)=3 vs COUNT(comment_likes)=5）。
-- 代码已在 CommentService.delete() 中补上级联清理，这里只做存量数据修复。
--
-- 幂等：DELETE ... WHERE NOT EXISTS 重复执行不会报错，也不会误删有效数据。
DELETE cl FROM comment_likes cl
WHERE NOT EXISTS (
    SELECT 1 FROM comments c WHERE c.id = cl.comment_id
);