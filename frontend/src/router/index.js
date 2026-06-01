import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'missions', name: 'Missions', component: () => import('../views/Missions.vue') },
      { path: 'telemetry', name: 'Telemetry', component: () => import('../views/Telemetry.vue') },
      { path: 'topology', name: 'Topology', component: () => import('../views/Topology.vue') },
      { path: 'alerts', name: 'Alerts', component: () => import('../views/Alerts.vue') },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.name !== 'Login') return { name: 'Login' }
})

export default router
