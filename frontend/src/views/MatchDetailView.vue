<template>
  <div class="page match-detail-page">
    <div class="page-inner">
      <!-- 返回按钮 -->
      <div class="back-row">
        <el-button class="back-btn" link @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="m15 18-6-6 6-6"/></svg>
          返回
        </el-button>
      </div>

      <!-- 比赛头部 -->
      <div class="match-hero" v-loading="loading">
        <div class="match-hero-inner">
          <div
            class="match-hero-team"
            :class="{ 'is-clickable': isTeamNavigable(homeTeamName) }"
            :role="isTeamNavigable(homeTeamName) ? 'button' : undefined"
            :tabindex="isTeamNavigable(homeTeamName) ? 0 : -1"
            :title="isTeamNavigable(homeTeamName) ? `查看${homeTeamName}详情` : undefined"
            @click="goToTeamDetail(homeTeamName)"
            @keydown.enter="goToTeamDetail(homeTeamName)"
          >
            <div class="hero-team-icon">
              <img v-if="homeTeamLogo" :src="homeTeamLogo" alt="" class="team-logo-img" />
              <span v-else class="icon-fallback"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg></span>
            </div>
            <div class="hero-team-info">
              <div class="hero-team-name-row">
                <span class="hero-team-name">{{ homeTeamName }}</span>
                <span v-if="isTeamNavigable(homeTeamName)" class="hero-team-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 18l6-6-6-6"/></svg>
                </span>
              </div>
              <div class="hero-team-sub">
                <span class="hero-team-label">主场</span>
              </div>
            </div>
          </div>
          <div class="match-hero-center">
            <div class="hero-score-display">
              <span class="hero-score">{{ homeTotalScore }}</span>
              <span class="hero-score-sep">:</span>
              <span class="hero-score">{{ awayTotalScore }}</span>
            </div>
            <el-tag
              :type="statusType(status)"
              size="small"
              :class="{ 'live-badge': status === 'LIVE' }"
            >{{ statusLabel(status) }}</el-tag>
          </div>
          <div
            class="match-hero-team match-hero-team--away"
            :class="{ 'is-clickable': isTeamNavigable(awayTeamName) }"
            :role="isTeamNavigable(awayTeamName) ? 'button' : undefined"
            :tabindex="isTeamNavigable(awayTeamName) ? 0 : -1"
            :title="isTeamNavigable(awayTeamName) ? `查看${awayTeamName}详情` : undefined"
            @click="goToTeamDetail(awayTeamName)"
            @keydown.enter="goToTeamDetail(awayTeamName)"
          >
            <div class="hero-team-info hero-team-info--right">
              <div class="hero-team-name-row hero-team-name-row--reverse">
                <span v-if="isTeamNavigable(awayTeamName)" class="hero-team-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M15 18l-6-6 6-6"/></svg>
                </span>
                <span class="hero-team-name">{{ awayTeamName }}</span>
              </div>
              <div class="hero-team-sub hero-team-sub--right">
                <span class="hero-team-label">客场</span>
              </div>
            </div>
            <div class="hero-team-icon">
              <img v-if="awayTeamLogo" :src="awayTeamLogo" alt="" class="team-logo-img" />
              <span v-else class="icon-fallback"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="1em" height="1em"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 关键数据对比：统计缺失时明确说明，不渲染全零 -->
      <div class="key-comparison" v-if="boxScore">
        <div class="section-header">
          <div class="section-header-left"><h2>关键数据对比</h2></div>
        </div>
        <div class="comparison-grid" v-if="statsAvailable">
          <div class="comparison-item" v-for="item in comparisonItems" :key="item.label">
            <span class="comp-value" :class="{ win: comparisonWinner(item, 'home') }">{{ item.homeValue }}</span>
            <span class="comp-label">{{ item.label }}</span>
            <span class="comp-value" :class="{ win: comparisonWinner(item, 'away') }">{{ item.awayValue }}</span>
          </div>
        </div>
        <p v-else class="stats-unavailable">{{ stats.hasPlayers ? '双方统计尚未齐全，暂无法对比' : '统计暂未提供' }}</p>
      </div>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="技术统计" name="stats">
          <!-- 将节末累计比分转换为单节得分，并附上比赛总比分 -->
          <div class="section-header">
            <div class="section-header-left">
              <h2>每节比分</h2>
            </div>
          </div>
          <el-card shadow="never" class="quarter-card">
            <el-table :data="periodScoreRows" stripe v-loading="quarterLoading" :empty-text="quarterEmptyText" :row-class-name="periodScoreRowClass">
              <el-table-column label="节次" width="120" align="center">
                <template #default="{ row }">
                  <span class="period-label">{{ row.label }}</span>
                </template>
              </el-table-column>
              <el-table-column :label="homeTeamName !== '--' ? homeTeamName : '主队'" align="center">
                <template #default="{ row }">
                  <span class="quarter-score" :class="{ 'quarter-score--win': row.homeScore != null && row.awayScore != null && row.homeScore > row.awayScore }">{{ row.homeScore ?? '--' }}</span>
                </template>
              </el-table-column>
              <el-table-column :label="awayTeamName !== '--' ? awayTeamName : '客队'" align="center">
                <template #default="{ row }">
                  <span class="quarter-score" :class="{ 'quarter-score--win': row.homeScore != null && row.awayScore != null && row.awayScore > row.homeScore }">{{ row.awayScore ?? '--' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 球员统计 -->
          <div class="section-header">
            <div class="section-header-left">
              <h3 class="team-table-title">{{ homeTeamName !== '--' ? homeTeamName : '主队' }} · 阵容技术统计</h3>
            </div>
          </div>
          <el-card shadow="never" class="boxscore-card">
            <el-table :data="sortedHomePlayers" stripe v-loading="boxscoreLoading" :empty-text="boxScoreEmptyText" size="small" :row-class-name="boxScoreRowClass">
              <el-table-column label="球员" min-width="140" fixed="left">
                <template #default="{ row }">
                  <div class="player-cell">
                    <span class="player-name" :class="{ 'player-starter': row.starter }">{{ row.playerName }}</span>
                    <el-tag v-if="row.starter" size="small" type="success" effect="dark" class="starter-tag">首发</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="时间 MIN" width="76" align="center" prop="minutes" />
              <el-table-column label="得分 PTS" width="74" align="center" prop="points" sortable />
              <el-table-column label="篮板 REB" width="74" align="center" prop="rebounds" sortable />
              <el-table-column label="助攻 AST" width="74" align="center" prop="assists" sortable />
              <el-table-column label="抢断 STL" width="70" align="center" prop="steals" />
              <el-table-column label="盖帽 BLK" width="70" align="center" prop="blocks" />
              <el-table-column label="失误 TO" width="68" align="center" prop="turnovers" />
              <el-table-column label="投篮 FG" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.fgMade, row.fgAttempted, row.fgPct) }}</span></template>
              </el-table-column>
              <el-table-column label="三分 3PT" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.threeMade, row.threeAttempted, row.threePct) }}</span></template>
              </el-table-column>
              <el-table-column label="罚球 FT" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.ftMade, row.ftAttempted, row.ftPct) }}</span></template>
              </el-table-column>
              <el-table-column label="+/-" width="76" align="center" prop="plusMinus" sortable>
                <template #default="{ row }">
                  <span class="plus-minus-badge" :class="row.plusMinus > 0 ? 'plus-minus-badge--pos' : (row.plusMinus < 0 ? 'plus-minus-badge--neg' : 'plus-minus-badge--zero')">
                    {{ row.plusMinus > 0 ? '+' : '' }}{{ row.plusMinus }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <div class="section-header" style="margin-top: 24px;">
            <div class="section-header-left">
              <h3 class="team-table-title">{{ awayTeamName !== '--' ? awayTeamName : '客队' }} · 阵容技术统计</h3>
            </div>
          </div>
          <el-card shadow="never" class="boxscore-card">
            <el-table :data="sortedAwayPlayers" stripe v-loading="boxscoreLoading" :empty-text="boxScoreEmptyText" size="small" :row-class-name="boxScoreRowClass">
              <el-table-column label="球员" min-width="140" fixed="left">
                <template #default="{ row }">
                  <div class="player-cell">
                    <span class="player-name" :class="{ 'player-starter': row.starter }">{{ row.playerName }}</span>
                    <el-tag v-if="row.starter" size="small" type="success" effect="dark" class="starter-tag">首发</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="时间 MIN" width="76" align="center" prop="minutes" />
              <el-table-column label="得分 PTS" width="74" align="center" prop="points" sortable />
              <el-table-column label="篮板 REB" width="74" align="center" prop="rebounds" sortable />
              <el-table-column label="助攻 AST" width="74" align="center" prop="assists" sortable />
              <el-table-column label="抢断 STL" width="70" align="center" prop="steals" />
              <el-table-column label="盖帽 BLK" width="70" align="center" prop="blocks" />
              <el-table-column label="失误 TO" width="68" align="center" prop="turnovers" />
              <el-table-column label="投篮 FG" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.fgMade, row.fgAttempted, row.fgPct) }}</span></template>
              </el-table-column>
              <el-table-column label="三分 3PT" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.threeMade, row.threeAttempted, row.threePct) }}</span></template>
              </el-table-column>
              <el-table-column label="罚球 FT" width="112" align="center">
                <template #default="{ row }"><span class="shooting-stat">{{ formatShooting(row.ftMade, row.ftAttempted, row.ftPct) }}</span></template>
              </el-table-column>
              <el-table-column label="+/-" width="76" align="center" prop="plusMinus" sortable>
                <template #default="{ row }">
                  <span class="plus-minus-badge" :class="row.plusMinus > 0 ? 'plus-minus-badge--pos' : (row.plusMinus < 0 ? 'plus-minus-badge--neg' : 'plus-minus-badge--zero')">
                    {{ row.plusMinus > 0 ? '+' : '' }}{{ row.plusMinus }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="投篮热图" name="shotchart">
          <GameShotChartPanel
            :shots="gameShots"
            :mode="shotChartMode"
            :source="shotChartSource"
            :message="shotChartMessage"
            :loading="shotChartLoading"
            :home-team-id="homeTeamIdForShot"
            :away-team-id="awayTeamIdForShot"
            :home-team-name="homeTeamNameForShot"
            :away-team-name="awayTeamNameForShot"
          />
        </el-tab-pane>

        <el-tab-pane label="战术情报与复盘" name="playbyplay">
          <div v-if="replayLoading" class="loading-state">加载中...</div>
          <ReplayAvailabilityPanel
            :state="replayState"
            :quality="replayQuality"
            :error="replayError"
            :operation-id="replayOperationId"
            :retry-after-seconds="replayRetryAfterSeconds"
            :busy="replayBusy"
            @prepare="replay.prepare"
            @retry="replay.retry"
            @refresh="replay.refresh"
          />
          <div v-if="!replayLoading && replaySummaryOnly" class="summary-only-panel">
            <div class="summary-only-head">
              <h3>节末比分摘要</h3>
              <p>当前来源只提供节末比分，页面不会生成走势、AI 解读或伪逐回合事件。</p>
            </div>
            <div class="summary-only-grid">
              <div v-for="event in timelineEvents" :key="event.id ?? event.eventOrder" class="summary-only-item">
                <span>{{ periodLabel(event.period) }}</span>
                <strong>{{ event.homeScore }} - {{ event.awayScore }}</strong>
              </div>
            </div>
          </div>
          <GameIntelligenceCenter
            v-if="!replayLoading && showReplayCenter"
            :game-id="gameId"
            :home-team="homeTeamName !== '--' ? homeTeamName : ''"
            :away-team="awayTeamName !== '--' ? awayTeamName : ''"
            :timeline-events="timelineEvents"
            :momentum-data="momentumData"
            :highlights="highlightsData"
            :turning-points="turningPointsData"
            :summary="gameSummary"
            :comparison-items="comparisonItems"
            :home-players="sortedHomePlayers"
            :away-players="sortedAwayPlayers"
            :selected-event="selectedTimelineEvent"
            @select-event="selectedTimelineEvent = $event"
          />
          <div v-if="!replayLoading && !replaySummaryOnly && !showReplayCenter" class="empty-inline">暂无可展示的比赛回放事实</div>
        </el-tab-pane>

        <el-tab-pane label="观赛聊天室" name="livechat">
          <LiveChat :game-id="gameId" :home-team="homeTeamName !== '--' ? homeTeamName : ''" :away-team="awayTeamName !== '--' ? awayTeamName : ''" />
        </el-tab-pane>

        <el-tab-pane label="赛后热评" name="comments">
          <!-- 战术复盘与赛后热评区 -->
          <div class="comment-section">
            <!-- 评论区顶栏：标题、战术标签与排序切换 -->
            <div class="comment-section-header">
              <div class="comment-section-title-wrap">
                <div class="comment-title-main">
                  <span class="comment-section-badge">战术复盘</span>
                  <h3 class="comment-section-title">赛后深度复盘与球迷热评</h3>
                </div>
                <p class="comment-section-desc">就球论球 · 拆解关键攻防回合 · 传递专业比赛洞察</p>
              </div>

              <!-- 排序切换 -->
              <div class="comment-sort-tabs">
                <button
                  type="button"
                  class="sort-tab-btn"
                  :class="{ active: commentSort === 'hot' }"
                  @click="commentSort = 'hot'"
                >
                  最热复盘
                </button>
                <button
                  type="button"
                  class="sort-tab-btn"
                  :class="{ active: commentSort === 'latest' }"
                  @click="commentSort = 'latest'"
                >
                  最新发布
                </button>
              </div>
            </div>

            <!-- 战术话题快捷插入标签 -->
            <div class="tactical-tags-strip">
              <span class="tactical-tags-label">复盘话题:</span>
              <div class="tactical-tags-list">
                <button
                  v-for="tag in tacticalTopics"
                  :key="tag"
                  type="button"
                  class="tactical-tag-pill"
                  @click="insertTacticalTag(tag)"
                >
                  {{ tag }}
                </button>
              </div>
            </div>

            <!-- 发表评论卡片 -->
            <div class="comment-composer">
              <div class="comment-composer-heading">
                <div class="composer-user-preview">
                  <span class="composer-avatar">
                    <img v-if="auth.avatarUrl" :src="auth.avatarUrl" alt="" @error="onCommentAvatarError" />
                    <span v-else class="composer-avatar-text">{{ auth.username?.charAt(0)?.toUpperCase() || '我' }}</span>
                  </span>
                  <div class="composer-user-text">
                    <span class="composer-username">{{ auth.username || '登录参与复盘' }}</span>
                    <span class="composer-tip">分享你对本场攻防博弈与胜负手的高见</span>
                  </div>
                </div>
              </div>
              <div class="comment-input">
                <el-input
                  ref="commentInputRef"
                  v-model="newComment"
                  type="textarea"
                  :rows="4"
                  placeholder="写下你的专业战术复盘或观赛感想（支持点击上方快捷话题插入）..."
                  maxlength="2000"
                  show-word-limit
                />
              </div>
              <div class="comment-input-actions">
                <div class="comment-community-note">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>
                    <path d="m9 12 2 2 4-4"/>
                  </svg>
                  <span>文明复盘，就球论球，理性讨论比赛事实</span>
                </div>
                <div class="composer-btn-group">
                  <div
                    ref="commentEmojiBtn"
                    class="emoji-trigger"
                    title="插入表情"
                    @click="emoji.open(commentEmojiBtn!, e => newComment += e)"
                  >
                    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10" />
                      <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                      <line x1="9" y1="9" x2="9.01" y2="9" />
                      <line x1="15" y1="9" x2="15.01" y2="9" />
                    </svg>
                    <span class="emoji-trigger-label">表情</span>
                  </div>
                  <el-button
                    class="comment-submit"
                    type="primary"
                    :loading="commentSubmitting"
                    :disabled="!newComment.trim()"
                    @click="submitComment"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="m22 2-7 20-4-9-9-4Z"/>
                      <path d="M22 2 11 13"/>
                    </svg>
                    发表复盘
                  </el-button>
                </div>
              </div>
            </div>

            <!-- 评论列表 -->
            <div class="comment-list-wrap">
              <div v-for="comment in sortedComments" :key="comment.id" class="comment-item">
                <div class="comment-main">
                  <div class="comment-body">
                    <!-- 评论头部 -->
                    <div class="comment-meta">
                      <span class="comment-avatar comment-avatar--lg" :class="{ 'has-img': !!comment.avatarUrl }">
                        <span class="comment-avatar-fallback">{{ comment.username?.charAt(0)?.toUpperCase() }}</span>
                        <img v-if="comment.avatarUrl" :src="comment.avatarUrl" alt="" @error="onCommentAvatarError" />
                      </span>
                      <div class="comment-author-info">
                        <div class="comment-author-line">
                          <span class="comment-user">{{ comment.username }}</span>
                          <span v-if="comment.pinned" class="comment-badge pinned-badge">置顶</span>
                          <span v-if="comment.likeCount && comment.likeCount >= 3" class="comment-badge hot-badge">热评</span>
                          <span v-if="canEditComment(comment)" class="comment-badge author-badge">本人</span>
                        </div>
                        <span class="comment-time">{{ formatCommentTime(comment.createTime) }}</span>
                      </div>
                    </div>

                    <!-- 评论正文 -->
                    <p class="comment-text">{{ comment.content }}</p>

                    <!-- 精细操作区 -->
                    <div class="comment-actions">
                      <button
                        type="button"
                        class="action-pill like-pill"
                        :class="{ 'is-active': comment.likedByMe }"
                        @click="toggleCommentLikeItem(comment)"
                      >
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
                        </svg>
                        <span class="action-label">{{ comment.likedByMe ? '已赞' : '赞' }}</span>
                        <span v-if="comment.likeCount" class="action-count">{{ comment.likeCount }}</span>
                      </button>

                      <button
                        type="button"
                        class="action-pill dislike-pill"
                        :class="{ 'is-active': isCommentDisliked(comment.id) }"
                        @click="toggleCommentDislike(comment.id)"
                      >
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3" />
                        </svg>
                        <span class="action-label">{{ isCommentDisliked(comment.id) ? '已踩' : '踩' }}</span>
                      </button>

                      <button
                        type="button"
                        class="action-pill reply-pill"
                        :class="{ 'is-active': replyingTo === comment.id }"
                        @click="replyTo(comment.id)"
                      >
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                        </svg>
                        <span class="action-label">回复</span>
                        <span v-if="comment.replies && comment.replies.length" class="action-count">{{ comment.replies.length }}</span>
                      </button>

                      <div class="action-sep"></div>

                      <el-button v-if="canEditComment(comment)" link size="small" class="edit-link" @click="startEditComment(comment)">
                        编辑
                      </el-button>
                      <el-button v-if="canEditComment(comment)" link size="small" type="danger" class="delete-link" @click="deleteCommentItem(comment.id)">
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>

                <!-- 二级回复嵌套树排版 (Nested Reply Tree) -->
                <div v-if="comment.replies && comment.replies.length > 0" class="comment-replies-tree">
                  <div class="reply-tree-spine" aria-hidden="true"></div>
                  <div class="reply-tree-nodes">
                    <div v-for="reply in comment.replies" :key="reply.id" class="comment-reply-node">
                      <div class="reply-node-curve" aria-hidden="true"></div>
                      <div class="reply-node-body">
                        <div class="comment-meta">
                          <span class="comment-avatar comment-avatar--sm" :class="{ 'has-img': !!reply.avatarUrl }">
                            <span class="comment-avatar-fallback">{{ reply.username?.charAt(0)?.toUpperCase() }}</span>
                            <img v-if="reply.avatarUrl" :src="reply.avatarUrl" alt="" @error="onCommentAvatarError" />
                          </span>
                          <div class="comment-author-info">
                            <div class="comment-author-line">
                              <span class="comment-user">{{ reply.username }}</span>
                              <span class="reply-target-text">回复 @{{ comment.username }}</span>
                              <span v-if="canEditComment(reply)" class="comment-badge author-badge">本人</span>
                            </div>
                            <span class="comment-time">{{ formatCommentTime(reply.createTime) }}</span>
                          </div>
                        </div>

                        <p class="comment-text reply-text">{{ reply.content }}</p>

                        <div class="comment-actions reply-actions-bar">
                          <button
                            type="button"
                            class="action-pill like-pill is-mini"
                            :class="{ 'is-active': reply.likedByMe }"
                            @click="toggleCommentLikeItem(reply)"
                          >
                            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
                            </svg>
                            <span class="action-label">{{ reply.likedByMe ? '已赞' : '赞' }}</span>
                            <span v-if="reply.likeCount" class="action-count">{{ reply.likeCount }}</span>
                          </button>

                          <button
                            type="button"
                            class="action-pill dislike-pill is-mini"
                            :class="{ 'is-active': isCommentDisliked(reply.id) }"
                            @click="toggleCommentDislike(reply.id)"
                          >
                            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3" />
                            </svg>
                          </button>

                          <div class="action-sep"></div>

                          <el-button v-if="canEditComment(reply)" link size="small" class="edit-link" @click="startEditComment(reply)">
                            编辑
                          </el-button>
                          <el-button v-if="canEditComment(reply)" link size="small" type="danger" class="delete-link" @click="deleteCommentItem(reply.id)">
                            删除
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 回复输入框 (平滑展开) -->
                <div v-if="replyingTo === comment.id" class="reply-input-box">
                  <div class="reply-input-header">
                    <span>回复 <strong>@{{ comment.username }}</strong> 的复盘:</span>
                    <button type="button" class="reply-cancel-link" @click="replyingTo = null">取消回复</button>
                  </div>
                  <el-input
                    v-model="replyContent"
                    type="textarea"
                    :rows="3"
                    :placeholder="`理性回复 @${comment.username} ...`"
                    maxlength="1000"
                    show-word-limit
                    size="default"
                  />
                  <div class="reply-actions">
                    <div ref="replyEmojiBtn" class="emoji-trigger" title="插入表情" @click="emoji.open(replyEmojiBtn!, e => replyContent += e)">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10" />
                        <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                        <line x1="9" y1="9" x2="9.01" y2="9" />
                        <line x1="15" y1="9" x2="15.01" y2="9" />
                      </svg>
                      <span class="emoji-trigger-label">表情</span>
                    </div>
                    <div class="reply-btn-group">
                      <el-button size="small" @click="replyingTo = null">取消</el-button>
                      <el-button type="primary" size="small" :loading="commentSubmitting" :disabled="!replyContent.trim()" @click="submitReply(comment.id)">
                        提交回复
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-if="comments.length === 0" class="comment-empty">
              <div class="comment-empty-visual" aria-hidden="true">
                <span class="comment-empty-orbit"></span>
                <svg viewBox="0 0 64 64" fill="none">
                  <path d="M12 17a8 8 0 0 1 8-8h24a8 8 0 0 1 8 8v18a8 8 0 0 1-8 8H30L18 53v-10a8 8 0 0 1-6-8Z" fill="currentColor" opacity=".22"/>
                  <circle cx="24" cy="27" r="2.5" fill="currentColor"/>
                  <circle cx="32" cy="27" r="2.5" fill="currentColor"/>
                  <circle cx="40" cy="27" r="2.5" fill="currentColor"/>
                </svg>
              </div>
              <strong class="empty-title-text">暂无战术复盘评论</strong>
              <span class="empty-sub-text">本场攻防转换与关键决胜时刻等你来拆解，发表第一条复盘吧！</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
  <el-dialog v-model="commentEditVisible" title="修改战术复盘评论" width="560px" destroy-on-close append-to-body @closed="emoji.close">
    <div class="comment-edit-input">
      <el-input v-model="commentEditContent" type="textarea" :rows="5" maxlength="2000" show-word-limit />
      <button ref="commentEditEmojiBtn" class="emoji-trigger comment-edit-emoji" type="button" title="插入表情" @click="emoji.open(commentEditEmojiBtn!, value => commentEditContent += value)">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M8 14s1.5 2 4 2 4-2 4-2" />
          <line x1="9" y1="9" x2="9.01" y2="9" />
          <line x1="15" y1="9" x2="15.01" y2="9" />
        </svg>
        <span class="emoji-trigger-label">表情</span>
      </button>
    </div>
    <template #footer>
      <el-button @click="commentEditVisible = false">取消</el-button>
      <el-button type="primary" :loading="commentEditSubmitting" :disabled="!commentEditContent.trim()" @click="submitCommentEdit">保存修改</el-button>
    </template>
  </el-dialog>
  <EmojiPicker :visible="emoji.visible.value" :anchor="emoji.anchor.value" @select="emoji.onSelect" @close="emoji.close" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchBoxScore, fetchQuarterScores } from '@/api/matchDetail'
