<template>
  <div class="copilot-workspace" :class="{ 'has-conversation': msgs.length > 0 }">
    <!-- 侧边栏：历史会话 -->
    <aside
      id="copilot-history"
      class="sidebar"
      :class="{ open: sidebarOpen }"
      :inert="!sidebarOpen"
      :aria-hidden="!sidebarOpen"
      aria-label="历史对话"
    >
      <div class="sidebar-brand">
        <div class="sidebar-brand-badge">
          <span class="badge-dot"></span>
          <span>NBA ANALYTICS AI</span>
        </div>
        <div class="sidebar-brand-header">
          <div class="brand-logo-icon">
            <svg viewBox="0 0 24 24" fill="none" class="brand-svg">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8" />
              <path d="M12 2v20M2 12h20" stroke="currentColor" stroke-width="1.8" />
              <path d="M4.5 5.5c4 2.5 5.5 7 5.5 13M19.5 5.5c-4 2.5-5.5 7-5.5 13M4.5 18.5c4-2.5 5.5-7 5.5-13M19.5 18.5c-4-2.5-5.5-7-5.5-13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </div>
          <div class="brand-title-wrap">
            <strong class="sidebar-brand-heading">NBA Copilot</strong>
            <span class="sidebar-brand-subtitle">高阶数据 · 战术复盘 · 赛事推演</span>
          </div>
        </div>
        <button class="sidebar-close" type="button" aria-label="关闭历史对话" @click="sidebarOpen = false">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18" aria-hidden="true">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <!-- 新建对话按钮 -->
      <div class="sidebar-actions">
        <button class="sidebar-new" type="button" @click="newChat">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
          </svg>
          <span>新建对话</span>
        </button>
      </div>

      <!-- 历史搜索栏 (当有历史记录时常驻显示) -->
      <div v-if="convs.length > 0" class="sidebar-search">
        <svg viewBox="0 0 24 24" fill="none" width="14" height="14" class="search-icon" aria-hidden="true">
          <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.8" />
          <path d="m21 21-4.35-4.35" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        <input
          v-model="searchConvText"
          type="text"
          placeholder="搜索历史对话..."
          class="sidebar-search-input"
          aria-label="搜索历史对话"
        />
        <button
          v-if="searchConvText"
          type="button"
          class="sidebar-search-clear"
          aria-label="清空搜索"
          @click="searchConvText = ''"
        >×</button>
      </div>

      <div class="sidebar-section-title">
        <div class="title-with-icon">
          <svg viewBox="0 0 24 24" fill="none" width="15" height="15" aria-hidden="true">
            <path d="M12 8v4l3 3m6-3a9 9 0 1 1-18 0 9 9 0 0 1 18 0z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>历史会话</span>
        </div>
        <span class="conv-count-badge" v-if="convs.length">{{ convs.length }}</span>
      </div>

      <!-- 对话列表 -->
      <div class="sidebar-body">
        <div v-if="!convs.length" class="sidebar-empty">
          <div class="empty-icon-wrap">
            <svg viewBox="0 0 48 48" fill="none" width="36" height="36" aria-hidden="true">
              <path d="M8 10h32v22H22l-8 7v-7H8V10Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
              <path d="M16 18h16M16 24h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </div>
          <strong>暂无历史对话</strong>
          <span>开始新提问，调取 NBA 高阶数据与战术知识库。</span>
        </div>

        <div v-else-if="filteredGroupedConvs.length === 0" class="sidebar-empty">
          <strong>未找到匹配对话</strong>
          <span>尝试搜索其他关键词</span>
        </div>

        <section v-for="group in filteredGroupedConvs" :key="group.label" class="sidebar-group">
          <div class="group-header">
            <h3>{{ group.label }}</h3>
            <span class="group-badge">{{ group.items.length }}</span>
          </div>
          <div
            v-for="c in group.items"
            :key="c.id"
            class="sidebar-item"
            :class="{ active: c.id === sid }"
          >
            <button
              class="sidebar-item-main"
              type="button"
              :aria-current="c.id === sid ? 'page' : undefined"
              :title="c.title"
              @click="loadConv(c)"
            >
              <div class="sidebar-item-header">
                <svg viewBox="0 0 24 24" fill="none" width="13" height="13" class="item-chat-icon" aria-hidden="true">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                </svg>
                <span class="sidebar-item-title">{{ c.title }}</span>
              </div>
              <span class="sidebar-item-time">{{ fmtTime(c.time) }}</span>
            </button>

            <!-- 删除确认交互胶囊 -->
            <div v-if="convToDelete === c.id" class="del-confirm-pill" @click.stop>
              <span class="del-confirm-text">确定删除？</span>
              <button
                class="del-confirm-btn confirm"
                type="button"
                title="确认删除此会话"
                aria-label="确认删除"
                @click.stop="confirmDelConv(c.id)"
              >
                <svg viewBox="0 0 24 24" fill="none" width="12" height="12" stroke="currentColor" stroke-width="2.5">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </button>
              <button
                class="del-confirm-btn cancel"
                type="button"
                title="取消删除"
                aria-label="取消删除"
                @click.stop="cancelDelConv"
              >
                <svg viewBox="0 0 24 24" fill="none" width="12" height="12" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            <!-- 常规删除按钮 -->
            <button
              v-else
              class="sidebar-item-del"
              type="button"
              :aria-label="`删除对话：${c.title}`"
              title="删除此会话"
              @click.stop="requestDelConv(c.id)"
            >
              <svg viewBox="0 0 24 24" fill="none" width="13" height="13" aria-hidden="true">
                <path d="M19 7l-.867 12.142A2 2 0 0 1 16.138 21H7.862a2 2 0 0 1-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v3M4 7h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
        </section>
      </div>

      <div class="sidebar-foot" aria-hidden="true">
        <div class="foot-tech-info">
          <span class="foot-kicker">COURTOPTIX × AI</span>
          <span class="foot-version">ENGINE v4.2 PRO</span>
        </div>
        <div class="foot-line"></div>
      </div>
    </aside>

    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

    <!-- 分享模态框 -->
    <div v-if="showShareModal" class="modal-mask" @click="showShareModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <div class="modal-icon-badge">
            <svg viewBox="0 0 24 24" fill="none" width="20" height="20" stroke="currentColor" stroke-width="2">
              <circle cx="18" cy="5" r="3" />
              <circle cx="6" cy="12" r="3" />
              <circle cx="18" cy="19" r="3" />
              <path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" stroke-linecap="round" />
            </svg>
          </div>
          <div class="modal-title-wrap">
            <h3>分享当前对话</h3>
            <span class="modal-sub">生成公开发布链接，供同行或球迷查阅本次深度分析</span>
          </div>
          <button class="modal-header-close" type="button" aria-label="关闭分享窗口" @click="showShareModal = false">
            <svg viewBox="0 0 24 24" fill="none" width="16" height="16" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <p class="modal-desc">已将本次对话与关联数据生成只读快照链接，任何人均可通过此链接查看。</p>
        <div class="modal-share-row">
          <input :value="shareUrl" readonly aria-label="分享链接" @focus="selectShareInput" />
          <button type="button" class="btn-copy" :class="{ copied: shareCopied }" @click="copyShareUrl">
            <template v-if="!shareCopied">
              <svg viewBox="0 0 24 24" fill="none" width="14" height="14" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
              </svg>
              <span>复制链接</span>
            </template>
            <template v-else>
              <svg viewBox="0 0 24 24" fill="none" width="14" height="14" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              <span>已复制!</span>
            </template>
          </button>
        </div>
        <div class="modal-actions">
          <button class="modal-close-btn" type="button" @click="showShareModal = false">完成</button>
        </div>
      </div>
    </div>

    <!-- 工作区主体 -->
    <div class="workspace-main">
      <!-- 实时比分跑马灯 -->
      <div v-if="showLocalScoreStatus" class="score-status" role="status" aria-live="polite">
        <div class="score-status__label">
          <span class="score-status__dot" :class="{ 'is-live': liveMatches.length > 0 }">
            <span class="pulse-ring" v-if="liveMatches.length > 0"></span>
          </span>
          <span class="score-status__text">实时赛况</span>
        </div>
        <div class="score-status__track">
          <span v-if="!liveMatches.length" class="score-status__empty">{{ scoreStatusText }}</span>
          <span v-for="match in liveMatches" :key="match.id" class="score-status__game">
            <span class="team-name">{{ match.awayTeam }}</span>
            <strong class="team-score">{{ match.awayScore ?? '—' }}</strong>
            <span class="score-sep">:</span>
            <strong class="team-score">{{ match.homeScore ?? '—' }}</strong>
            <span class="team-name">{{ match.homeTeam }}</span>
          </span>
        </div>
        <span class="score-status__tagline" aria-hidden="true">BASKETBALL LIVES IN DATA</span>
      </div>

      <!-- 顶栏导航 -->
      <header class="top-bar">
        <div class="top-left">
          <button
            class="icon-btn"
            type="button"
            :aria-label="sidebarOpen ? '收起历史对话' : '打开历史对话'"
            :aria-expanded="sidebarOpen"
            aria-controls="copilot-history"
            @click="sidebarOpen = !sidebarOpen"
          >
            <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
              <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
          <span class="top-divider" aria-hidden="true" />
          <div class="top-title-group">
            <span class="top-status">AI 助手</span>
            <span class="top-context" aria-hidden="true">NBA COPILOT · PRO</span>
          </div>
          <div class="top-status-indicator">
            <span class="status-live-dot"></span>
            <span class="status-live-text">ONLINE</span>
          </div>
          <button v-if="!sidebarOpen" class="top-btn top-new" type="button" @click="newChat">
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            <span>新对话</span>
          </button>
        </div>
        <div class="top-right">
          <span v-if="docCount > 0" class="top-doc-badge" :title="`知识库收录 ${docCount.toLocaleString()} 篇专业文档与赛事实录`">
            <svg viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            </svg>
            <span>已索引 {{ docCount.toLocaleString() }} 篇</span>
          </span>
          <span v-if="!msgs.length" class="top-action-hint">开始提问后可导出或分享</span>
          <button
            class="top-btn"
            type="button"
            aria-label="导出对话"
            :disabled="!msgs.length"
            :title="msgs.length ? '导出当前对话为 Markdown' : '开始对话后可导出'"
            @click="exportChat"
          >
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span>导出</span>
          </button>
          <button
            class="top-btn top-share-btn"
            type="button"
            aria-label="分享对话"
            :disabled="!msgs.length"
            :title="msgs.length ? '生成链接分享当前分析' : '开始对话后可分享'"
            @click="shareChat"
          >
            <svg viewBox="0 0 24 24" fill="none" width="14" height="14">
              <circle cx="18" cy="5" r="3" stroke="currentColor" stroke-width="2" />
              <circle cx="6" cy="12" r="3" stroke="currentColor" stroke-width="2" />
              <circle cx="18" cy="19" r="3" stroke="currentColor" stroke-width="2" />
              <path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            <span>分享</span>
          </button>
        </div>
      </header>

      <!-- 对话主视图 / 欢迎屏 -->
      <div class="content-area" ref="msgsEl" @scroll.passive="handleContentScroll">
        <!-- 欢迎屏 -->
        <div v-if="!msgs.length" class="welcome-screen">
          <section class="welcome-hero">
            <div class="hero-copy">
              <div class="hero-badges-row">
                <span class="hero-badge">
                  <span class="spark-dot"></span>
                  NBA ANALYTICS INTELLIGENCE
                </span>
                <span class="hero-micro-tag">RAG HYBRID RETRIEVAL</span>
              </div>
              <h1 class="hero-title">
                把比赛读得<span class="title-highlight-wrap"><span class="title-highlight">更深</span><span class="title-highlight-bar" aria-hidden="true"></span></span>
              </h1>
              <p class="hero-desc">
                结合联盟实时比赛流、高阶攻防效率值与战术模型，回答每一个深度篮球问题。
              </p>
              <div class="hero-stats-row">
                <div class="hero-stat-item">
                  <span class="stat-dot"></span>
                  <span>实时赛事实录</span>
                </div>
                <div class="hero-stat-sep">/</div>
                <div class="hero-stat-item">
                  <span class="stat-dot"></span>
                  <span>30 支球队战术剖析</span>
                </div>
                <div class="hero-stat-sep">/</div>
                <div class="hero-stat-item">
                  <span class="stat-dot"></span>
                  <span>现役球员多维高阶数据</span>
                </div>
              </div>
            </div>

            <!-- 战术球场背景图腾 -->
            <div class="hero-graphic" aria-hidden="true">
              <svg class="hero-court" viewBox="0 0 760 300" fill="none">
                <!-- 球场半场边线 -->
                <rect x="20" y="20" width="720" height="260" rx="10" stroke="currentColor" stroke-width="1.5" stroke-opacity="0.25" />
                <line x1="380" y1="20" x2="380" y2="280" stroke="currentColor" stroke-width="1.5" stroke-opacity="0.3" />
                <!-- 中圈 -->
                <circle cx="380" cy="150" r="54" stroke="currentColor" stroke-width="1.5" stroke-opacity="0.35" />
                <circle cx="380" cy="150" r="18" stroke="currentColor" stroke-width="1.2" stroke-opacity="0.2" />
                <!-- 右侧三分线与限制区 -->
                <path d="M740 50h-70c-60 0-110 45-110 100s50 100 110 100h70" stroke="currentColor" stroke-width="1.6" stroke-opacity="0.4" />
                <!-- 禁区 -->
                <rect x="580" y="80" width="160" height="140" stroke="currentColor" stroke-width="1.5" stroke-opacity="0.35" />
                <circle cx="580" cy="150" r="40" stroke="currentColor" stroke-width="1.4" stroke-dasharray="5 5" stroke-opacity="0.35" />
                <circle cx="710" cy="150" r="12" stroke="currentColor" stroke-width="1.8" stroke-opacity="0.6" />
                <line x1="722" y1="135" x2="722" y2="165" stroke="currentColor" stroke-width="2.5" stroke-opacity="0.8" />
                <!-- 战术路线 (挡拆、顺下、底角空位) -->
                <path d="M510 90 Q 560 110 630 140" stroke="var(--copilot-orange)" stroke-width="2" stroke-dasharray="6 4" stroke-opacity="0.8" class="tactical-arrow-1" />
                <polygon points="633,142 622,136 626,145" fill="var(--copilot-orange)" />
                <path d="M460 210 Q 550 230 670 235" stroke="var(--copilot-ice)" stroke-width="1.8" stroke-dasharray="5 4" stroke-opacity="0.75" class="tactical-arrow-2" />
                <polygon points="674,235 664,230 665,240" fill="var(--copilot-ice)" />
                <!-- 掩护标记 T -->
                <path d="M525 80v15M518 80h14" stroke="var(--copilot-orange)" stroke-width="2" stroke-linecap="round" />
              </svg>
              <!-- 质感立体篮球 -->
              <div class="hero-ball-glow">
                <svg class="hero-ball" viewBox="0 0 200 200" fill="none">
                  <defs>
                    <radialGradient id="copilotBallGrad" cx="35%" cy="35%" r="65%">
                      <stop offset="0%" stop-color="#FF8A50" />
                      <stop offset="50%" stop-color="#E65100" />
                      <stop offset="100%" stop-color="#881B03" />
                    </radialGradient>
                  </defs>
                  <circle cx="100" cy="100" r="90" fill="url(#copilotBallGrad)" />
                  <circle cx="100" cy="100" r="90" stroke="rgba(255, 255, 255, 0.28)" stroke-width="1.5" />
                  <path d="M100 10v180M10 100h180" stroke="#1E120D" stroke-width="5" stroke-opacity="0.85" />
                  <path d="M30 40c68 22 94 72 68 148M170 40c-68 22-94 72-68 148" stroke="#1E120D" stroke-width="5" stroke-opacity="0.85" />
                </svg>
              </div>
            </div>
          </section>

          <!-- 核心能力区 -->
          <section class="capabilities" aria-label="AI 能力入口">
            <!-- 特色焦点卡片 -->
            <button class="cap-main" type="button" :disabled="loading" @click="send(featuredPrompt)">
              <div class="cap-main-icon-wrap">
                <div class="cap-main-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="26" height="26" aria-hidden="true">
                    <rect x="3" y="4" width="18" height="18" rx="3" />
                    <path d="M16 2v4M8 2v4M3 10h18" stroke-linecap="round" />
                    <path d="m9 16 2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </div>
              </div>
              <div class="cap-main-text">
                <div class="cap-main-kicker-row">
                  <span class="cap-main-kicker">FEATURED ANALYSIS</span>
                  <span class="kicker-pill">今日焦点</span>
                </div>
                <strong class="cap-main-title">{{ featuredTitle }}</strong>
                <span class="cap-main-desc">{{ featuredDescription }}</span>
              </div>
              <div class="cap-main-cta">
                <span class="cap-main-cta-text">立即分析</span>
                <span class="cap-main-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
                    <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </span>
              </div>
            </button>

            <!-- 四大能力矩阵 -->
            <div class="cap-grid">
              <button class="cap-card" type="button" :disabled="loading" @click="send('对比勒布朗·詹姆斯和凯文·杜兰特本赛季的数据表现与高阶影响力')">
                <div class="cap-card-top">
                  <span class="cap-icon people">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                      <circle cx="9" cy="7" r="4" />
                      <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
                    </svg>
                  </span>
                  <span class="cap-card-tag">高阶对比</span>
                  <span class="cap-card-arrow" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                      <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                </div>
                <div class="cap-copy">
                  <span class="cap-title">球员对比分析</span>
                  <span class="cap-description">多维高阶数据对照，对比球员攻防影响力、真实命中率与关键球表现。</span>
                </div>
              </button>

              <button class="cap-card" type="button" :disabled="loading" @click="send('深度剖析波士顿凯尔特人本赛季战术体系与攻防效率净值')">
                <div class="cap-card-top">
                  <span class="cap-icon globe">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                      <circle cx="12" cy="12" r="10" />
                      <path d="M12 2a14.5 14.5 0 0 0 0 20M12 2a14.5 14.5 0 0 1 0 20M2 12h20" />
                    </svg>
                  </span>
                  <span class="cap-card-tag">体系复盘</span>
                  <span class="cap-card-arrow" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                      <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                </div>
                <div class="cap-copy">
                  <span class="cap-title">球队战术剖析</span>
                  <span class="cap-description">深度调取球队攻防效率净值、近期走势、半场阵地与轮换深度。</span>
                </div>
              </button>

              <button class="cap-card" type="button" :disabled="loading" @click="send('基于当前赛季已有数据，推演季后赛分区对阵胜率与可能战术转折')">
                <div class="cap-card-top">
                  <span class="cap-icon trophy">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                      <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6M18 9h1.5a2.5 2.5 0 0 0 0-5H18M4 22h16" />
                      <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22M18 2H6v7a6 6 0 0 0 12 0V2Z" />
                    </svg>
                  </span>
                  <span class="cap-card-tag">胜率模拟</span>
                  <span class="cap-card-arrow" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                      <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                </div>
                <div class="cap-copy">
                  <span class="cap-title">季后赛推演</span>
                  <span class="cap-description">基于全赛季数据与对阵关系，推演系列赛关键博弈点与胜负手。</span>
                </div>
              </button>

              <button class="cap-card" type="button" :disabled="loading" @click="send('本赛季真实命中率（TS%）与高阶正负值（BPM）最高的领袖球员有哪些？')">
                <div class="cap-card-top">
                  <span class="cap-icon chart">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                      <path d="M4 19V5M9 19v-8M14 19V8M19 19V3" stroke-linecap="round" />
                    </svg>
                  </span>
                  <span class="cap-card-tag">数据榜单</span>
                  <span class="cap-card-arrow" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                      <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                </div>
                <div class="cap-copy">
                  <span class="cap-title">赛季数据榜</span>
                  <span class="cap-description">全维度洞察得分、篮板、助攻与高阶效率（PER/BPM/WS）排行榜。</span>
                </div>
              </button>
            </div>
          </section>

          <!-- 灵动推荐提问 -->
          <div class="prompt-chips" aria-label="推荐提问">
            <div class="prompt-label-group">
              <svg viewBox="0 0 24 24" fill="none" width="14" height="14" stroke="currentColor" stroke-width="2">
                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
              </svg>
              <span class="prompt-label">灵动推荐提问</span>
            </div>
            <div class="chips-container">
              <button class="chip" type="button" :disabled="loading" @click="send('解析掘金第四节五外战术执行效率')">
                <span class="chip-spark">⚡</span>
                <span>掘金五外战术效率</span>
              </button>
              <button class="chip" type="button" :disabled="loading" @click="send('对比本赛季联盟前五三分射手的接投与运球投命中率')">
                <span class="chip-spark">🎯</span>
                <span>前五三分手接投 vs 运投</span>
              </button>
              <button class="chip" type="button" :disabled="loading" @click="send('深度对比雷霆与森林狼的内线护筐与禁区防守效率')">
                <span class="chip-spark">🛡️</span>
                <span>雷霆 vs 森林狼禁区防守</span>
              </button>
              <button class="chip" type="button" :disabled="loading" @click="send('分析湖人最近 5 场比赛的攻防效率与轮换转折')">
                <span class="chip-spark">📈</span>
                <span>湖人近况战术复盘</span>
              </button>
              <button class="chip" type="button" :disabled="loading" @click="send('库里与东契奇关键时刻（Clutch Time）攻防影响力对比')">
                <span class="chip-spark">🔥</span>
                <span>关键时刻影响力对比</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 对话流列表 -->
        <div v-else class="chat-flow">
          <div v-for="(m, i) in msgs" :key="i" class="msg-row" :class="m.role">
            <!-- 用户消息 -->
            <div v-if="m.role === 'user'" class="user-bubble-container">
              <div class="msg-bubble user">
                <div class="user-msg-text">{{ m.content }}</div>
              </div>
            </div>

            <!-- AI 消息 -->
            <template v-else>
              <div class="msg-avatar-wrap">
                <div class="msg-avatar">
                  <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8" />
                    <path d="M12 2v20M2 12h20" stroke="currentColor" stroke-width="1.8" />
                  </svg>
                </div>
              </div>
              <div class="msg-body">
                <!-- 头部元数据栏 -->
                <div class="ai-msg-header">
                  <div class="ai-title-wrap">
                    <strong class="ai-name">NBA Copilot</strong>
                    <span class="ai-model-tag">RAG v4.2</span>
                  </div>
                  <div class="ai-actions">
                    <!-- 生成中悬浮中止按钮 -->
                    <button
                      v-if="loading && i === msgs.length - 1"
                      type="button"
                      class="msg-action-btn stop-generating-btn"
                      title="停止生成当前回答"
                      aria-label="停止生成"
                      @click="cancelQuery"
                    >
                      <svg viewBox="0 0 24 24" fill="currentColor" width="11" height="11">
                        <rect x="5" y="5" width="14" height="14" rx="2" />
                      </svg>
                      <span>停止</span>
                    </button>

                    <span v-if="m.responseTime" class="msg-meta">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                        <circle cx="12" cy="12" r="10" />
                        <path d="M12 6v6l4 2" />
                      </svg>
                      {{ (m.responseTime / 1000).toFixed(1) }}s
                    </span>

                    <button
                      v-if="m.content"
                      type="button"
                      class="msg-action-btn"
                      :title="copiedMsgIndex === i ? '已复制' : '复制回答'"
                      :aria-label="copiedMsgIndex === i ? '已复制' : '复制回答'"
                      @click="copyMessage(m.content, i)"
                    >
                      <svg v-if="copiedMsgIndex !== i" viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                      </svg>
                      <svg v-else viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2.5" class="copy-success-icon">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    </button>
                  </div>
                </div>

                <!-- 生成中加载状态 -->
                <div v-if="loading && i === msgs.length - 1 && !m.content && !m.error" class="msg-loading">
                  <div class="loading-animation-wrap">
                    <div class="loading-dots"><span></span><span></span><span></span></div>
                  </div>
                  <div class="loading-status-wrap">
                    <span class="loading-text">{{ statusText }}</span>
                    <span class="loading-elapsed">已耗时 {{ elapsed }}s</span>
                  </div>
                  <button class="loading-cancel" type="button" @click="cancelQuery">
                    <svg viewBox="0 0 24 24" fill="none" width="12" height="12" stroke="currentColor" stroke-width="2">
                      <rect x="6" y="6" width="12" height="12" rx="2" />
                    </svg>
                    <span>停止生成</span>
                  </button>
                </div>

                <!-- 回答气泡 -->
                <div v-if="m.content" class="msg-bubble ai" v-html="render(m, i)"></div>

                <!-- 关联实体 -->
                <div v-if="m.relatedEntities?.length" class="msg-entities">
                  <div class="entities-header">
                    <svg viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
                      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                    </svg>
                    <span class="entities-label">关联实体分析</span>
                  </div>
                  <div class="entities-list">
                    <RelatedEntityCard v-for="entity in m.relatedEntities" :key="`${entity.type}-${entity.id}`" :entity="entity" />
                  </div>
                </div>

                <!-- 错误提示 -->
                <div v-if="m.error" class="msg-error">
                  <div class="error-icon">
                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16">
                      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
                      <path d="M12 8v4M12 16h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    </svg>
                  </div>
                  <span class="error-text">{{ m.error }}</span>
                  <button class="msg-retry" type="button" @click="retryLast">
                    <svg viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
                      <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
                    </svg>
                    <span>重试提问</span>
                  </button>
                </div>

                <!-- 数据引用来源 -->
                <div v-if="m.sources?.length" class="msg-sources">
                  <button class="sources-toggle" type="button" @click="m.showSources = !m.showSources">
                    <span class="source-badge-icon">
                      <svg viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                      </svg>
                    </span>
                    <span class="sources-title">{{ m.sources.length }} 个数据源引用</span>
                    <svg viewBox="0 0 24 24" fill="none" width="13" height="13" class="toggle-arrow" :class="{ flip: m.showSources }">
                      <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    </svg>
                  </button>
                  <div v-if="m.showSources" class="sources-list">
                    <div v-for="(s, si) in m.sources" :key="si" class="source-item">
                      <span class="source-num">{{ si + 1 }}</span>
                      <span class="source-text">{{ s }}</span>
                    </div>
                  </div>
                </div>

                <!-- 深入追问推荐 -->
                <div v-if="m.followUps?.length && i === msgs.length - 1 && !loading" class="follow-ups">
                  <div class="follow-ups-header">
                    <svg viewBox="0 0 24 24" fill="none" width="13" height="13" stroke="currentColor" stroke-width="2">
                      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                    </svg>
                    <span class="follow-ups-label">深入追问</span>
                  </div>
                  <div class="follow-ups-list">
                    <button v-for="(q, qi) in m.followUps" :key="qi" class="follow-up-btn" type="button" @click="send(q)">
                      <span>{{ q }}</span>
                      <svg viewBox="0 0 24 24" fill="none" width="12" height="12" stroke="currentColor" stroke-width="2">
                        <path d="M5 12h14M12 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 悬浮回到底部按钮 -->
        <button
          v-if="msgs.length > 0 && !autoFollow"
          type="button"
          class="scroll-bottom-pill"
          aria-label="回到底部"
          @click="scrollToBottom(true)"
        >
          <svg viewBox="0 0 24 24" fill="none" width="14" height="14" stroke="currentColor" stroke-width="2.2">
            <path d="M12 5v14M19 12l-7 7-7-7" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>回到底部</span>
        </button>
      </div>

      <!-- 输入工作台 -->
      <div class="composer-area">
        <!-- 活跃对话时的快捷追问条 -->
        <div class="composer-chips" v-if="msgs.length">
          <button class="chip-sm" type="button" :disabled="loading" @click="send('结合战术体系详细解释一下')">
            <span class="chip-sm-icon">🔍</span>
            <span>详细解释</span>
          </button>
          <button class="chip-sm" type="button" :disabled="loading" @click="send('对比同位置其他顶级球星的数据表现')">
            <span class="chip-sm-icon">⚖️</span>
            <span>对比其他球员</span>
          </button>
          <button class="chip-sm" type="button" :disabled="loading" @click="send('请给出以上结论的数据来源与依据')">
            <span class="chip-sm-icon">📚</span>
            <span>数据来源依据</span>
          </button>
          <button class="chip-sm" type="button" :disabled="loading" @click="send('提炼核心战术要点与攻防胜负手')">
            <span class="chip-sm-icon">🎯</span>
            <span>战术核心要点</span>
          </button>
        </div>

        <!-- 胶囊工作台 -->
        <div class="composer-wrap" :class="{ focus: composerFocused, 'has-text': canSend }" @click="focusInput">
          <textarea
            ref="inputEl"
            v-model="question"
            placeholder="询问球员、球队、赛程或战术问题 (例如: 掘金第四节关键时刻攻防表现如何？)..."
            rows="1"
            maxlength="500"
            :disabled="loading"
            aria-label="输入篮球问题或战术分析指令"
            @keydown="handleKeydown"
            @input="autoGrow"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
          ></textarea>
          <div class="composer-footer">
            <div class="composer-hints-left">
              <span class="hint-pill" title="已集成 NBA 知识库检索与实时赛况增强">
                <span class="hint-dot"></span>
                <span>NBA 知识库增强</span>
              </span>
              <span class="hint-key">
                <kbd class="kbd-shortcut">Enter</kbd> 发送 <span class="hint-sep">·</span> <kbd class="kbd-shortcut">Ctrl+Enter</kbd> 换行
              </span>
            </div>
            <div class="composer-actions-right">
              <span class="char-count" :class="{ warn: question.length > 450, error: question.length === 500 }">
                {{ question.length }}/500
              </span>
              <button
                class="send-btn"
                :class="{ active: canSend, loading, 'is-stop': loading }"
                type="button"
                :disabled="!canSend && !loading"
                :aria-label="loading ? '停止生成' : '发送问题'"
                :title="loading ? '点击停止生成' : canSend ? '发送提问 (Enter)' : '请输入问题'"
                @click="handleComposerBtnClick"
              >
                <template v-if="!loading">
                  <svg viewBox="0 0 24 24" fill="none" width="16" height="16" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 19V5M5 12l7-7 7 7" />
                  </svg>
                </template>
                <template v-else>
                  <svg class="stop-icon" viewBox="0 0 24 24" fill="currentColor" width="13" height="13">
                    <rect x="4" y="4" width="16" height="16" rx="3" />
                  </svg>
                </template>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { getToken, clearAuthStorage } from '@/utils/authStorage'
