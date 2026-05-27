import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/authStorage'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
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
          meta: { title: '球队数据' },
        },
        {
          path: 'players',
          name: 'players',
          component: () => import('@/views/PlayerListView.vue'),
          meta: { title: '球员数据' },
        },
        {
          path: 'news',
          name: 'news',
          component: () => import('@/views/NewsView.vue'),
          meta: { title: '赛事资讯' },
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
