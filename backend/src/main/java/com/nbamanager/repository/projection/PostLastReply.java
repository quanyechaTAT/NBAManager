package com.nbamanager.repository.projection;

import java.time.LocalDateTime;

/**
 * 按帖子聚合的"最后回复时间"投影。
 *
 * 列表场景下原先对每一行都查一次 {@code findTopByPostIdOrderByCreateTimeDesc}，
 * 换成一条 GROUP BY MAX(createTime)。
 */
public interface PostLastReply {

    Long getPostId();

    LocalDateTime getLastReplyTime();
}
