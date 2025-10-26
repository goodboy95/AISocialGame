import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import pinia from '../store';
import { useAuthStore } from '../store/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'dashboard',
    component: () => import('../pages/AdminDashboard.vue')
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async () => {
  const authStore = useAuthStore(pinia);
  await authStore.initialize();
  return true;
});

export default router;
