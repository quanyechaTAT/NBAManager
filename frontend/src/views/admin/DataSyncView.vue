<template>
  <div class="data-management-page">
    <!-- 空间质感科技背景 -->
    <div class="bg-decoration" aria-hidden="true">
      <div class="bg-orb bg-orb-1"></div>
      <div class="bg-orb bg-orb-2"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 顶部控制台头部 -->
    <header class="page-header">
      <div class="header-left">
        <div class="header-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="m13 2-9 12h7l-1 8 9-12h-7l1-8Z" />
          </svg>
        </div>
        <div class="header-title-box">
          <div class="header-title-row">
            <h2>数据调度中心</h2>
            <span class="system-status-chip" :class="{ 'is-running': isAnySyncing }">
              <span class="status-pulse-dot"></span>
              {{ isAnySyncing ? '数据同步中' : '调度引擎待命' }}
            </span>
            <span class="season-badge font-tabular">{{ currentSeason }} 赛季</span>
          </div>
          <p class="subtitle">NBA 全维度官方数据流调度与实时同步控制台 · 支持一键全量流水线与领域独立调度</p>
        </div>
      </div>

      <div class="header-actions">
        <button
          v-if="isAnySyncing"
          class="btn-action btn-cancel"
          :disabled="isCancelling"
          @click="handleCancelAll"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
          <span>{{ isCancelling ? '正在请求取消...' : '取消全部' }}</span>
        </button>

        <button
          class="btn-primary"
          :disabled="!canSyncAll"
          @click="handleSyncAll"
        >
          <svg
            class="btn-icon"
            :class="{ spinning: isAnySyncing || submittingAll }"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
            <path d="M3 3v5h5" />
            <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
            <path d="M16 21h5v-5" />
          </svg>
          <span>{{ isAnySyncing ? '同步中...' : submittingAll ? '正在提交...' : '一键全量同步' }}</span>
        </button>
      </div>
    </header>

    <!-- 加载中指示器 -->
    <div v-if="initialLoading" class="status-banner loading-banner" role="status">
      <span class="status-spinner" aria-hidden="true"></span>
      <span>正在加载任务状态...</span>
    </div>

    <!-- 刷新异常告警 -->
    <div v-if="refreshError" class="failure-alert" role="alert">
      <div class="failure-alert-main">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <path d="M12 8v4M12 16h.01" />
        </svg>
        <span>{{ refreshError }}</span>
      </div>
      <button class="btn-ghost" @click="loadOverview">重新连接</button>
    </div>

    <!-- 调度操作说明提示条 -->
    <div class="sync-guidance-bar">
      <div class="guidance-content">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="guidance-icon">
          <circle cx="12" cy="12" r="10" />
          <path d="M12 16v-4M12 8h.01" />
        </svg>
        <p class="sync-help">全量同步按步骤顺序执行；单个模块可独立同步。页面自动刷新，取消后需等待后端确认。</p>
      </div>
      <div class="guidance-meta">
        <span class="polling-tag">
          <span class="polling-dot"></span>
          自动轮询：{{ isAnySyncing ? '3秒/次 (实时)' : '15秒/次 (闲时)' }}
        </span>
      </div>
    </div>

    <!-- 全局进度仪表板 -->
    <Transition name="slide-down">
      <div v-if="isAnySyncing" class="global-progress">
        <div class="progress-info">
          <div class="progress-label-wrap">
            <span class="progress-label">{{ fullSyncJobId ? '全量同步进度' : '任务进度' }}</span>
            <span class="progress-pipeline-badge">{{ fullSyncJobId ? `流水线 #${fullSyncJobId}` : `${currentSyncingModules.length} 模块活动` }}</span>
          </div>
          <span class="progress-value font-tabular">{{ globalProgress }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-bar" :style="{ width: globalProgress + '%' }"></div>
        </div>
        <div class="progress-status">
          <span class="pulse-dot"></span>
          <span>正在同步: {{ currentSyncingModules.join('、') || '准备中...' }}</span>
        </div>
      </div>
    </Transition>

    <!-- 失败告警（置顶展示） -->
    <Transition name="slide-down">
      <div v-if="failedModules.length > 0 && !isAnySyncing" class="failure-alert" role="alert">
        <div class="failure-alert-main">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="m12 3 9 17H3L12 3Z" />
            <path d="M12 9v5M12 17v.01" />
          </svg>
          <div class="failure-alert-text">
            <strong>{{ failedModules.length }} 个模块同步失败</strong>
            <span>{{ failedModules.map(m => m.name).join('、') }}</span>
          </div>
        </div>
        <button class="btn-sm-retry" :disabled="isAnySyncing || isSubmitting" @click="retryFailed">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="retry-icon">
            <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
            <path d="M3 3v5h5" />
            <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
            <path d="M16 21h5v-5" />
          </svg>
          <span>{{ isSubmitting ? '正在提交...' : '重试失败模块' }}</span>
        </button>
      </div>
    </Transition>

    <!-- 成功结果提示条 -->
    <Transition name="slide-down">
      <div v-if="recentSuccess && !isAnySyncing && failedModules.length === 0" class="success-alert">
        <div class="success-alert-main">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="m5 12 4 4L19 6" />
          </svg>
          <span>{{ recentSuccess }}</span>
        </div>
      </div>
    </Transition>

    <!-- 核心统计概览卡片 -->
    <section class="stats-row" aria-label="数据中心关键指标">
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M4 19V5M4 19h16" />
            <path d="m7 15 3-4 3 2 5-7" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value font-tabular">{{ totalCount.toLocaleString() }}</span>
          <span class="stat-label" title="各模块统计口径不同，包含重叠记录与待翻译项">模块计数合计</span>
        </div>
      </div>

      <div class="stat-card" :class="{ 'stat-active': syncingCount > 0 }">
        <div class="stat-icon stat-icon--cyan" aria-hidden="true">
          <svg :class="{ 'spinning': syncingCount > 0 }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M20 11a8 8 0 0 0-14.7-4L3 10" />
            <path d="M3 4v6h6" />
            <path d="M4 13a8 8 0 0 0 14.7 4L21 14" />
            <path d="M21 20v-6h-6" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value font-tabular">{{ syncingCount }}</span>
          <span class="stat-label">同步中</span>
        </div>
      </div>

      <div class="stat-card stat-success">
        <div class="stat-icon stat-icon--green" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m5 12 4 4L19 6" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value font-tabular">{{ successCount }} <span class="stat-sub">/ {{ modules.length }}</span></span>
          <span class="stat-label">已完成</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon--amber" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="8.5" />
            <path d="M12 7v5l3 2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value stat-time font-tabular">{{ lastSyncTime || '从未' }}</span>
          <span class="stat-label">上次同步</span>
        </div>
      </div>
    </section>

    <!-- 模块卡片网格 -->
    <section class="modules-section" aria-label="数据模块调度阵列">
      <div class="section-title-row">
        <div class="section-title-left">
          <span class="section-pill">Modules</span>
          <h3 class="section-heading">业务模块调度状态</h3>
        </div>
        <div class="section-title-hint">
          <span>共 {{ modules.length }} 个官方核心领域数据源</span>
        </div>
      </div>

      <div class="modules-grid">
        <div
          v-for="(module, index) in modules"
          :key="module.key"
          class="module-card"
          :class="[`status-${module.status}`, { 'is-syncing': module.status === 'RUNNING' }]"
          :style="{ animationDelay: index * 0.04 + 's' }"
        >
          <!-- 顶部状态彩条 -->
          <div class="card-indicator" :class="module.status"></div>

          <!-- 卡片内容主体 -->
          <div class="card-body">
            <!-- 头部：图标 + 标题与计数 + 状态胶囊 -->
            <div class="card-header">
              <div class="card-icon-wrap" :class="`icon-${module.key.toLowerCase()}`">
                <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                  <path :d="moduleIconPath(module.key)" />
                </svg>
                <span v-if="module.status === 'RUNNING'" class="icon-ring"></span>
              </div>

              <div class="card-meta">
                <div class="card-meta-top">
                  <h3>{{ module.name }}</h3>
                  <span v-if="module.triggerType" class="source-tag">
                    {{ triggerLabel(module.triggerType) }}
                  </span>
                </div>
                <div class="card-count-box">
                  <span class="card-count-val font-tabular">{{ module.count.toLocaleString() }}</span>
                  <span class="card-count-unit">条数据记录</span>
                </div>
              </div>

              <div class="card-badge" :class="module.status">
                <span class="badge-dot"></span>
                <span>{{ getStatusText(module.status) }}</span>
              </div>
            </div>

            <!-- 执行进度条：只在任务进行中展示 -->
            <div v-if="ACTIVE_STATUSES.has(module.status)" class="card-progress">
              <span class="card-progress-label">执行</span>
              <div class="progress-track">
                <div class="progress-bar" :class="module.status" :style="{ width: module.progress + '%' }"></div>
              </div>
              <span class="progress-pct font-tabular">{{ module.progress }}%</span>
            </div>

            <!-- 终态结果呈现 -->
            <div v-else-if="TERMINAL_STATUSES.has(module.status)" class="card-result" :class="module.status">
              <span class="result-label">同步结果：</span>
              <span class="result-text">{{ getStatusText(module.status) }}</span>
            </div>

            <!-- 全量执行步骤指示 -->
            <div v-if="module.activeFullStep" class="step-stage">
              <span class="step-badge">全量步骤 {{ module.activeFullStep.stepOrder + 1 }}</span>
              <span class="step-name">{{ module.activeFullStep.stage || getStatusText(module.activeFullStep.status) }}</span>
            </div>

            <!-- 卡片底栏：最后同步时间与操作按钮 -->
            <div class="card-footer">
              <div class="card-time-group">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 7v5l3 2" />
                </svg>
                <span class="card-time font-tabular">
                  {{ module.lastSyncTime ? '最近成功 ' + formatTime(module.lastSyncTime) : '暂无成功记录' }}
                </span>
              </div>

              <div class="card-actions">
                <button
                  v-if="!fullSyncJobId && ACTIVE_STATUSES.has(module.status)"
                  class="btn-sm btn-sm-cancel"
                  :disabled="(module.jobId != null && cancellingIds.has(module.jobId)) || module.status === 'CANCELLING'"
                  @click="handleCancelModule(module.key)"
                  title="取消任务"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path d="m6 6 12 12M18 6 6 18" />
                  </svg>
                  <span>取消</span>
                </button>

                <button
                  v-if="!ACTIVE_STATUSES.has(module.status)"
                  class="btn-sm btn-sm-sync"
                  :disabled="!canSyncModule(module.key)"
                  @click="handleSyncModule(module.key)"
                >
                  <svg
                    class="sync-action-icon"
                    :class="{ spinning: pendingModules.has(module.key) }"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                    <path d="M3 3v5h5" />
                    <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
                    <path d="M16 21h5v-5" />
                  </svg>
                  <span>{{ pendingModules.has(module.key) ? '正在提交...' : '同步' }}</span>
                </button>
              </div>
            </div>

            <!-- 异常折叠面板 -->
            <Transition name="slide-down">
              <div
                v-if="(module.status === 'FAILED' || module.status === 'PARTIAL_FAILED' || module.status === 'INTERRUPTED') && module.errorMessage"
                class="card-error"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                  <path d="m12 3 9 17H3L12 3Z" />
                  <path d="M12 9v5M12 17v.01" />
                </svg>
                <div class="card-error-text">{{ module.errorMessage }}</div>
              </div>
            </Transition>
          </div>
        </div>
      </div>
    </section>

    <!-- 底部双翼工作区：历史记录与日志终端 -->
    <div class="operations-console-grid">
      <!-- 同步历史面板 -->
      <section class="history-panel" aria-label="调度历史">
        <div class="history-header">
          <div class="panel-title-group">
            <div class="panel-icon-wrap" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 6v6l4 2" />
              </svg>
            </div>
            <div>
              <h3>同步历史</h3>
              <span>自动、手动与补偿任务使用同一记录</span>
            </div>
          </div>
          <button class="btn-ghost" @click="loadHistory(0)" :disabled="historyLoading">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: historyLoading }">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
              <path d="M3 3v5h5" />
            </svg>
            <span>刷新</span>
          </button>
        </div>

        <!-- 筛选过滤行 -->
        <div class="history-filters">
          <div class="select-field">
            <select v-model="historyModule" aria-label="模块筛选">
              <option value="">全部模块</option>
              <option v-for="(_, key) in MODULE_META" :key="key" :value="key">{{ MODULE_META[key].name }}</option>
              <option value="FULL_SYNC">全量同步</option>
            </select>
          </div>

          <div class="select-field">
            <select v-model="historyTriggerType" aria-label="来源筛选">
              <option value="">全部来源</option>
              <option value="MANUAL">手动</option>
              <option value="SCHEDULED">定时</option>
              <option value="LOGIN_CATCHUP">登录补偿</option>
              <option value="STARTUP_CATCHUP">启动补偿</option>
            </select>
          </div>

          <div class="select-field">
            <select v-model="historyStatus" aria-label="状态筛选">
              <option value="">全部状态</option>
              <option value="QUEUED">等待中</option>
              <option value="RUNNING">运行中</option>
              <option value="CANCELLING">取消中</option>
              <option value="SUCCESS">成功</option>
              <option value="PARTIAL_FAILED">部分失败</option>
              <option value="FAILED">失败</option>
              <option value="CANCELLED">已取消</option>
              <option value="INTERRUPTED">已中断</option>
            </select>
          </div>

          <button class="btn-action history-query" @click="loadHistory(0)">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.3-4.3" />
            </svg>
            <span>查询</span>
          </button>
        </div>

        <p v-if="historyError" class="history-error-banner" role="alert">{{ historyError }}</p>

        <!-- 历史记录表格 -->
        <div class="history-table-wrap">
          <table class="history-table">
            <thead>
              <tr>
                <th style="width: 80px;">任务</th>
                <th style="width: 140px;">模块</th>
                <th style="width: 100px;">来源</th>
                <th style="width: 100px;">状态</th>
                <th style="width: 90px;">进度</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="job in historyJobs"
                :key="job.jobId"
                :class="{ 'is-selected': selectedJob?.jobId === job.jobId }"
                @click="showJobDetail(job.jobId)"
              >
                <td class="job-id-cell font-tabular">#{{ job.jobId }}</td>
                <td class="module-name-cell">
                  <strong>{{ job.module === 'FULL_SYNC' ? '全量同步' : (MODULE_META[job.module]?.name || job.module) }}</strong>
                </td>
                <td><span class="source-tag">{{ triggerLabel(job.triggerType) }}</span></td>
                <td>
                  <span class="history-status" :class="job.status">
                    <span class="status-dot"></span>
                    {{ getStatusText(job.status) }}
                  </span>
                </td>
                <td class="progress-cell font-tabular">
                  <div class="mini-progress-track">
                    <div class="mini-progress-bar" :class="job.status" :style="{ width: job.progress + '%' }"></div>
                  </div>
                  <span>{{ job.progress }}%</span>
                </td>
                <td class="time-cell font-tabular">{{ formatTime(job.createdAt) }}</td>
              </tr>
              <tr v-if="!historyLoading && historyJobs.length === 0">
                <td colspan="6" class="history-empty">暂无匹配任务记录</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页控制器 -->
        <div class="history-pagination">
          <button class="btn-ghost btn-pagination" :disabled="historyLoading || historyPage === 0" @click="loadHistory(historyPage - 1)">
            上一页
          </button>
          <span class="pagination-indicator font-tabular">
            第 {{ historyPage + 1 }} / {{ Math.max(historyTotalPages, 1) }} 页
          </span>
          <button class="btn-ghost btn-pagination" :disabled="historyLoading || historyPage + 1 >= historyTotalPages" @click="loadHistory(historyPage + 1)">
            下一页
          </button>
        </div>

        <p v-if="detailLoading" class="detail-loading-banner" role="status">正在加载任务详情...</p>
        <p v-if="detailError" class="detail-error-banner" role="alert">{{ detailError }}</p>

        <!-- 任务详情抽屉/卡片 -->
        <Transition name="slide-down">
          <div v-if="selectedJob" class="job-detail">
            <div class="job-detail-header">
              <div class="job-detail-title">
                <strong class="font-tabular">任务 #{{ selectedJob.jobId }}</strong>
                <span class="source-tag">{{ triggerLabel(selectedJob.triggerType) }}</span>
                <span class="history-status" :class="selectedJob.status">{{ getStatusText(selectedJob.status) }}</span>
              </div>
              <button class="btn-ghost btn-detail-close" @click="closeDetail">关闭</button>
            </div>

            <p v-if="selectedJob.errorMessage" class="job-detail-error" role="alert">
              {{ selectedJob.errorMessage }}
            </p>

            <div v-if="selectedJob.module !== 'FULL_SYNC'" class="job-data-stats font-tabular">
              <span class="stat-badge stat-insert">新增 {{ selectedJob.insertedCount }}</span>
              <span class="stat-badge stat-update">更新 {{ selectedJob.updatedCount }}</span>
              <span class="stat-badge stat-skip">跳过 {{ selectedJob.skippedCount }}</span>
            </div>

            <!-- 子步骤执行流 -->
            <div v-if="selectedJob.steps.length" class="step-list">
              <div v-for="step in selectedJob.steps" :key="step.id" class="step-row" :class="`step-${step.status}`">
                <span class="step-name">{{ step.stepOrder + 1 }}. {{ MODULE_META[step.module]?.name || step.module }}</span>
                <span class="step-status" :class="step.status">{{ getStatusText(step.status) }}</span>
                <span class="step-pct font-tabular">{{ step.progress }}%</span>
              </div>
            </div>
            <p v-else class="history-empty">该任务没有子步骤</p>
          </div>
        </Transition>
      </section>

      <!-- 同步流水日志面板 -->
      <section class="log-panel" aria-label="调度流水日志">
        <div class="log-header">
          <div class="log-title">
            <div class="terminal-dots" aria-hidden="true">
              <span class="dot dot-red"></span>
              <span class="dot dot-yellow"></span>
              <span class="dot dot-green"></span>
            </div>
            <span>同步日志</span>
            <span class="log-count font-tabular" v-if="logs.length > 0">{{ logs.length }}</span>
          </div>
          <button class="btn-ghost" @click="logs = []" v-if="logs.length > 0">清空日志</button>
        </div>

        <div class="log-body" ref="logContainer">
          <div v-if="logs.length === 0" class="log-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path d="M8 4h8M9 3h6v3H9zM6 5H4v16h16V5h-2" />
              <path d="M8 11h8M8 15h6" />
            </svg>
            <span>暂无同步记录</span>
          </div>

          <div
            v-for="(log, i) in logs"
            :key="i"
            class="log-entry"
            :class="log.type"
          >
            <span class="log-time font-tabular">{{ log.time }}</span>
            <span class="log-badge">{{ log.module }}</span>
            <span class="log-msg">{{ log.message }}</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import {
  useSyncCenter,
  MODULE_META,
  ACTIVE_STATUSES,
  TERMINAL_STATUSES,
  getStatusText,
  formatTime,
  triggerLabel,
} from '@/composables/useSyncCenter'