import { fetchMatchRecords } from '@/api/matchRecord'
import { getGameShotChart } from '@/api/shotChart'
import { fetchGameComments, createComment, deleteComment, updateComment, toggleCommentLike } from '@/api/community'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { useEmojiPicker } from '@/composables/useEmojiPicker'
import { useCommentRealtime } from '@/composables/useCommentRealtime'
import GameShotChartPanel from '@/components/shot-chart/GameShotChartPanel.vue'
import LiveChat from '@/components/LiveChat.vue'
import GameIntelligenceCenter from '@/components/game-intelligence/GameIntelligenceCenter.vue'
import ReplayAvailabilityPanel from '@/components/game-intelligence/ReplayAvailabilityPanel.vue'
import { useGameReplay } from '@/composables/useGameReplay'
import type { BoxScore, BoxScorePlayer, MatchRecord, QuarterScore, TimelineEvent, MomentumSnapshot, Highlight, TurningPoint, ShotData } from '@/api/types'
import type { Comment } from '@/api/community'
import { getTeamLogo } from '@/utils/teamLogos'
import { assessBoxScoreStats, meaningfulPlayers } from '@/utils/boxScoreStats'
import { buildPeriodScoreRows, type PeriodScoreRow } from '@/utils/periodScores'
import { useAuthStore } from '@/stores/auth'
import { useIdentityStore } from '@/stores/identity'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const identityStore = useIdentityStore()

