<template>
  <el-card class="auth-card">
    <h2>欢迎回来</h2>
    <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" autocomplete="current-password" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSubmit">登录</el-button>
        <el-button link type="primary" @click="goRegister">还没有账号？注册</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { useRouter } from "vue-router";
import { useAuthStore } from "../store/user";

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  username: "",
  password: ""
});

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
};

async function handleSubmit() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch (error) {
    console.warn("表单校验失败", error);
    return;
  }

  loading.value = true;
  try {
    await authStore.login(form.username, form.password);
    router.push({ name: "lobby" });
  } finally {
    loading.value = false;
  }
}

function goRegister() {
  router.push({ name: "register" });
}
</script>

<style scoped>
.auth-card {
  max-width: 420px;
  margin: 48px auto;
}
</style>