const {
  currentSeason,
  modules,
  logs,
  initialLoading,
  refreshError,
  loadOverview,
  fullSyncJobId,
  isAnySyncing,
  isSubmitting,
  submittingAll,
  pendingModules,
  isCancelling,
  cancellingIds,
  canSyncAll,
  canSyncModule,
  totalCount,
  syncingCount,
  successCount,
  failedModules,
  recentSuccess,
  lastSyncTime,
  globalProgress,
  currentSyncingModules,
  retryFailed,
  handleSyncAll,
  handleSyncModule,
  handleCancelAll,
  handleCancelModule,
  historyJobs,
  historyPage,
  historyTotalPages,
  historyModule,
  historyTriggerType,
  historyStatus,
  historyLoading,
  historyError,
  loadHistory,
  selectedJob,
  detailLoading,
  detailError,
  showJobDetail,
  closeDetail,
} = useSyncCenter()

const logContainer = ref<HTMLElement | null>(null)

watch(
  () => logs.value[logs.value.length - 1],
  async () => {
    await nextTick()
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  },
)

const MODULE_ICON_PATHS: Record<string, string> = {
  TEAMS: 'M4 20V9l8-5 8 5v11M7 20v-6h10v6M9 9h.01M12 9h.01M15 9h.01',
  PLAYERS: 'M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  MATCHES: 'M4 19V5M4 19h16M7 15l3-4 3 2 5-7',
  TODAY_GAMES: 'M12 3v18M3 12h18M5.6 5.6l12.8 12.8M18.4 5.6 5.6 18.4',
  NEWS: 'M5 4h14v16H5zM8 8h8M8 12h8M8 16h5',
  DRAFT: 'M6 4h12v16H6zM9 8h6M9 12h6M9 16h4',
  PLAYOFF: 'M6 3h12v6a6 6 0 0 1-12 0V3ZM12 15v6M8 21h8',
  HISTORICAL: 'M5 5h14v14H5zM8 3v4M16 3v4M5 10h14M9 14h2M13 14h2',
  TRANSLATION: 'M4 5h16v14H4zM8 9h8M8 13h5',
}