import { createTextBatcher } from '@/utils/textBatcher'
import RelatedEntityCard from '@/components/domain/RelatedEntityCard.vue'
import { useMatchFeed } from '@/composables/useMatchFeed'
import type { RelatedEntity } from '@/api/types'
import {
  renderMarkdown,
  groupConversations,
  filterGroupedConversations,
  prepareRetryTurn,
  copyText,
} from '@/utils/smartSearchHelpers'

interface M {
  role: 'user' | 'assistant'
  content: string
  sources?: string[]
  showSources?: boolean
  responseTime?: number
  followUps?: string[]
  error?: string
  relatedEntities?: RelatedEntity[]
}
interface C { id: string; title: string; time: number; messages: M[] }

const route = useRoute()
const router = useRouter()
const inputEl = ref<HTMLTextAreaElement>()
const msgsEl = ref<HTMLElement>()
const question = ref(typeof route.query.q === 'string' ? route.query.q.slice(0, 200) : '')
const msgs = ref<M[]>([])
const loading = ref(false)
const docCount = ref(0)
const sid = ref(gen())
const desktopSidebarQuery = typeof window !== 'undefined' ? window.matchMedia('(min-width: 769px)') : null
const sidebarOpen = ref(desktopSidebarQuery?.matches ?? false)
const isMobileViewport = ref(!(desktopSidebarQuery?.matches ?? false))
const convs = ref<C[]>([])
const searchConvText = ref('')
const composerFocused = ref(false)
const statusText = ref('正在思考...')
const elapsed = ref(0)
const copiedMsgIndex = ref<number | null>(null)
let currentAbort: AbortController | null = null
let currentTextBatch: ReturnType<typeof createTextBatcher> | null = null
const activeAssistantIndex = ref<number | null>(null)
let statusTimer: ReturnType<typeof setTimeout> | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null
let scrollFrame: number | null = null
const autoFollow = ref(true)
const AUTO_FOLLOW_THRESHOLD = 96
const STREAM_IDLE_TIMEOUT_MS = Number(import.meta.env.VITE_RAG_STREAM_IDLE_TIMEOUT_MS || 300000)
const { matches: feedMatches, tickerMatches, live: liveMatches, groups: matchGroups, loading: feedLoading, failed: feedFailed, refresh: refreshMatchFeed } = useMatchFeed()
const showLocalScoreStatus = computed(() => isMobileViewport.value || !tickerMatches.value.length)

