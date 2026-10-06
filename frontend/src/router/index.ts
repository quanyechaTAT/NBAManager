import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/authStorage'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '赛事总览' },
        },
        {
          path: 'teams',
          name: 'teams',
          component: () => import('@/views/TeamListView.vue'),
          meta: { title: '球队战绩榜' },
        },
        {
          path: 'teams/detail',
          name: 'team-detail',
          component: () => import('@/views/TeamDetailView.vue'),
          meta: { title: '球队详情' },
        },
        {
          path: 'players',
          name: 'players',
          component: () => import('@/views/PlayerListView.vue'),
          meta: { title: '球员列表' },
        },
        {
          path: 'players/detail',
          name: 'player-detail',
          component: () => import('@/views/PlayerDetailView.vue'),
          meta: { title: '球员详情', requiresAuth: true },
        },
        {
          path: 'players/compare',
          name: 'player-compare',
          component: () => import('@/views/PlayerCompareView.vue'),
          meta: { title: '球员对比' },
        },
        {
          path: 'news',
          name: 'news',
          component: () => import('@/views/NewsView.vue'),
          meta: { title: '赛事中心' },
        },
        {
          path: 'match-detail',
          name: 'match-detail',
          component: () => import('@/views/MatchDetailView.vue'),
          meta: { title: '比赛详情', requiresAuth: true },
        },
        {
          path: 'community',
          name: 'community',
          component: () => import('@/views/CommunityView.vue'),
          meta: { title: '球迷社区' },
        },
        {
          path: 'community/post',
          name: 'post-detail',
          component: () => import('@/views/PostDetailView.vue'),
          meta: { title: '帖子详情' },
        },
        {
          path: 'drafts',
          name: 'drafts',
          component: () => import('@/views/DraftView.vue'),
          meta: { title: '选秀数据' },
        },
        {
          path: 'playoff',
          name: 'playoff',
          component: () => import('@/views/PlayoffView.vue'),
          meta: { title: '季后赛对阵图' },
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/HistoricalDataView.vue'),
          meta: { title: '历史数据' },
        },
        {
          path: 'smart-search',
          name: 'smart-search',
          component: () => import('@/views/SmartSearchView.vue'),
          meta: { title: 'AI 助手' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
        {
          path: 'admin/sync',
          name: 'admin-sync',
          component: () => import('@/views/admin/DataSyncView.vue'),
          meta: { title: '数据调度中心', requiresAdmin: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.hydrate()

  const shareId = typeof to.query.share === 'string' ? to.query.share : ''
  if (to.name === 'smart-search' && /^[A-Za-z0-9]{8,64}$/.test(shareId)) {
    return true
  }

  if (to.meta.public) {
    if (getToken() && (to.path === '/login' || to.path === '/register')) {
      return { path: '/dashboard' }
    }
    return true
  }

  if (to.meta.requiresAuth && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 管理员路由守卫
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { path: '/dashboard' }
  }

  return true
})

export default router
