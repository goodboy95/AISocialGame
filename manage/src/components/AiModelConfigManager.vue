<template>
  <div class="model-manager">
    <div class="model-manager__header">
      <div class="model-manager__title">
        <el-button text @click="$emit('back')">返回</el-button>
        <div>
          <h2>AI 模型配置管理</h2>
          <p>维护可供平台调用的 AI 大模型接入信息。</p>
        </div>
      </div>
      <el-button type="primary" @click="openCreateDialog">新增模型配置</el-button>
    </div>

    <el-table :data="models" v-loading="loading" border>
      <el-table-column prop="name" label="配置名" min-width="160" />
      <el-table-column prop="base_url" label="BaseURL" min-width="240" />
      <el-table-column label="Token" min-width="220">
        <template #default="{ row }">
          <el-input v-model="tokenCopy[row.id]" readonly size="small">
            <template #append>
              <el-button text type="primary" @click="copyToken(row.token)">复制</el-button>
            </template>
          </el-input>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updated_at) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && models.length === 0"
      description="暂无模型配置"
      class="model-manager__empty"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="配置名" required>
          <el-input v-model="form.name" placeholder="用于展示的唯一名称" />
        </el-form-item>
        <el-form-item label="BaseURL" required>
          <el-input v-model="form.base_url" placeholder="例如：https://api.example.com" />
        </el-form-item>
        <el-form-item label="Token" required>
          <el-input v-model="form.token" placeholder="访问模型时使用的鉴权 token" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { notifyError, notifySuccess, notifyWarning } from "../services/notifications";
import {
  createAiModel,
  deleteAiModel,
  fetchAiModels,
  updateAiModel,
  type AiModelConfig,
  type AiModelConfigPayload
} from "../api/manage";

interface TokenBuffer {
  [key: number]: string;
}

const models = ref<AiModelConfig[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const mode = ref<"create" | "edit">("create");
const submitting = ref(false);
const editingId = ref<number | null>(null);
const tokenCopy = reactive<TokenBuffer>({});

const form = reactive<AiModelConfigPayload>({
  name: "",
  base_url: "",
  token: ""
});

const dialogTitle = computed(() => (mode.value === "create" ? "新增模型配置" : "编辑模型配置"));

function resetForm() {
  form.name = "";
  form.base_url = "";
  form.token = "";
}

async function loadModels() {
  loading.value = true;
  try {
    const data = await fetchAiModels();
    models.value = data;
    data.forEach((item) => {
      tokenCopy[item.id] = item.token;
    });
  } catch (error) {
    console.error("Failed to load AI models", error);
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  mode.value = "create";
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEditDialog(model: AiModelConfig) {
  mode.value = "edit";
  editingId.value = model.id;
  form.name = model.name;
  form.base_url = model.base_url;
  form.token = model.token;
  dialogVisible.value = true;
}

async function submitForm() {
  if (!form.name.trim() || !form.base_url.trim() || !form.token.trim()) {
    notifyError("请完整填写配置信息");
    return;
  }
  submitting.value = true;
  try {
    let result: AiModelConfig;
    if (mode.value === "create") {
      result = await createAiModel({ ...form });
      models.value.push(result);
      tokenCopy[result.id] = result.token;
      notifySuccess("已新增模型配置");
    } else if (editingId.value !== null) {
      result = await updateAiModel(editingId.value, { ...form });
      const index = models.value.findIndex((item) => item.id === editingId.value);
      if (index !== -1) {
        models.value[index] = result;
      }
      tokenCopy[result.id] = result.token;
      notifySuccess("已更新模型配置");
    }
    dialogVisible.value = false;
  } catch (error) {
    console.error("Failed to submit model config", error);
  } finally {
    submitting.value = false;
  }
}

async function confirmDelete(model: AiModelConfig) {
  try {
    await ElMessageBox.confirm(`确定要删除「${model.name}」配置吗？`, "确认删除", {
      type: "warning"
    });
    await deleteAiModel(model.id);
    models.value = models.value.filter((item) => item.id !== model.id);
    notifySuccess("已删除模型配置");
  } catch (error) {
    if (error !== "cancel") {
      console.error("Failed to delete model config", error);
    }
  }
}

function formatTime(input: string) {
  try {
    return new Intl.DateTimeFormat("zh-CN", {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(new Date(input));
  } catch (error) {
    return input;
  }
}

function copyToken(token: string) {
  if (typeof navigator !== "undefined" && navigator.clipboard) {
    navigator.clipboard
      .writeText(token)
      .then(() => notifySuccess("已复制 Token"))
      .catch((error) => {
        console.error("Copy token failed", error);
        notifyError("复制失败，请手动复制 Token");
      });
  } else {
    notifyWarning("当前浏览器不支持剪贴板操作");
  }
}

onMounted(() => {
  loadModels();
});
</script>

<style scoped lang="scss">
.model-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.model-manager__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.model-manager__title {
  display: flex;
  align-items: center;
  gap: 16px;
}

.model-manager__empty {
  margin-top: 24px;
}
</style>