// 会话删除确认状态
const convToDelete = ref<string | null>(null)
let deleteTimer: ReturnType<typeof setTimeout> | null = null

function requestDelConv(id: string): void {
  if (deleteTimer) clearTimeout(deleteTimer)
  convToDelete.value = id
  deleteTimer = setTimeout(() => {
    if (convToDelete.value === id) convToDelete.value = null
  }, 4000)
}

function cancelDelConv(): void {
  if (deleteTimer) clearTimeout(deleteTimer)
  convToDelete.value = null
}

async function confirmDelConv(id: string): Promise<void> {
  if (deleteTimer) clearTimeout(deleteTimer)
  convToDelete.value = null
  await delConv(id)
}

const scoreStatusText = computed(() => {
  if (feedLoading.value && !feedMatches.value.length) return '赛况更新中'
  if (feedFailed.value && !feedMatches.value.length) return '赛况暂不可用'
  return tickerMatches.value.length ? '当前暂无直播比赛' : '当前暂无比赛'
})

const todayMatches = computed(() => matchGroups.value.find(group => group.kind === 'today')?.matches ?? [])
const recentMatches = computed(() => matchGroups.value.find(group => group.kind === 'recent')?.matches ?? [])
const featuredTitle = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '今日赛事分析'
  return recentMatches.value.length ? '最近比赛复盘' : '赛事分析'
})
const featuredDescription = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '用真实赛况梳理胜负关键、核心对位与比赛看点。'
  if (recentMatches.value.length) return '回看最近已结束的比赛，梳理关键转折与数据。'
  return '从赛程、球队或球员入手，提出你的篮球问题。'
})
const featuredPrompt = computed(() => {
  if (liveMatches.value.length || todayMatches.value.length) return '基于今天的真实赛事数据，分析比赛看点、核心对位与胜负关键；未开赛的比赛请明确标注为赛前分析。'
  if (recentMatches.value.length) return '根据最近已结束的比赛数据，复盘关键转折和双方表现，并标明比赛日期与数据来源。'
  return '目前可以分析哪些 NBA 赛事、球队和球员数据？请说明数据范围。'
})

