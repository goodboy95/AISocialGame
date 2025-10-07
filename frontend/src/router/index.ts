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
  },
  {
    path: "/admin/word-bank",
    name: "word-bank-admin",
    component: () => import("../pages/WordBankAdminPage.vue"),
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore(pinia);
  if (to.meta.requiresAuth && !authStore.profile) {
    next({ name: "login", query: { redirect: to.fullPath } });
    return;
  }
  next();
});

export default router;
