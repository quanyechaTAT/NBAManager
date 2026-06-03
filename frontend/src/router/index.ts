import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/authStorage'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
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
          meta: { title: '数据看板' },
        },
        {
          path: 'teams',
          name: 'teams',
          component: () => import('@/views/TeamListView.vue'),
          meta: { title: '球队战绩' },
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
          meta: { title: '球员数据' },
        },
        {
          path: 'players/detail',
          name: 'player-detail',
          component: () => import('@/views/PlayerDetailView.vue'),
          meta: { title: '球员详情', requiresAuth: true },
        },
        {
          path: 'news',
          name: 'news',
          component: () => import('@/views/NewsView.vue'),
          meta: { title: '赛事资讯' },
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
          meta: { title: '社区' },
        },
        {
          path: 'community/post',
          name: 'post-detail',
          component: () => import('@/views/PostDetailView.vue'),
          meta: { title: '帖子详情' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人主页' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.hydrate()

  if (to.meta.public) {
    if (getToken() && (to.path === '/login' || to.path === '/register')) {
      return { path: '/dashboard' }
    }
    return true
  }

  if (to.meta.requiresAuth && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
