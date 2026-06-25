import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      redirect: '/projects',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectList.vue')
        },
        {
          path: 'project/:id',
          name: 'project-detail',
          component: () => import('@/views/ProjectDetail.vue')
        },
        {
          path: 'project/:id/qa',
          name: 'project-qa',
          component: () => import('@/views/QA.vue')
        },
        {
          path: 'project/:id/graph',
          name: 'project-graph',
          component: () => import('@/views/Graph.vue')
        },
        {
          path: 'project/:id/files',
          name: 'project-files',
          component: () => import('@/components/FileViewer.vue')
        },
        {
          path: 'qa',
          name: 'qa',
          component: () => import('@/views/QA.vue')
        },
        {
          path: 'graph',
          name: 'graph',
          component: () => import('@/views/Graph.vue')
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/Settings.vue')
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next('/projects')
  } else {
    next()
  }
})

export default router