const groupedConvs = computed(() => groupConversations(convs.value))

const filteredGroupedConvs = computed(() => filterGroupedConversations(groupedConvs.value, searchConvText.value))

function onSidebarBreakpointChange(event: MediaQueryListEvent): void {
  sidebarOpen.value = event.matches
  isMobileViewport.value = !event.matches
}

function gen() { return 's_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) }
const canSend = computed(() => question.value.trim().length > 0 && !loading.value && question.value.trim().length <= 500)

// 持久化当前对话到 sessionStorage，切换页面后回来可恢复
const STORAGE_KEY = 'nba_copilot_active_session'

function saveActiveSession(): void {
  if (!msgs.value.length) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ sid: sid.value, msgs: msgs.value }))
  } catch { /* ignore quota errors */ }
}

function restoreActiveSession(): boolean {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const { sid: savedSid, msgs: savedMsgs } = JSON.parse(raw)
    if (Array.isArray(savedMsgs) && savedMsgs.length > 0) {
      sid.value = savedSid
      msgs.value = savedMsgs
      autoFollow.value = true
      return true
    }
  } catch { /* ignore parse errors */ }
  return false
}

function render(m: M, i: number): string {
  const isStreaming = loading.value && i === msgs.value.length - 1
  return renderMarkdown(m.content, isStreaming)
}

