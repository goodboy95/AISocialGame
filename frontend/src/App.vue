<template>
  <div class="layout">
    <el-container class="app-container">
      <el-header height="60px">
        <div class="brand">AI Social Game</div>
        <el-space>
          <el-button type="primary" link @click="goTo('login')">登录</el-button>
          <el-button type="primary" link @click="goTo('register')">注册</el-button>
          <el-button type="primary" link @click="goTo('lobby')">大厅</el-button>
        </el-space>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

function goTo(name: string) {
  router.push({ name });
}
</script>

<style scoped lang="scss">
.layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f6ff 0%, #ffffff 100%);
}

.app-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
}

.brand {
  font-size: 20px;
  font-weight: 600;
}
</style>