function moduleIconPath(key: string): string {
  return MODULE_ICON_PATHS[key] || MODULE_ICON_PATHS.MATCHES
}
</script>

<style scoped>
/* ==========================================================================
   数据调度中心 (Data Dispatch Center) 专业管理系统样式
   ========================================================================== */

.data-management-page {
  position: relative;
  max-width: 1400px;
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 28px 32px 64px;
  color: var(--text-primary);
  box-sizing: border-box;
}

/* 等宽数字基座 */
.font-tabular {
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum' 1;
}

/* 背景装饰光感 */
.bg-decoration {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.12;
}

.bg-orb-1 {
  width: 460px;
  height: 460px;
  top: -120px;
  right: -100px;
  background: radial-gradient(circle, var(--accent, #0891b2), transparent 70%);
}

.bg-orb-2 {
  width: 360px;
  height: 360px;
  bottom: 80px;
  left: -80px;
  background: radial-gradient(circle, #6366f1, transparent 70%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--border-light) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-light) 1px, transparent 1px);
  background-size: 48px 48px;
  opacity: 0.2;
}

/* ==========================================================================
   页面顶部标头
   ========================================================================== */
.page-header {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.2) 0%, rgba(245, 158, 11, 0.15) 100%);
  border: 1px solid rgba(8, 145, 178, 0.35);
  border-radius: 12px;
  color: var(--accent, #0891b2);
  box-shadow: 0 4px 16px rgba(8, 145, 178, 0.15);
  flex-shrink: 0;
}

.header-icon svg {
  width: 24px;
  height: 24px;
}

.header-title-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-title-row h2 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  margin: 0;
  font-family: var(--font-heading);
}

.system-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: var(--success, #10b981);
  font-size: 11px;
  font-weight: 700;
}

.system-status-chip.is-running {
  background: rgba(8, 145, 178, 0.14);
  border-color: rgba(8, 145, 178, 0.4);
  color: var(--accent, #0891b2);
}

.status-pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 8px currentColor;
  animation: pulseDot 1.8s infinite;
}

@keyframes pulseDot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
}