async function copyMessage(text: string, index: number): Promise<void> {
  const success = await copyText(text)
  if (success) {
    copiedMsgIndex.value = index
    setTimeout(() => {
      if (copiedMsgIndex.value === index) copiedMsgIndex.value = null
    }, 2000)
  }
}

function autoGrow(): void {
  const e = inputEl.value
  if (!e) return
  e.style.height = 'auto'
  if (!question.value) {
    e.style.height = ''
    return
  }
  const nextHeight = Math.min(Math.max(e.scrollHeight, 40), 180)
  e.style.height = `${nextHeight}px`
}

watch(question, (val) => {
  if (!val) {
    if (inputEl.value) inputEl.value.style.height = ''
  } else {
    nextTick(autoGrow)
  }
})

function focusInput(e: MouseEvent): void {
  if (loading.value) return
  const target = e.target as HTMLElement
  if (target.closest('button, a')) return
  if (target === inputEl.value) return
  if (inputEl.value) {
    inputEl.value.focus()
    const len = inputEl.value.value.length
    inputEl.value.setSelectionRange(len, len)
  }
}

function isNearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= AUTO_FOLLOW_THRESHOLD
}

function handleContentScroll(): void {
  if (msgsEl.value) autoFollow.value = isNearBottom(msgsEl.value)
}

