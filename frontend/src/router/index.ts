import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";

import pinia from "../store";
import { useAuthStore } from "../store/user";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    redirect: { name: "lobby" }
  },
  {
    path: "/login",
    name: "login",
    component: () => import("../pages/LoginPage.vue")
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../pages/RegisterPage.vue")
  },
  {
    path: "/lobby",
    name: "lobby",
    component: () => import("../pages/LobbyPage.vue")
  },
  {
    path: "/room/:id",
    name: "room-detail",
    component: () => import("../pages/RoomPage.vue"),
    props: true
  },
  {
    path: "/stats",
    name: "stats",
    component: () => import("../pages/StatsPage.vue")
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia);
  await authStore.initialize();

  if (authStore.refreshToken) {
    const refreshed = await authStore.refreshSession();
    if (!refreshed && to.meta.requiresAuth) {
      return { name: "login", query: { redirect: to.fullPath } };
    }
  }

  if (to.meta.requiresAuth && !authStore.profile) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