const matchRecordFallback = ref<MatchRecord | null>(null)
const gameId = computed(() => (route.query.gameId as string) || '')
const status = computed(() => matchRecordFallback.value?.status || (route.query.status as string) || 'UNKNOWN')
const queryHomeTeam = computed(() => (route.query.homeTeam as string) || '')
const queryAwayTeam = computed(() => (route.query.awayTeam as string) || '')
const homeTeamName = computed(() => matchRecordFallback.value?.homeTeam || boxScore.value?.homeTeam || queryHomeTeam.value || '--')
const awayTeamName = computed(() => matchRecordFallback.value?.awayTeam || boxScore.value?.awayTeam || queryAwayTeam.value || '--')

function goBack() {
  router.back()
}

function isTeamNavigable(name: string | null | undefined): boolean {
  if (!name) return false
  const trimmed = name.trim()
  return Boolean(trimmed && trimmed !== '--' && trimmed !== '待定' && trimmed !== 'TBD' && trimmed !== 'tbd')
}

function goToTeamDetail(name: string) {
  if (!isTeamNavigable(name)) return
  router.push({
    path: '/teams/detail',
    query: { name, returnTo: route.fullPath },
  })
}

const boxScore = ref<BoxScore | null>(null)
const activeTab = ref('stats')

// Tab 切换时加载对应数据
watch(activeTab, (tab) => {
  if (tab === 'playbyplay') {
    void loadReplayData()
  }
  if (tab === 'shotchart' && gameId.value && !gameShots.value.length) {
    loadGameShotChart(gameId.value)
  }
  if (tab === 'comments') loadComments()
})