function scrollToBottom(force = false): void {
  if (!force && !autoFollow.value) return
  nextTick(() => {
    if (!msgsEl.value) return
    if (scrollFrame !== null) {
      cancelAnimationFrame(scrollFrame)
      scrollFrame = null
    }
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = null
      if (!msgsEl.value) return
      msgsEl.value.scrollTop = msgsEl.value.scrollHeight
    })
  })
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.isComposing || e.keyCode === 229) return
  if (e.key === 'Enter') {
    if (!e.ctrlKey && !e.shiftKey && !e.metaKey) {
      e.preventDefault()
      if (canSend.value) send()
    } else if (e.ctrlKey || e.metaKey) {
      e.preventDefault()
      const target = (e.target as HTMLTextAreaElement) || inputEl.value
      if (target) {
        const start = target.selectionStart ?? question.value.length
        const end = target.selectionEnd ?? question.value.length
        const prev = question.value
        question.value = prev.slice(0, start) + '\n' + prev.slice(end)
        nextTick(() => {
          target.selectionStart = target.selectionEnd = start + 1
          autoGrow()
        })
      } else {
        question.value += '\n'
        nextTick(autoGrow)
      }
    }
  }
}

function handleGlobalKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    if (showShareModal.value) {
      showShareModal.value = false
    } else if (convToDelete.value) {
      cancelDelConv()
    } else if (loading.value) {
      cancelQuery()
    }
  }
}

