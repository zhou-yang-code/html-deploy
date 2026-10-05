import { createRouter, createWebHistory } from 'vue-router'

import { hasSession } from '@/api'
import LoginView from '@/views/LoginView.vue'
import ProjectView from '@/views/ProjectView.vue'
import ProjectsView from '@/views/ProjectsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/projects',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/projects',
      name: 'projects',
      component: ProjectsView,
      meta: { requiresAuth: true },
    },
    {
      path: '/projects/:projectId',
      name: 'project',
      component: ProjectView,
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !hasSession()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && hasSession()) {
    return { name: 'projects' }
  }
  return true
})