// 评论相关
const comments = ref<Comment[]>([])
const newComment = ref('')
const replyContent = ref('')
const replyingTo = ref<number | null>(null)
const commentSubmitting = ref(false)
const emoji = useEmojiPicker()
const commentEmojiBtn = ref<HTMLElement | null>(null)
const replyEmojiBtn = ref<HTMLElement | null>(null)
const commentEditVisible = ref(false)
const commentEditSubmitting = ref(false)
const commentEditId = ref<number | null>(null)
const commentEditContent = ref('')
const commentEditEmojiBtn = ref<HTMLElement | null>(null)

// 战术复盘与排序相关
const commentSort = ref<'hot' | 'latest'>('hot')
const tacticalTopics = [
  '#关键决胜回合#',
  '#核心球员MVP表现#',
  '#攻防战术拆解#',
  '#末节攻坚细节#',
  '#板凳深度对比#',
  '#关键防守锁死#',
]
const commentInputRef = ref<any>(null)

function insertTacticalTag(tag: string) {
  if (replyingTo.value != null) {
    if (!replyContent.value.includes(tag)) {
      replyContent.value = replyContent.value ? `${tag} ${replyContent.value}` : `${tag} `
    }
  } else {
    if (!newComment.value.includes(tag)) {
      newComment.value = newComment.value ? `${tag} ${newComment.value}` : `${tag} `
    }
    nextTick(() => {
      commentInputRef.value?.focus?.()
    })
  }
}

// 客户端点踩反馈
const dislikedCommentIds = ref<Set<number>>(new Set())

function isCommentDisliked(id: number): boolean {
  return dislikedCommentIds.value.has(id)
}