function handleComposerBtnClick(): void {
  if (loading.value) {
    cancelQuery()
  } else if (canSend.value) {
    send()
  }
}

function clearTimers(): void {
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  elapsed.value = 0
  statusText.value = '正在思考...'
}

function cancelQuery(): void {
  currentTextBatch?.flush()
  currentTextBatch?.dispose()
  currentTextBatch = null
  const index = activeAssistantIndex.value
  if (index !== null && msgs.value[index]?.role === 'assistant') {
    if (!msgs.value[index].content.trim()) {
      msgs.value[index].error = '回答已取消'
    } else {
      msgs.value[index].followUps = []
    }
  }
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  activeAssistantIndex.value = null
  clearTimers()
  loading.value = false
  saveActiveSession()
}

function retryLast(): void {
  const { remainingMessages, textToRetry } = prepareRetryTurn(msgs.value)
  msgs.value = remainingMessages
  if (textToRetry) {
    send(textToRetry)
  }
}

async function loadConvs(): Promise<void> {
  try {
    const { data } = await request.get('/rag/conversations')
    convs.value = data || []
  } catch {
    convs.value = []
  }
}

async function saveConv(snapshotMsgs?: M[]): Promise<void> {
  const source = snapshotMsgs || msgs.value
  if (!source.length) return
  const validMsgs: M[] = []
  for (let index = 0; index < source.length - 1; index += 1) {
    const user = source[index]
    const assistant = source[index + 1]
    if (user.role !== 'user' || assistant.role !== 'assistant' || assistant.error || !assistant.content.trim()) continue
    validMsgs.push(user, assistant)
    index += 1
  }
  if (!validMsgs.length) return
  const title = validMsgs[0]?.content?.slice(0, 30) || '新对话'
  try {
    await request.post('/rag/conversations', { sessionId: sid.value, title, messages: JSON.stringify(validMsgs) })
    await loadConvs()
  } catch { /* ignore */ }
}

async function loadConv(c: C): Promise<void> {
  try {
    const { data } = await request.get(`/rag/conversations/${c.id}`)
    if (data?.messages) {
      sid.value = c.id
      msgs.value = JSON.parse(data.messages)
      if (!desktopSidebarQuery?.matches) sidebarOpen.value = false
      autoFollow.value = true
      saveActiveSession()
      scrollToBottom(true)
    }
  } catch { /* ignore */ }
}

async function delConv(id: string): Promise<void> {
  try {
    await request.delete(`/rag/conversations/${id}`)
    convs.value = convs.value.filter(c => c.id !== id)
    if (sid.value === id) newChat()
  } catch { /* ignore */ }
}

function newChat(): void {
  if (loading.value) cancelQuery()
  const snapshot = [...msgs.value]
  msgs.value = []
  sid.value = gen()
  autoFollow.value = true
  if (!desktopSidebarQuery?.matches) sidebarOpen.value = false
  sessionStorage.removeItem(STORAGE_KEY)
  if (snapshot.length) void saveConv(snapshot)
}

function fmtTime(t: number): string {
  const d = Date.now() - t
  if (d < 60000) return '刚刚'
  if (d < 3600000) return `${Math.floor(d / 60000)} 分钟前`
  if (d < 86400000) return `${Math.floor(d / 3600000)} 小时前`
  return new Date(t).toLocaleDateString('zh-CN')
}