.season-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--bg-hover);
  border: 1px solid var(--border-medium);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.4;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

/* ==========================================================================
   按钮体系
   ========================================================================== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 20px;
  background: linear-gradient(135deg, var(--accent, #0891b2) 0%, #06b6d4 100%);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 4px 14px rgba(8, 145, 178, 0.25);
  white-space: nowrap;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(8, 145, 178, 0.38);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.16s ease;
  white-space: nowrap;
}

.btn-cancel {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

.btn-cancel:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.22);
  border-color: #ef4444;
  transform: translateY(-1px);
}

.btn-cancel:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 7px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.16s ease;
  white-space: nowrap;
}

.btn-ghost:hover:not(:disabled) {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.1));
  border-color: var(--accent, #0891b2);
  color: var(--accent, #0891b2);
}

.btn-ghost:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ==========================================================================
   提示与指示状态条
   ========================================================================== */
.status-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 13px;
  font-weight: 600;
  position: relative;
  z-index: 1;
}

.loading-banner {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.status-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--border-light);
  border-top-color: var(--accent, #0891b2);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.failure-alert,
.success-alert {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 18px;
  border-radius: 12px;
  backdrop-filter: blur(8px);
}

.failure-alert {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

.failure-alert-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.failure-alert-main svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.failure-alert-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.failure-alert-text strong {
  font-size: 14px;
  font-weight: 700;
}

.failure-alert-text span {
  font-size: 12px;
  opacity: 0.9;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-sm-retry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid rgba(239, 68, 68, 0.45);
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.16s ease;
  white-space: nowrap;
  flex-shrink: 0;
}

.btn-sm-retry:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.22);
  border-color: #ef4444;
}