function toggleCommentDislike(id: number) {
  if (!auth.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (dislikedCommentIds.value.has(id)) {
    dislikedCommentIds.value.delete(id)
  } else {
    dislikedCommentIds.value.add(id)
  }
}

// 按照热度 / 最新排序展示评论
const sortedComments = computed(() => {
  const list = [...comments.value]
  if (commentSort.value === 'hot') {
    return list.sort((a, b) => {
      if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
      const likeDiff = (b.likeCount || 0) - (a.likeCount || 0)
      if (likeDiff !== 0) return likeDiff
      return new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
    })
  } else {
    return list.sort((a, b) => {
      if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
      return new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
    })
  }
})

const currentUserId = computed(() => {
  try {
    if (!auth.token) return null
    return JSON.parse(atob(auth.token.split('.')[1])).userId ?? null
  } catch { return null }
})
const canEditComment = (comment: Comment) =>
  auth.isAdmin ||
  (currentUserId.value != null && currentUserId.value === comment.userId) ||
  (Boolean(auth.username) && auth.username === comment.username)


useCommentRealtime(
  () => gameId.value ? `/topic/comments/game/${gameId.value}` : null,
  () => void loadComments(),
)

// 当 boxScore 加载完成后，如果 quarterScores 为空，则从 boxScore 中获取
watch(boxScore, (newVal) => {
  if (newVal?.quarterScores?.length && !quarterScores.value.length) {
    quarterScores.value = newVal.quarterScores
    quarterFallbackAttempted.value = false
  }
})
const quarterScores = ref<QuarterScore[]>([])
const loading = ref(false)
const boxscoreLoading = ref(false)
const quarterLoading = ref(false)
const quarterFallbackAttempted = ref(false)

const quarterEmptyText = computed(() => {
  if (status.value === 'SCHEDULED') return '比赛尚未开始'
  if (quarterLoading.value) return '暂无每节比分，正在尝试从官方比分源补全'
  if (quarterFallbackAttempted.value) return '该场比赛官方每节比分暂不可用'
  return '暂无每节比分数据'
})

function displayedScore(side: 'homeScore' | 'awayScore'): string {
  if (status.value !== 'LIVE' && status.value !== 'FINISHED') return '--'
  // 状态与比分采用同一份最新比赛记录；网址和不完整球员统计不能充当比赛比分。
  const score = matchRecordFallback.value ? matchRecordFallback.value[side] : boxScore.value?.[side]
  return score != null && Number.isFinite(score) && score >= 0 ? String(score) : '--'
}

const homeTotalScore = computed(() => displayedScore('homeScore'))

const homeTeamLogo = computed(() => {
  const team = homeTeamName.value
  return isTeamNavigable(team) ? getTeamLogo(team) : null
})
const awayTeamLogo = computed(() => {
  const team = awayTeamName.value
  return isTeamNavigable(team) ? getTeamLogo(team) : null
})

const awayTotalScore = computed(() => displayedScore('awayScore'))

const periodScoreRows = computed(() => buildPeriodScoreRows(quarterScores.value, {
  status: status.value,
  homeScore: homeTotalScore.value === '--' ? null : Number(homeTotalScore.value),
  awayScore: awayTotalScore.value === '--' ? null : Number(awayTotalScore.value),
}))

const periodScoreRowClass = ({ row }: { row: PeriodScoreRow }) => row.isTotal ? 'quarter-total-row' : ''

function sortPlayers(players: BoxScorePlayer[]): BoxScorePlayer[] {
  return [...players].sort((a, b) => {
    if (a.starter !== b.starter) return a.starter ? -1 : 1
    return b.points - a.points
  })
}

function boxScoreRowClass({ row, rowIndex }: { row: BoxScorePlayer; rowIndex: number }) {
  if (row.starter) return 'row-starter'
  return rowIndex === 5 ? 'row-bench-first row-bench' : 'row-bench'
}

/** 过滤掉无任何统计的空行，避免整行 0 误导 */
const sortedHomePlayers = computed(() => sortPlayers(meaningfulPlayers(boxScore.value?.homePlayers)))
const sortedAwayPlayers = computed(() => sortPlayers(meaningfulPlayers(boxScore.value?.awayPlayers)))

/** 技术统计是否可用：两队都无有效数据时判定缺失 */
const stats = computed(() => assessBoxScoreStats(boxScore.value))
const statsAvailable = computed(() => stats.value.available)
const boxScoreEmptyText = computed(() =>
  boxscoreLoading.value ? '' : (stats.value.available ? '暂无数据' : '统计暂未提供')
)

// 关键数据对比
const comparisonItems = computed(() => {
  if (!boxScore.value || !statsAvailable.value) return []
  const h = boxScore.value.homePlayers ?? []
  const a = boxScore.value.awayPlayers ?? []
  const sum = (players: any[], field: string) => players.reduce((s: number, p: any) => s + (p[field] ?? 0), 0)
  const shootingPct = (players: any[], madeField: string, attemptedField: string) => {
    const made = sum(players, madeField)
    const attempted = sum(players, attemptedField)
    if (!attempted) return '0.0%'
    return ((made / attempted) * 100).toFixed(1) + '%'
  }
  return [
    { label: '总得分', homeValue: String(sum(h, 'points')), awayValue: String(sum(a, 'points')) },
    { label: '总篮板', homeValue: String(sum(h, 'rebounds')), awayValue: String(sum(a, 'rebounds')) },
    { label: '总助攻', homeValue: String(sum(h, 'assists')), awayValue: String(sum(a, 'assists')) },
    { label: '总抢断', homeValue: String(sum(h, 'steals')), awayValue: String(sum(a, 'steals')) },
    { label: '总盖帽', homeValue: String(sum(h, 'blocks')), awayValue: String(sum(a, 'blocks')) },
    { label: '总失误', homeValue: String(sum(h, 'turnovers')), awayValue: String(sum(a, 'turnovers')) },
    { label: '投篮命中率', homeValue: shootingPct(h, 'fgMade', 'fgAttempted'), awayValue: shootingPct(a, 'fgMade', 'fgAttempted') },
    { label: '三分命中率', homeValue: shootingPct(h, 'threeMade', 'threeAttempted'), awayValue: shootingPct(a, 'threeMade', 'threeAttempted') },
  ]
})

/** 字符串比分与百分比均按数值比较；失误数较少的一方高亮。 */
function comparisonWinner(item: { label: string; homeValue: string; awayValue: string }, side: 'home' | 'away') {
  const home = Number.parseFloat(item.homeValue)
  const away = Number.parseFloat(item.awayValue)
  if (!Number.isFinite(home) || !Number.isFinite(away) || home === away) return false
  const lowerIsBetter = item.label === '总失误'
  return side === 'home' ? (lowerIsBetter ? home < away : home > away) : (lowerIsBetter ? away < home : away > home)
}

function statusType(s: string) {
  const map: Record<string, string> = { SCHEDULED: 'warning', LIVE: 'info', FINISHED: 'info' }
  return map[s] || 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { SCHEDULED: '未开赛', LIVE: '进行中', FINISHED: '已结束', UNKNOWN: '状态待确认' }
  return map[s] || s
}

function formatShooting(made: number, attempted: number, pct: number) {
  if (attempted === 0) return '0/0'
  return `${made}/${attempted} (${(pct * 100).toFixed(1)}%)`
}

function periodLabel(p: number) {
  if (p <= 4) return `第${p}节`
  return `OT${p - 4}`
}

async function loadBoxScore() {
  if (!gameId.value) return
  boxscoreLoading.value = true
  loading.value = true
  try {
    const { data } = await fetchBoxScore(gameId.value)
    boxScore.value = data
    if (data.quarterScores?.length) {
      quarterScores.value = data.quarterScores
      quarterFallbackAttempted.value = false
    }
  } catch {
    ElMessage.error('加载比赛数据失败')
  } finally {
    boxscoreLoading.value = false
    loading.value = false
  }
}

function computeQuarterScoresFromEvents(events: TimelineEvent[]): QuarterScore[] {
  if (!events || events.length === 0) return []
  const periodMap = new Map<number, QuarterScore>()
  for (const event of events) {
    if ((event.homeScore > 0 || event.awayScore > 0) && event.period > 0) {
      periodMap.set(event.period, {
        period: event.period,
        homeScore: event.homeScore,
        awayScore: event.awayScore,
      })
    }
  }
  return Array.from(periodMap.values()).sort((a, b) => a.period - b.period)
}

// AI Replay data
const replay = useGameReplay(gameId, { autoLoad: false, autoPrepare: true })
const replayState = replay.state
const replayError = replay.error
const replayQuality = replay.quality
const replayBusy = replay.isBusy
const replayOperationId = replay.operationId
const replayRetryAfterSeconds = replay.retryAfterSeconds
const timelineEvents = computed<TimelineEvent[]>(() => replay.data.value?.timeline ?? [])
const momentumData = computed<MomentumSnapshot[]>(() => replay.data.value?.momentum ?? [])
const highlightsData = computed<Highlight[]>(() => replay.data.value?.highlights ?? [])
const turningPointsData = computed<TurningPoint[]>(() => replay.data.value?.turningPoints ?? [])
const gameSummary = computed(() => replay.data.value?.cachedSummary ?? null)
const replaySummaryOnly = computed(() => replayQuality.value?.level === 'SUMMARY_ONLY')
const showReplayCenter = computed(() => (
  (replayState.value === 'ready' || replayState.value === 'partial')
  && !replaySummaryOnly.value
  && timelineEvents.value.length > 0
))
const selectedTimelineEvent = ref<TimelineEvent | null>(null)
const replayLoading = computed(() => replayBusy.value && !replay.data.value)

watch(timelineEvents, (events) => {
  if (selectedTimelineEvent.value) {
    const stillVisible = events.some(event => event.id === selectedTimelineEvent.value?.id)
    if (!stillVisible) selectedTimelineEvent.value = null
  }
  if (events.length > 0 && quarterScores.value.length === 0) {
    const derived = computeQuarterScoresFromEvents(events)
    if (derived.length > 0) {
      quarterScores.value = derived
      quarterFallbackAttempted.value = false
    }
  }
})

// 投篮图状态
const shotChartLoading = ref(false)
const gameShots = ref<ShotData[]>([])
const shotChartMode = ref<string>('UNAVAILABLE')
const shotChartSource = ref<string>('')
const shotChartMessage = ref<string>('')

async function loadReplayData() {
  if (!gameId.value) return
  await replay.load()
}

// 投篮图过滤
const homeTeamIdForShot = ref<number | null>(null)
const awayTeamIdForShot = ref<number | null>(null)
const homeTeamNameForShot = ref('')
const awayTeamNameForShot = ref('')

async function loadGameShotChart(gameIdStr: string) {
  shotChartLoading.value = true
  try {
    const { data } = await getGameShotChart(gameIdStr)
    gameShots.value = data.shots ?? []
    shotChartMode.value = data.mode || 'UNAVAILABLE'
    shotChartSource.value = data.source || ''
    shotChartMessage.value = data.message || ''
    homeTeamIdForShot.value = data.homeTeamId ?? null
    awayTeamIdForShot.value = data.awayTeamId ?? null
    homeTeamNameForShot.value = data.homeTeam ?? ''
    awayTeamNameForShot.value = data.awayTeam ?? ''
  } catch {
    gameShots.value = []
    shotChartMode.value = 'UNAVAILABLE'
    shotChartSource.value = ''
    shotChartMessage.value = '数据获取失败'
  } finally {
    shotChartLoading.value = false
  }
}

async function loadQuarterScores() {
  if (!gameId.value) return
  // 如果boxScore已经加载了quarterScores，跳过单独的API调用
  if (quarterScores.value.length > 0) {
    quarterFallbackAttempted.value = false
    return
  }
  quarterLoading.value = true
  try {
    const { data } = await fetchQuarterScores(gameId.value)
    if (data && data.length > 0) {
      quarterScores.value = data
      quarterFallbackAttempted.value = false
    } else {
      quarterFallbackAttempted.value = true
      if (status.value !== 'SCHEDULED') {
        if (timelineEvents.value.length > 0) {
          const derived = computeQuarterScoresFromEvents(timelineEvents.value)
          if (derived.length > 0) {
            quarterScores.value = derived
            quarterFallbackAttempted.value = false
          }
        } else {
          void replay.load()
        }
      }
    }
  } catch {
    quarterFallbackAttempted.value = true
    if (status.value !== 'SCHEDULED') {
      if (timelineEvents.value.length > 0) {
        const derived = computeQuarterScoresFromEvents(timelineEvents.value)
        if (derived.length > 0) {
          quarterScores.value = derived
          quarterFallbackAttempted.value = false
        }
      } else {
        void replay.load()
      }
    }
  } finally {
    quarterLoading.value = false
  }
}

// 评论功能
async function loadComments() {
  if (!gameId.value) return
  try {
    const { data } = await fetchGameComments(gameId.value)
    comments.value = data
  } catch { /* ignore */ }
}

async function submitComment() {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  if (!newComment.value.trim()) return
  commentSubmitting.value = true
  try {
    await createComment({ gameId: gameId.value, content: newComment.value })
    newComment.value = ''
    ElMessage.success('评论成功')
    loadComments()
  } catch { ElMessage.error('评论失败') }
  finally { commentSubmitting.value = false }
}

function replyTo(parentId: number) { replyingTo.value = parentId; replyContent.value = '' }

async function submitReply(parentId: number) {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  if (!replyContent.value.trim()) return
  commentSubmitting.value = true

  try {
    await createComment({ gameId: gameId.value, content: replyContent.value, parentId })
    replyContent.value = ''
    replyingTo.value = null
    ElMessage.success('回复成功')
    loadComments()
  } catch { ElMessage.error('回复失败') }
  finally { commentSubmitting.value = false }
}

async function deleteCommentItem(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' })
    await deleteComment(id)
    ElMessage.success('已删除')
    loadComments()
  } catch { /* cancel */ }
}

