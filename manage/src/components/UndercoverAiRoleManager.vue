<template>
  <div class="ai-role-manager">
    <div class="ai-role-manager__header">
      <div>
        <h3>AI 角色配置</h3>
        <p>为“谁是卧底”配置可选的 AI 玩家角色及行为倾向。</p>
      </div>
      <div class="ai-role-manager__actions">
        <el-button @click="refreshModels" :loading="modelLoading">刷新模型列表</el-button>
        <el-button type="primary" @click="openCreateDialog">新增 AI 角色</el-button>
      </div>
    </div>

    <el-alert
      v-if="models.length === 0"
      type="warning"
      show-icon
      class="ai-role-manager__alert"
    >
      当前没有可用的 AI 模型配置，请先在“AI 模型配置管理”中创建模型后再新增角色。
    </el-alert>

    <el-table :data="roles" v-loading="loading" border>
      <el-table-column prop="name" label="角色名称" min-width="160" />
      <el-table-column label="绑定模型" min-width="180">
        <template #default="{ row }">
          <el-tag type="success">{{ modelName(row.model.id) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="性格与倾向" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="ai-role-manager__personality">{{ row.personality }}</div>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updated_at) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && roles.length === 0"
      description="暂未配置 AI 角色"
      class="ai-role-manager__empty"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" placeholder="例如：稳健型卧底" />
        </el-form-item>
        <el-form-item label="绑定模型" required>
          <el-select v-model="form.model_id" placeholder="选择已配置的模型">
            <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="性格与倾向" required>
          <el-input
            v-model="form.personality"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            placeholder="描述 AI 的语言风格、策略倾向等"
          />
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
import {
  createUndercoverRole,
  deleteUndercoverRole,
  fetchAiModels,
  fetchUndercoverRoles,
  updateUndercoverRole,
  type AiModelConfig,
  type UndercoverAiRole,
  type UndercoverAiRolePayload
} from "../api/manage";
import { notifyError, notifySuccess } from "../services/notifications";

const loading = ref(false);
const modelLoading = ref(false);
const roles = ref<UndercoverAiRole[]>([]);
const models = ref<AiModelConfig[]>([]);
const dialogVisible = ref(false);
const submitting = ref(false);
const mode = ref<"create" | "edit">("create");
const editingId = ref<number | null>(null);

const form = reactive<UndercoverAiRolePayload>({
  name: "",
  model_id: 0,
  personality: ""
});

const dialogTitle = computed(() => (mode.value === "create" ? "新增 AI 角色" : "编辑 AI 角色"));

async function loadRoles() {
  loading.value = true;
  try {
    roles.value = await fetchUndercoverRoles();
  } catch (error) {
    console.error("Failed to load AI roles", error);
  } finally {
    loading.value = false;
  }
}

async function loadModels() {
  modelLoading.value = true;
  try {
    models.value = await fetchAiModels();
  } catch (error) {
    console.error("Failed to load AI models", error);
  } finally {
    modelLoading.value = false;
  }
}

function refreshModels() {
  void loadModels();
}

function openCreateDialog() {
  if (models.value.length === 0) {
    notifyError("请先创建至少一个 AI 模型配置");
    return;
  }
  mode.value = "create";
  editingId.value = null;
  form.name = "";
  form.personality = "";
  form.model_id = models.value[0]?.id ?? 0;
  dialogVisible.value = true;
}

function openEditDialog(role: UndercoverAiRole) {
  mode.value = "edit";
  editingId.value = role.id;
  form.name = role.name;
  form.personality = role.personality;
  form.model_id = role.model.id;
  dialogVisible.value = true;
}

async function submitForm() {
  if (!form.name.trim() || !form.personality.trim() || !form.model_id) {
    notifyError("请完整填写角色信息");
    return;
  }
  submitting.value = true;
  try {
    if (mode.value === "create") {
      const created = await createUndercoverRole({ ...form });
      roles.value.push(created);
      notifySuccess("已新增 AI 角色");
    } else if (editingId.value !== null) {
      const updated = await updateUndercoverRole(editingId.value, { ...form });
      const index = roles.value.findIndex((item) => item.id === editingId.value);
      if (index !== -1) {
        roles.value[index] = updated;
      }
      notifySuccess("已更新 AI 角色");
    }
    dialogVisible.value = false;
  } catch (error) {
    console.error("Failed to save AI role", error);
  } finally {
    submitting.value = false;
  }
}

async function confirmDelete(role: UndercoverAiRole) {
  try {
    await ElMessageBox.confirm(`确定要删除「${role.name}」吗？`, "确认删除", {
      type: "warning"
    });
    await deleteUndercoverRole(role.id);
    roles.value = roles.value.filter((item) => item.id !== role.id);
    notifySuccess("已删除 AI 角色");
  } catch (error) {
    if (error !== "cancel") {
      console.error("Failed to delete AI role", error);
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

function modelName(id: number) {
  return models.value.find((item) => item.id === id)?.name ?? "未知模型";
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadModels()]);
});
</script>

<style scoped lang="scss">
.ai-role-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-role-manager__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.ai-role-manager__actions {
  display: flex;
  gap: 12px;
}

.ai-role-manager__alert {
  margin-bottom: 8px;
}

.ai-role-manager__empty {
  margin-top: 24px;
}

.ai-role-manager__personality {
  white-space: pre-wrap;
}
</style>
