import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      redirect: '/projects',
      children: [
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectList.vue'),
        },
        {
          path: 'project/:id',
          name: 'project-detail',
          component: () => import('@/views/ProjectDetail.vue'),
        },
        {
          path: 'qa',
          name: 'qa',
          component: () => import('@/views/QA.vue'),
        },
        {
          path: 'graph',
          name: 'graph',
          component: () => import('@/views/Graph.vue'),
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/Settings.vue'),
        },
      ],
    },
  ],
})

export default router