function startEditComment(comment: Comment) {
  commentEditId.value = comment.id
  commentEditContent.value = comment.content
  commentEditVisible.value = true
}

async function submitCommentEdit() {
  if (commentEditId.value == null || !commentEditContent.value.trim()) return
  commentEditSubmitting.value = true
  try {
    await updateComment(commentEditId.value, commentEditContent.value)
    commentEditVisible.value = false
    ElMessage.success('评论已更新')
    await loadComments()
  } catch {
    ElMessage.error('编辑评论失败')
  } finally {
    commentEditSubmitting.value = false
  }
}

async function toggleCommentLikeItem(comment: Comment) {
  if (!auth.token) { ElMessage.warning('请先登录'); return }
  try {
    await toggleCommentLike(comment.id)
    loadComments()
  } catch { /* ignore */ }
}

function formatCommentTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN')
}


// 实时身份同步：更新评论用户名/头像
watch(() => identityStore.revision, () => {
  comments.value = comments.value.map(c => applyIdentityToComment(c))
})

function onCommentAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

function applyIdentityToComment(c: Comment): Comment {
  const id = identityStore.getIdentity(c.userId)
  const updated = id ? { ...c, username: id.username, avatarUrl: id.avatarUrl } : c
  if (c.replies?.length) {
    updated.replies = c.replies.map(r => applyIdentityToComment(r))
  }
  return updated
}

async function loadMatchRecordFallback() {
  if (!gameId.value) return
  try {
    const { data } = await fetchMatchRecords()
    if (data && data.length > 0) {
      const target = data.find(m => m.nbaGameId === gameId.value || String(m.id) === gameId.value)
      if (target) {
        matchRecordFallback.value = target
      }
    }
  } catch {
    // ignore
  }
}

onMounted(async () => {
  // 先核对状态，避免从旧网址或技术统计中显示未开赛比赛的比分。
  await Promise.all([
    loadComments(),
    loadMatchRecordFallback(),
  ])
  if (status.value !== 'SCHEDULED') {
    await Promise.all([loadBoxScore(), loadQuarterScores()])
  }
  // 如果默认 tab 是 playbyplay，也加载回放数据
  if (activeTab.value === 'playbyplay' && gameId.value) {
    void loadReplayData()
  }
})
</script>

<style scoped>
.comment-edit-input { position: relative; }
.comment-edit-input :deep(.el-textarea__inner) { padding-bottom: 42px; }
.comment-edit-emoji {
  position: absolute;
  left: 10px;
  bottom: 10px;
  z-index: 1;
  height: 32px !important;
  padding: 0 10px !important;
}

.page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  min-height: calc(100vh - 108px);
  position: relative;
  border-radius: var(--radius-lg);
  animation: pageFadeIn 0.4s var(--ease-smooth) forwards;
  opacity: 0;
  transform: translateY(12px);
}
@keyframes pageFadeIn { to { opacity: 1; transform: translateY(0); } }

/* 返回按钮 */
.back-row {
  margin-bottom: 16px;
}
.back-btn {
  color: var(--text-muted) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 6px 12px !important;
  border-radius: var(--radius-md) !important;
  transition: all var(--duration-fast) var(--ease-smooth) !important;
}
.back-btn:hover {
  color: var(--accent) !important;
  background: var(--accent-lighter) !important;
}
.back-btn svg {
  margin-right: 4px;
}

/* 比赛头部 */
.match-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 18px;
  padding: 28px 32px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  animation: courtside-rise 0.5s var(--ease-out-expo, ease) both;
  box-shadow: var(--shadow-md);
  opacity: 0;
  transform: translateY(16px);
}
@keyframes matchHeroIn { to { opacity: 1; transform: translateY(0); } }
.match-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent-light), var(--accent));
}
.match-hero::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
  opacity: 0.3;
  pointer-events: none;
}
.match-hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
  gap: 20px;
}
.match-hero-team {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}
.match-hero-team.is-clickable {
  cursor: pointer;
  border-radius: var(--radius-xl, 16px);
  padding: 10px 14px;
  margin: -10px -14px;
  border: 1px solid transparent;
  transition:
    background var(--duration-fast) var(--ease-smooth),
    border-color var(--duration-fast) var(--ease-smooth),
    transform var(--duration-fast) cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow var(--duration-fast) var(--ease-smooth);
  user-select: none;
}
.match-hero-team.is-clickable:hover {
  background: rgba(var(--accent-rgb, 34, 211, 238), 0.05);
  border-color: rgba(var(--accent-rgb, 34, 211, 238), 0.18);
  box-shadow: 0 4px 20px -4px rgba(var(--accent-rgb, 34, 211, 238), 0.12);
  transform: translateY(-2px);
}
.match-hero-team.is-clickable:active {
  transform: translateY(0) scale(0.985);
}
.match-hero-team.is-clickable:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.match-hero-team.is-clickable:hover .hero-team-icon {
  border-color: var(--accent);
  box-shadow: 0 0 24px var(--accent-glow), 0 0 10px rgba(var(--accent-rgb, 34, 211, 238), 0.4);
  transform: scale(1.08);
}
.match-hero-team.is-clickable:hover .hero-team-name {
  color: var(--accent);
  text-shadow: 0 0 16px var(--accent-glow);
}
.match-hero-team.is-clickable:hover .hero-team-arrow {
  color: var(--accent);
  opacity: 1;
  transform: translateX(5px);
}
.match-hero-team--away.is-clickable:hover .hero-team-arrow {
  transform: translateX(-5px);
}
.match-hero-team--away {
  justify-content: flex-end;
}
.hero-team-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-lg);
  background: var(--accent-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
  overflow: hidden;
  border: 2px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-smooth);
}
.team-logo-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
.hero-team-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hero-team-info--right {
  text-align: right;
}
.hero-team-name-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.hero-team-name-row--reverse {
  justify-content: flex-end;
}
.hero-team-arrow {
  display: inline-flex;
  align-items: center;
  color: var(--text-dim);
  transition: transform var(--duration-fast) var(--ease-smooth), color var(--duration-fast) var(--ease-smooth);
}
.hero-team-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
  transition: color var(--duration-fast) var(--ease-smooth);
}
.hero-team-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hero-team-sub--right {
  justify-content: flex-end;
}
.hero-team-label {
  font-size: 12px;
  color: var(--text-muted);
}
.match-hero-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.hero-score-display {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hero-score {
  font-size: 48px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--font-heading);
  text-shadow: 0 0 30px var(--accent-glow);
  line-height: 1;
  letter-spacing: -1px;
}
.hero-score-sep {
  font-size: 32px;
  font-weight: 300;
  color: var(--text-dim);
}