function exportChat(): void {
  if (!msgs.value.length) return
  let md = `# NBA Copilot 对话记录\n\n- 导出时间: ${new Date().toLocaleString('zh-CN')}\n- 会话标识: ${sid.value}\n\n---\n\n`
  msgs.value.forEach(m => {
    md += `### ${m.role === 'user' ? '用户' : 'NBA Copilot 助手'}\n\n`
    if (m.error) {
      md += `> *[异常提示: ${m.error}]*\n\n`
    } else if (m.content) {
      md += `${m.content}\n\n`
    }
    if (m.responseTime) {
      md += `> 响应耗时: ${(m.responseTime / 1000).toFixed(1)}s\n\n`
    }
    if (m.sources?.length) {
      md += `> **参考来源:**\n${m.sources.map((s, idx) => `> ${idx + 1}. ${s}`).join('\n')}\n\n`
    }
    md += `---\n\n`
  })
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `NBA_Copilot_对话_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

const shareUrl = ref('')
const showShareModal = ref(false)
const shareCopied = ref(false)

async function shareChat(): Promise<void> {
  if (!msgs.value.length) return
  await saveConv()
  try {
    const { data } = await request.post(`/rag/conversations/${sid.value}/share`)
    if (data?.shareId) {
      const target = router.resolve({ name: 'smart-search', query: { share: data.shareId } })
      shareUrl.value = new URL(target.href, `${window.location.origin}/`).toString()
      shareCopied.value = false
      showShareModal.value = true
      await copyText(shareUrl.value)
    }
  } catch { /* ignore */ }
}

async function copyShareUrl(): Promise<void> {
  const success = await copyText(shareUrl.value)
  if (success) {
    shareCopied.value = true
    setTimeout(() => { shareCopied.value = false }, 2000)
  }
}

function selectShareInput(event: FocusEvent): void {
  const input = event.target as HTMLInputElement | null
  input?.select()
}

async function send(text?: string): Promise<void> {
  const content = (text || question.value.trim()).slice(0, 500)
  if (!content || loading.value) return
  loading.value = true
  msgs.value.push({ role: 'user', content })
  question.value = ''
  nextTick(() => {
    if (inputEl.value) {
      inputEl.value.style.height = ''
    }
  })
  const ai = msgs.value.length
  msgs.value.push({ role: 'assistant', content: '' })
  activeAssistantIndex.value = ai
  autoFollow.value = true
  scrollToBottom(true)

  statusText.value = '正在检索相关赛事数据...'
  elapsed.value = 0
  elapsedTimer = setInterval(() => { elapsed.value++ }, 1000)
  statusTimer = setTimeout(() => { statusText.value = '正在生成深度分析回答...' }, 3000)

  const abortController = new AbortController()
  currentAbort = abortController
  const textBatch = createTextBatcher(text => {
    if (currentAbort !== abortController) return
    msgs.value[ai].content += text
    scrollToBottom()
  })
  currentTextBatch = textBatch
  let idleTimedOut = false
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  const clearIdleTimeout = () => {
    if (timeoutId) clearTimeout(timeoutId)
    timeoutId = null
  }
  const resetIdleTimeout = () => {
    clearIdleTimeout()
    timeoutId = setTimeout(() => {
      if (loading.value) {
        idleTimedOut = true
        abortController.abort()
      }
    }, STREAM_IDLE_TIMEOUT_MS)
  }
  resetIdleTimeout()

  try {
    const token = getToken() || ''
    const headers: Record<string, string> = {}
    if (token) headers.Authorization = `Bearer ${token}`
    const streamParams = new URLSearchParams({ question: content, top_k: '5' })
    if (sid.value) streamParams.set('session_id', sid.value)

    let response!: Response
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        response = await fetch(`/api/rag/stream?${streamParams.toString()}`, {
          method: 'GET',
          headers,
          signal: abortController.signal,
        })
        break
      } catch (err: any) {
        if (err?.name === 'AbortError' || attempt === 1) throw err
        statusText.value = '连接中断，正在重连...'
        await new Promise((resolve) => setTimeout(resolve, 1200))
      }
    }

    if (currentAbort !== abortController) return
    if (abortController.signal.aborted) {
      if (idleTimedOut) throw new Error('Stream idle timeout')
      return
    }
    if (response.status === 401) {
      msgs.value[ai].error = '登录已过期，请重新登录'
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
      clearAuthStorage()
      setTimeout(() => router.push({ name: 'login', query: { redirect: route.fullPath } }), 1500)
      return
    }
    if (!response.ok) {
      msgs.value[ai].error = response.status === 403 ? '没有权限使用 AI 助手，请联系管理员' : `服务异常 (${response.status})`
      msgs.value[ai].content = ''
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let receivedDone = false
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      if (abortController.signal.aborted || currentAbort !== abortController) {
        await reader.cancel()
        if (idleTimedOut && currentAbort === abortController) throw new Error('Stream idle timeout')
        return
      }
      resetIdleTimeout()
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const d = JSON.parse(line.slice(6))
            if (d.type === 'status') {
              statusText.value = d.message || '正在检索比赛和数据来源...'
            } else if (d.type === 'sources') {
              msgs.value[ai].sources = d.sources || []
              if (Array.isArray(d.sourceDetails)) {
                const references = d.sourceDetails.filter((source: any) => typeof source?.url === 'string').map((source: any) => `${source.title || '比赛数据'} — ${source.url}`)
                msgs.value[ai].sources = [...new Set([...(msgs.value[ai].sources || []), ...references])]
              }
              statusText.value = '正在生成回答...'
            } else if (d.type === 'chunk') {
              if (typeof d.content === 'string') textBatch.push(d.content)
            } else if (d.type === 'entities') {
              msgs.value[ai].relatedEntities = d.entities || []
            } else if (d.type === 'done') {
              textBatch.flush()
              receivedDone = true
              msgs.value[ai].responseTime = d.response_time
              msgs.value[ai].followUps = Array.isArray(d.follow_ups)
                ? [...new Set(d.follow_ups.filter((q: unknown): q is string => typeof q === 'string' && q.trim().length > 0 && q.length <= 200))].slice(0, 3) as string[]
                : []
              if (d.entities?.length && !msgs.value[ai].relatedEntities?.length) msgs.value[ai].relatedEntities = d.entities
              loading.value = false
              currentAbort = null
              activeAssistantIndex.value = null
              clearIdleTimeout()
              clearTimers()
              saveConv()
              saveActiveSession()
              break
            } else if (d.type === 'error') {
              textBatch.flush()
              msgs.value[ai].error = d.error || '服务暂时不可用'
              msgs.value[ai].followUps = []
              loading.value = false
              currentAbort = null
              activeAssistantIndex.value = null
              clearIdleTimeout()
              clearTimers()
              saveActiveSession()
              break
            }
          } catch { /* ignore */ }
        }
      }
      if (receivedDone || currentAbort !== abortController) { await reader.cancel(); break }
    }
    textBatch.flush()
    if (currentAbort !== null && currentAbort !== abortController) return
    if (abortController.signal.aborted) {
      if (idleTimedOut && currentAbort === abortController) throw new Error('Stream idle timeout')
      return
    }
    if (loading.value) {
      if (!receivedDone) {
        msgs.value[ai].error = '连接在回答完成前中断，请重试'
        msgs.value[ai].followUps = []
      }
      loading.value = false
      currentAbort = null
      activeAssistantIndex.value = null
      clearIdleTimeout()
      clearTimers()
    }
  } catch (err: any) {
    if (abortController.signal.aborted && !idleTimedOut) return
    if (currentAbort !== abortController) return
    textBatch.flush()
    if (idleTimedOut) {
      msgs.value[ai].error = 'AI 长时间未返回新数据，请稍后重试'
      msgs.value[ai].followUps = []
    } else if (err.name !== 'AbortError') {
      msgs.value[ai].error = '网络连接失败，请检查网络后重试'
      msgs.value[ai].followUps = []
    }
    loading.value = false
    currentAbort = null
    activeAssistantIndex.value = null
    clearIdleTimeout()
    clearTimers()
  } finally {
    clearIdleTimeout()
    textBatch.dispose()
    if (currentTextBatch === textBatch) currentTextBatch = null
  }
}

onMounted(async () => {
  desktopSidebarQuery?.addEventListener('change', onSidebarBreakpointChange)
  window.addEventListener('keydown', handleGlobalKeydown)
  if (!feedMatches.value.length) void refreshMatchFeed()
  try {
    const { data } = await request.get('/rag/stats')
    docCount.value = data?.documentCount || 0
  } catch {
    docCount.value = 0
  }
  const shareId = typeof route.query.share === 'string' ? route.query.share.trim() : ''
  if (shareId) {
    try {
      const { data } = await request.get(`/rag/shared/${shareId}`)
      if (data?.messages) {
        sid.value = data.id
        msgs.value = JSON.parse(data.messages)
        scrollToBottom(true)
      }
    } catch { /* ignore */ }
  } else if (!restoreActiveSession()) {
    await loadConvs()
  } else {
    await loadConvs()
    nextTick(() => scrollToBottom(true))
  }
})

onUnmounted(() => {
  desktopSidebarQuery?.removeEventListener('change', onSidebarBreakpointChange)
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (deleteTimer) clearTimeout(deleteTimer)
  saveActiveSession()
  cancelQuery()
  if (scrollFrame !== null) cancelAnimationFrame(scrollFrame)
})
</script>

<style scoped src="./SmartSearchSports.css"></style>
