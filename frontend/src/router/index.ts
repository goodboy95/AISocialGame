import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";

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
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;