/* LIVE 脉冲动画 */
.live-badge {
  --el-tag-bg-color: rgba(var(--live-rgb), 0.13);
  --el-tag-border-color: rgba(var(--live-rgb), 0.42);
  --el-tag-text-color: var(--live);
  animation: livePulse 1.5s ease-in-out infinite;
}
@keyframes livePulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px rgba(var(--live-rgb), 0.18); }
  50% { opacity: 0.72; box-shadow: 0 0 16px rgba(var(--live-rgb), 0.3); }
}

/* section header */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.section-header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.section-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.3px;
}
.team-table-title {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.2px;
}

/* 逐节比分卡片 */
.quarter-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}
.quarter-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--warning), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.period-label {
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  letter-spacing: 0.2px;
}
.quarter-score {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.quarter-score--win {
  color: var(--accent);
  text-shadow: 0 0 12px var(--accent-glow);
}
.quarter-card :deep(.quarter-total-row td) {
  border-top: 2px solid var(--border-light);
}
.quarter-card :deep(.quarter-total-row .period-label) {
  color: var(--text-primary);
  font-weight: 700;
}

/* Tabs */
.detail-tabs {
  margin-top: 4px;
}
.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--border-light) !important;
}
.detail-tabs :deep(.el-tabs__item) {
  color: var(--text-muted) !important;
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 0.3px;
  transition: color var(--duration-fast) var(--ease-smooth);
}
.detail-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent) !important;
  font-weight: 700;
}
.detail-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--accent) !important;
}

/* Box Score 表格卡片 */
.boxscore-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  overflow: hidden;
  position: relative;
}
.boxscore-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent-light), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.boxscore-card :deep(.el-table__row.row-starter) {
  font-weight: 500;
}
.boxscore-card :deep(.el-table__row.row-bench-first td) {
  border-top: 2px dashed rgba(var(--accent-rgb), 0.3) !important;
}
.shooting-stat {
  font-family: var(--font-mono, monospace);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: var(--text-primary);
}
.player-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.player-name {
  font-weight: 500;
  color: var(--text-secondary);
}
.player-starter {
  color: var(--text-primary);
  font-weight: 700;
}
.starter-tag {
  font-size: 10px !important;
  padding: 0 5px !important;
  height: 18px !important;
  line-height: 16px !important;
  border-radius: 4px !important;
  background: var(--accent-soft) !important;
  color: var(--accent) !important;
  border: 1px solid rgba(var(--accent-rgb), 0.35) !important;
  font-weight: 700 !important;
}
.plus-positive {
  display: inline-block;
  padding: 2px 7px;
  border-radius: var(--radius-sm, 6px);
  background: rgba(16, 185, 129, 0.12);
  color: #10B981;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  border: 1px solid rgba(16, 185, 129, 0.28);
}
.plus-negative {
  display: inline-block;
  padding: 2px 7px;
  border-radius: var(--radius-sm, 6px);
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  border: 1px solid rgba(239, 68, 68, 0.28);
}

/* 节次筛选 */
.period-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

/* Play-by-Play 卡片 */
.pbp-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-xl) !important;
  position: relative;
  overflow: hidden;
}
.pbp-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent-light), var(--accent));
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.pbp-list {
  max-height: 600px;
  overflow-y: auto;
}
.pbp-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  transition: all var(--duration-fast) var(--ease-smooth);
}
.pbp-item:last-child {
  border-bottom: none;
}
.pbp-item:hover {
  background: var(--bg-hover);
  padding-left: 20px;
}
.pbp-clock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 70px;
  flex-shrink: 0;
}
.pbp-period-tag {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.pbp-time {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}
.pbp-desc {
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  letter-spacing: 0.2px;
}
.pbp-score {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.pbp-score-home,
.pbp-score-away {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}
.pbp-score-sep {
  color: var(--text-dim);
  font-weight: 300;
}

/* 战术复盘与赛后热评区 */
.comment-section {
  padding: 12px 0 24px;
}

.comment-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.comment-section-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment-title-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-section-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 800;
  color: var(--accent, #0891B2);
  background: rgba(8, 145, 178, 0.12);
  border: 1px solid rgba(8, 145, 178, 0.25);
  border-radius: 6px;
  padding: 2px 8px;
  letter-spacing: 0.3px;
}

.comment-section-title {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: -0.01em;
}

.comment-section-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* 排序切换 */
.comment-sort-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg-hover);
  padding: 3px;
  border-radius: 9px;
  border: 1px solid var(--border-light);
}

.sort-tab-btn {
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.14s ease;
}

.sort-tab-btn:hover {
  color: var(--text-primary);
}

.sort-tab-btn.active {
  background: var(--bg-card);
  color: var(--accent, #0891B2);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

/* 战术话题快捷插入标签 */
.tactical-tags-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 6px 12px;
  background: rgba(8, 145, 178, 0.04);
  border: 1px dashed rgba(8, 145, 178, 0.2);
  border-radius: 10px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tactical-tags-strip::-webkit-scrollbar {
  display: none;
}

.tactical-tags-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--accent, #0891B2);
  white-space: nowrap;
}

.tactical-tags-list {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tactical-tag-pill {
  padding: 3px 9px;
  border-radius: 9999px;
  background: var(--bg-card);
  border: 1px solid rgba(8, 145, 178, 0.22);
  color: var(--accent, #0891B2);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.12s ease;
  flex-shrink: 0;
}

.tactical-tag-pill:hover {
  background: var(--accent, #0891B2);
  color: #FFFFFF;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(8, 145, 178, 0.28);
}

/* 发表评论卡片 */
.comment-composer {
  background: var(--bg-card);
  border: 1px solid var(--border-medium, rgba(148, 163, 184, 0.2));
  border-radius: 16px;
  padding: 16px 18px;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: border-color 0.14s ease, box-shadow 0.14s ease;
}

.comment-composer:focus-within {
  border-color: var(--accent, #0891B2);
  box-shadow: 0 0 0 2px var(--accent-lighter), 0 6px 24px rgba(0, 0, 0, 0.14);
}

.composer-user-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.composer-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-lighter);
  border: 1px solid var(--border-medium);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.composer-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.composer-avatar-text {
  font-size: 14px;
  font-weight: 800;
  color: var(--accent);
}

.composer-user-text {
  display: flex;
  flex-direction: column;
}

.composer-username {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.composer-tip {
  font-size: 11px;
  color: var(--text-dim);
}

.comment-input {
  margin-bottom: 12px;
}

.comment-input :deep(.el-textarea__inner) {
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.65;
  padding: 12px;
}

.comment-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.comment-community-note {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-dim);
  font-size: 12px;
}

.comment-community-note svg {
  width: 14px;
  height: 14px;
  color: var(--accent, #0891B2);
  flex-shrink: 0;
}

.composer-btn-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  white-space: nowrap;
}

.comment-composer .emoji-trigger,
.reply-actions .emoji-trigger,
.emoji-trigger {
  display: inline-flex !important;
  flex-direction: row !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 6px;
  width: auto !important;
  min-width: fit-content !important;
  height: 36px;
  padding: 0 12px !important;
  border-radius: 8px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.12s ease;
  user-select: none;
  white-space: nowrap !important;
  flex: 0 0 auto !important;
  box-sizing: border-box;
}

.comment-composer .emoji-trigger:hover,
.reply-actions .emoji-trigger:hover,
.emoji-trigger:hover {
  background: var(--accent-lighter);
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-1px);
}

.emoji-trigger svg {
  width: 15px !important;
  height: 15px !important;
  flex-shrink: 0;
}

.emoji-trigger-icon {
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
}

.emoji-trigger-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap !important;
  line-height: 1;
  display: inline-block;
  flex-shrink: 0;
}

.emoji-trigger:hover .emoji-trigger-label {
  color: var(--accent);
}

.comment-submit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 18px;
  font-weight: 700;
  border-radius: 8px;
}

/* 评论列表 */
.comment-list-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  transition: border-color 0.14s ease, box-shadow 0.14s ease;
}

