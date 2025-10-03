<template>
  <el-card class="auth-card">
    <h2>创建新账号</h2>
    <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" autocomplete="email" />
      </el-form-item>
      <el-form-item label="显示名称" prop="displayName">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSubmit">注册</el-button>
        <el-button link type="primary" @click="goLogin">已有账号？登录</el-button>
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
  email: "",
  displayName: "",
  password: "",
  confirmPassword: ""
});

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" }
  ],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
  confirmPassword: [
    { required: true, message: "请确认密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error("两次输入的密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur"
    }
  ]
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
    await authStore.register({
      username: form.username,
      email: form.email,
      password: form.password,
      displayName: form.displayName
    });
    router.push({ name: "lobby" });
  } finally {
    loading.value = false;
  }
}

function goLogin() {
  router.push({ name: "login" });
}
</script>

<style scoped>
.auth-card {
  max-width: 480px;
  margin: 48px auto;
}
</style>