.btn-sm-retry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.retry-icon {
  width: 14px;
  height: 14px;
}

.success-alert {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.35);
  color: var(--success, #10b981);
}

.success-alert-main {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
}

.success-alert-main svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

/* 调度提示条 */
.sync-guidance-bar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.guidance-content {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.guidance-icon {
  width: 16px;
  height: 16px;
  color: var(--accent, #0891b2);
  flex-shrink: 0;
}

.sync-help {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.guidance-meta {
  display: flex;
  align-items: center;
}

.polling-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
}

.polling-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent, #0891b2);
  animation: pulseDot 2s infinite;
}

/* ==========================================================================
   全局进度指示器
   ========================================================================== */
.global-progress {
  position: relative;
  z-index: 2;
  background: var(--bg-card);
  border: 1px solid rgba(8, 145, 178, 0.4);
  border-radius: 14px;
  padding: 18px 22px;
  margin-bottom: 22px;
  box-shadow: 0 8px 30px rgba(8, 145, 178, 0.12);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-label-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-label {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.progress-pipeline-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--accent-lighter, rgba(8, 145, 178, 0.12));
  color: var(--accent, #0891b2);
}

.progress-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--accent, #0891b2);
  font-family: var(--font-heading);
}

.progress-track {
  height: 8px;
  background: var(--bg-hover);
  border-radius: 999px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--accent, #0891b2), #06b6d4);
  border-radius: inherit;
  transition: width 0.35s ease;
  position: relative;
}

.progress-bar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: shimmer 1.8s infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.progress-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-dim);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent, #0891b2);
  box-shadow: 0 0 0 4px rgba(8, 145, 178, 0.15);
  animation: pulseDot 1.5s infinite;
}