.comment-item:hover {
  border-color: rgba(8, 145, 178, 0.35);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.comment-author-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.comment-user {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comment-badge {
  font-size: 10px;
  font-weight: 800;
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.3px;
}

.pinned-badge {
  background: rgba(245, 158, 11, 0.14);
  color: #F59E0B;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.hot-badge {
  background: rgba(239, 68, 68, 0.14);
  color: #EF4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.author-badge {
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891B2);
  border: 1px solid rgba(8, 145, 178, 0.25);
}

.comment-time {
  font-size: 11px;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
}

.comment-avatar {
  position: relative;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-medium);
  background: var(--accent-lighter);
}

.comment-avatar--lg {
  width: 42px;
  height: 42px;
}

.comment-avatar--sm {
  width: 30px;
  height: 30px;
}

.comment-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--accent);
  font-weight: 800;
}

.comment-avatar--lg .comment-avatar-fallback {
  font-size: 16px;
}

.comment-avatar--sm .comment-avatar-fallback {
  font-size: 12px;
}

.comment-avatar img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 1;
}

.comment-avatar.has-img .comment-avatar-fallback {
  display: none;
}

.comment-avatar img[style*="display: none"] ~ .comment-avatar-fallback {
  display: flex;
}

.comment-text {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 精细操作区与点赞/点踩动效 */
.comment-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.action-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 11px;
  border-radius: 7px;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.12s ease;
}

.action-pill.is-mini {
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
}

.action-pill:hover {
  background: var(--bg-card);
  border-color: var(--border-medium);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.like-pill.is-active {
  background: rgba(8, 145, 178, 0.15);
  border-color: var(--accent, #0891B2);
  color: var(--accent, #0891B2);
}

.dislike-pill.is-active {
  background: rgba(239, 68, 68, 0.14);
  border-color: #EF4444;
  color: #EF4444;
}

.action-icon {
  font-size: 13px;
}

.action-count {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.action-sep {
  width: 1px;
  height: 14px;
  background: var(--border-light);
  margin: 0 2px;
}

.edit-link,
.delete-link {
  font-size: 12px !important;
  padding: 0 4px !important;
}

/* 二级回复嵌套树排版 (Nested Reply Tree) */
.comment-replies-tree {
  position: relative;
  margin-top: 14px;
  margin-left: 18px;
  padding-left: 20px;
}

.reply-tree-spine {
  position: absolute;
  left: 6px;
  top: 0;
  bottom: 14px;
  width: 2px;
  background: linear-gradient(180deg, var(--accent, #0891B2), var(--border-medium));
  border-radius: 999px;
  opacity: 0.5;
}

.reply-tree-nodes {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.comment-reply-node {
  position: relative;
}

.reply-node-curve {
  position: absolute;
  left: -14px;
  top: 18px;
  width: 12px;
  height: 12px;
  border-left: 2px solid var(--border-medium);
  border-bottom: 2px solid var(--border-medium);
  border-bottom-left-radius: 8px;
  opacity: 0.5;
}

.reply-node-body {
  background: var(--bg-panel, rgba(255, 255, 255, 0.02));
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 12px 14px;
}

.reply-target-text {
  font-size: 11px;
  color: var(--accent, #0891B2);
  font-weight: 700;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reply-text {
  margin: 4px 0 8px;
}

.reply-actions-bar {
  margin-top: 4px;
}

/* 回复输入框 */
.reply-input-box {
  margin-top: 14px;
  margin-left: 20px;
  padding: 14px;
  background: var(--bg-panel, rgba(255, 255, 255, 0.03));
  border: 1px solid var(--accent-lighter, rgba(8, 145, 178, 0.3));
  border-radius: 12px;
  animation: bubbleEntrance 0.2s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.reply-input-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.reply-input-header strong {
  color: var(--accent, #0891B2);
}

.reply-cancel-link {
  border: none;
  background: none;
  color: var(--text-dim);
  font-size: 11px;
  cursor: pointer;
  padding: 0;
}

.reply-cancel-link:hover {
  color: var(--text-primary);
  text-decoration: underline;
}

.reply-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.reply-btn-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 空状态卡片 */
.comment-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: var(--bg-card);
  border: 1px dashed var(--border-medium);
  border-radius: 16px;
  gap: 8px;
}

.comment-empty-visual {
  position: relative;
  width: 54px;
  height: 54px;
  color: var(--accent, #0891B2);
  margin-bottom: 4px;
}

.empty-title-text {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-primary);
}

.empty-sub-text {
  font-size: 13px;
  color: var(--text-dim);
  max-width: 360px;
}

/* 关键数据对比 */
.key-comparison {
  margin-bottom: 20px;
}
/* 统计缺失提示：替代全零网格 */
.stats-unavailable {
  margin: 0;
  padding: 28px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
  background: var(--bg-card);
  border: 1px dashed var(--border-medium);
  border-radius: 10px;
}
.comparison-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.comparison-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.comp-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  min-width: 40px;
  text-align: center;
}
.comp-value.win {
  color: var(--accent);
}
.comp-label {
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
  white-space: nowrap;
}
@media (max-width: 768px) {
  .comparison-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .comment-replies-tree {
    margin-left: 6px;
    padding-left: 10px;
  }
  .reply-tree-spine {
    left: 2px;
  }
  .reply-node-curve {
    left: -8px;
    width: 6px;
  }
  .reply-input-box {
    margin-left: 6px;
    padding: 10px;
  }
  .comment-author-line {
    gap: 4px;
  }
  .comment-user {
    max-width: 140px;
  }
}

/* Play-by-Play */
.pbp-container {
  max-height: 400px;
  overflow-y: auto;
  padding: 12px 0;
}
.pbp-event {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light);
  font-size: 13px;
}
.pbp-period {
  font-weight: 700;
  color: var(--accent);
  width: 30px;
  text-align: center;
}
.pbp-time {
  color: var(--text-muted);
  width: 50px;
  font-variant-numeric: tabular-nums;
}
.pbp-desc {
  flex: 1;
  color: var(--text-primary);
}
.empty-inline {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
  font-size: 14px;
}

.summary-only-panel {
  margin-top: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(34, 197, 94, 0.08), rgba(59, 130, 246, 0.04)),
    rgba(14, 16, 19, 0.72);
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.28);
  padding: 20px;
}

.summary-only-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.summary-only-head h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 700;
}

.summary-only-head p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.summary-only-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 10px;
}

.summary-only-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(14, 16, 19, 0.52);
  padding: 12px 14px;
}

.summary-only-item span {
  color: var(--text-muted);
  font-size: 13px;
}

.summary-only-item strong {
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  font-size: 15px;
}

.loading-state {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background: rgba(14, 16, 19, 0.72);
  color: var(--text-secondary);
  padding: 60px;
  text-align: center;
}

/* 投篮图 */
.shotchart-panel { display: flex; flex-direction: column; align-items: center; gap: 16px; }
.shotchart-toolbar { display: flex; gap: 12px; flex-wrap: wrap; justify-content: center; }
.shotchart-summary { display: flex; gap: 8px; align-items: center; font-size: 14px; color: var(--text-secondary); }
.summary-sep { color: var(--border-medium); }
.shotchart-source { font-size: 12px; color: var(--text-muted); text-align: center; }
.shotchart-loading-msg { text-align: center; padding: 60px 0; color: var(--text-secondary); font-size: 14px; }
.shotchart-empty-msg { text-align: center; padding: 60px 24px; color: var(--text-muted); }
.empty-title { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.empty-sub { font-size: 13px; color: var(--text-dim); }
</style>