/* ==========================================================================
   统计概览四宫格
   ========================================================================== */
.stats-row {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  transition: all 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-medium);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
}

.stat-card.stat-active {
  border-color: rgba(8, 145, 178, 0.6);
  box-shadow: 0 0 20px rgba(8, 145, 178, 0.12);
}

.stat-card.stat-success {
  border-color: rgba(16, 185, 129, 0.35);
}

.stat-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  flex-shrink: 0;
}

.stat-icon svg {
  width: 20px;
  height: 20px;
}

.stat-icon--purple {
  background: rgba(99, 102, 241, 0.12);
  color: #6366f1;
}

.stat-icon--cyan {
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891b2);
}

.stat-icon--green {
  background: rgba(16, 185, 129, 0.12);
  color: var(--success, #10b981);
}

.stat-icon--amber {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
  line-height: 1.1;
}

.stat-sub {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-dim);
}

.stat-time {
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

/* ==========================================================================
   模块调度阵列 (Modules Section)
   ========================================================================== */
.modules-section {
  position: relative;
  z-index: 1;
  margin-bottom: 32px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.section-title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-pill {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--accent-lighter, rgba(8, 145, 178, 0.12));
  color: var(--accent, #0891b2);
}

.section-heading {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.section-title-hint {
  font-size: 12px;
  color: var(--text-dim);
}

.modules-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.22s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.module-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-medium);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.module-card.is-syncing {
  border-color: rgba(8, 145, 178, 0.65);
  box-shadow: 0 0 24px rgba(8, 145, 178, 0.15);
}

.card-indicator {
  height: 3px;
  width: 100%;
  background: var(--border-light);
  transition: background 0.2s ease;
}

.card-indicator.RUNNING {
  background: linear-gradient(90deg, var(--accent, #0891b2), #06b6d4);
  animation: indicatorShimmer 2s infinite linear;
}

.card-indicator.SUCCESS {
  background: var(--success, #10b981);
}

.card-indicator.FAILED,
.card-indicator.PARTIAL_FAILED,
.card-indicator.INTERRUPTED {
  background: #ef4444;
}

@keyframes indicatorShimmer {
  0% { opacity: 0.7; }
  50% { opacity: 1; }
  100% { opacity: 0.7; }
}

.card-body {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.card-icon-wrap {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  flex-shrink: 0;
}

.card-icon {
  width: 20px;
  height: 20px;
}

.icon-ring {
  position: absolute;
  inset: -3px;
  border: 2px solid var(--accent, #0891b2);
  border-radius: 13px;
  opacity: 0;
  animation: ringPulse 1.6s infinite;
}

@keyframes ringPulse {
  0% { opacity: 0; transform: scale(0.95); }
  50% { opacity: 0.6; }
  100% { opacity: 0; transform: scale(1.1); }
}

.card-meta {
  flex: 1;
  min-width: 0;
}

.card-meta-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
  flex-wrap: wrap;
}

.card-meta-top h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.source-tag {
  display: inline-flex;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  color: var(--text-dim);
  font-size: 10px;
  font-weight: 600;
}

.card-count-box {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.card-count-val {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.card-count-unit {
  font-size: 11px;
  color: var(--text-dim);
}

.card-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  background: var(--bg-hover);
  color: var(--text-dim);
}

.badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.card-badge.RUNNING,
.card-badge.QUEUED,
.card-badge.CANCELLING {
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891b2);
}

.card-badge.SUCCESS {
  background: rgba(16, 185, 129, 0.12);
  color: var(--success, #10b981);
}

.card-badge.FAILED,
.card-badge.PARTIAL_FAILED,
.card-badge.INTERRUPTED {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

/* 进度条与结果 */
.card-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  margin-bottom: 12px;
}

.card-progress-label {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}

.card-progress .progress-track {
  flex: 1;
  height: 6px;
  background: var(--bg-hover);
  border-radius: 999px;
  overflow: hidden;
}

.card-progress .progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--accent, #0891b2), #06b6d4);
  border-radius: inherit;
  transition: width 0.3s ease;
}

.progress-pct {
  min-width: 32px;
  font-size: 12px;
  font-weight: 700;
  color: var(--accent, #0891b2);
  text-align: right;
}

.card-result {
  margin-top: 4px;
  margin-bottom: 12px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--bg-hover);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.result-label {
  color: var(--text-dim);
}

.result-text {
  font-weight: 700;
}

.card-result.SUCCESS .result-text {
  color: var(--success, #10b981);
}

.card-result.FAILED .result-text,
.card-result.PARTIAL_FAILED .result-text,
.card-result.INTERRUPTED .result-text {
  color: #ef4444;
}

.step-stage {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  margin-bottom: 10px;
}

.step-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891b2);
  font-weight: 700;
}

.step-name {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 底栏与操作 */
.card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.card-time-group {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-dim);
}

.card-time {
  font-size: 11px;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.16s ease;
  white-space: nowrap;
}

.btn-sm-sync {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.btn-sm-sync:hover:not(:disabled) {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.12));
  border-color: var(--accent, #0891b2);
  color: var(--accent, #0891b2);
  transform: translateY(-1px);
}

.btn-sm-sync:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.sync-action-icon {
  width: 13px;
  height: 13px;
}

.btn-sm-cancel {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.btn-sm-cancel:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.2);
  border-color: #ef4444;
}

.btn-sm-cancel svg {
  width: 13px;
  height: 13px;
}

.card-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  color: #f87171;
  font-size: 11px;
  line-height: 1.5;
}

.card-error svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  color: #ef4444;
}

.card-error-text {
  word-break: break-word;
}

/* ==========================================================================
   底部工作台：历史表格与实时日志终端
   ========================================================================== */
.operations-console-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.history-panel,
.log-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.panel-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-icon-wrap {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.panel-icon-wrap svg {
  width: 16px;
  height: 16px;
}

.history-header,
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.history-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.history-header span {
  font-size: 12px;
  color: var(--text-dim);
}

.history-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) 100px;
  gap: 10px;
  padding: 12px 20px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.select-field select {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--border-medium);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 500;
  outline: none;
  cursor: pointer;
  transition: border-color 0.16s ease;
}

.select-field select:focus {
  border-color: var(--accent, #0891b2);
}

.history-query {
  height: 36px;
  background: var(--accent, #0891b2);
  color: #fff;
  border: none;
  justify-content: center;
}

.history-query:hover {
  background: var(--accent-hover, #06b6d4);
}

.history-error-banner {
  margin: 0;
  padding: 10px 20px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
}

.history-table-wrap {
  overflow-x: auto;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
}

.history-table th {
  padding: 12px 18px;
  background: var(--bg-hover);
  color: var(--text-dim);
  font-size: 11px;
  font-weight: 700;
  text-align: left;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.history-table td {
  padding: 13px 18px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.history-table tbody tr {
  cursor: pointer;
  transition: background 0.14s ease;
}

.history-table tbody tr:hover {
  background: var(--bg-hover);
}

.history-table tbody tr.is-selected {
  background: var(--accent-lighter, rgba(8, 145, 178, 0.08));
}

.job-id-cell {
  font-weight: 700;
  color: var(--accent, #0891b2);
}

.module-name-cell strong {
  color: var(--text-primary);
}

.history-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 700;
  font-size: 11px;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.history-status.SUCCESS { color: var(--success, #10b981); }
.history-status.RUNNING,
.history-status.QUEUED { color: var(--accent, #0891b2); }
.history-status.FAILED,
.history-status.PARTIAL_FAILED { color: #ef4444; }

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mini-progress-track {
  width: 48px;
  height: 5px;
  border-radius: 999px;
  background: var(--bg-hover);
  overflow: hidden;
}

.mini-progress-bar {
  height: 100%;
  background: var(--accent, #0891b2);
}

.mini-progress-bar.SUCCESS {
  background: var(--success, #10b981);
}

.mini-progress-bar.FAILED {
  background: #ef4444;
}

.history-empty {
  text-align: center;
  padding: 36px !important;
  color: var(--text-dim) !important;
  font-size: 13px;
}

.history-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid var(--border-light);
}

.btn-pagination {
  height: 30px;
  padding: 0 12px;
}

.pagination-indicator {
  font-size: 12px;
  color: var(--text-dim);
}

/* 任务详情弹层 */
.job-detail {
  padding: 18px 20px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-hover);
}

.job-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.job-detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.job-detail-title strong {
  font-size: 14px;
  color: var(--text-primary);
}

.btn-detail-close {
  height: 28px;
  padding: 0 10px;
}

.job-detail-error {
  margin: 0 0 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
}

.job-data-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.stat-badge {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
}

.stat-insert {
  background: rgba(16, 185, 129, 0.12);
  color: var(--success, #10b981);
}

.stat-update {
  background: rgba(8, 145, 178, 0.12);
  color: var(--accent, #0891b2);
}

.stat-skip {
  background: var(--bg-card);
  color: var(--text-dim);
  border: 1px solid var(--border-light);
}

.step-list {
  display: grid;
  gap: 6px;
}

.step-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--bg-card);
  border-left: 3px solid var(--accent, #0891b2);
  font-size: 12px;
}

.step-row.step-SUCCESS {
  border-left-color: var(--success, #10b981);
}

.step-row.step-FAILED {
  border-left-color: #ef4444;
}

.step-status {
  font-weight: 600;
}

.step-status.SUCCESS { color: var(--success, #10b981); }
.step-status.FAILED { color: #ef4444; }

.detail-loading-banner,
.detail-error-banner {
  margin: 0;
  padding: 10px 20px;
  font-size: 12px;
}

/* ==========================================================================
   日志终端控制台 (Terminal Console)
   ========================================================================== */
.terminal-dots {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.dot-red { background: #ef4444; }
.dot-yellow { background: #f59e0b; }
.dot-green { background: #10b981; }

.log-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-heading);
}

.log-count {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent-lighter, rgba(8, 145, 178, 0.12));
  color: var(--accent, #0891b2);
  font-size: 10px;
  font-weight: 800;
}

.log-body {
  max-height: 280px;
  overflow-y: auto;
  padding: 10px 18px;
  background: var(--bg-card);
  font-family: var(--font-mono, 'Fira Code', 'JetBrains Mono', Consolas, monospace);
}

.log-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--text-dim);
  font-size: 13px;
}

.log-empty svg {
  width: 18px;
  height: 18px;
}

.log-entry {
  display: grid;
  grid-template-columns: 80px 110px 1fr;
  gap: 12px;
  align-items: baseline;
  padding: 7px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
}

.log-entry:last-child {
  border-bottom: none;
}

.log-time {
  color: var(--text-dim);
  font-size: 11px;
}

.log-badge {
  font-weight: 700;
  color: var(--text-secondary);
}

.log-msg {
  color: var(--text-secondary);
  word-break: break-all;
}

.log-entry.error .log-msg {
  color: #f87171;
}

.log-entry.success .log-msg {
  color: #86efac;
}

/* ==========================================================================
   动画与响应式断点
   ========================================================================== */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 1100px) {
  .modules-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .data-management-page {
    padding: 20px 16px 40px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions button {
    flex: 1;
    justify-content: center;
  }

  .modules-grid {
    grid-template-columns: 1fr;
  }

  .history-filters {
    grid-template-columns: 1fr 1fr;
  }

  .history-query {
    grid-column: span 2;
  }

  .log-entry {
    grid-template-columns: 65px 85px 1fr;
    gap: 8px;
  }
}
</style>
