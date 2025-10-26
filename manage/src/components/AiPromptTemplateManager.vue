<template>
  <div class="prompt-manager">
    <div class="prompt-manager__header">
      <div>
        <h3>提示词管理</h3>
        <p>维护不同游戏场景、角色与阶段下的系统提示词模板。</p>
      </div>
      <div class="prompt-manager__actions">
        <el-button @click="refreshPrompts" :loading="loading">刷新列表</el-button>
        <el-button type="primary" @click="openCreateDialog" :disabled="!filters.game_type">
          新增提示词
        </el-button>
      </div>
    </div>

    <el-card class="prompt-manager__filters" shadow="never">
      <el-form :inline="true" label-width="0">
        <el-form-item label="游戏">
          <el-select
            v-model="filters.game_type"
            placeholder="选择游戏"
            :loading="dictionaryLoading"
            style="width: 200px"
          >
            <el-option
              v-for="game in gameOptions"
              :key="game.key"
              :label="game.label"
              :value="game.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="filters.role_key"
            placeholder="全部角色"
            clearable
            @change="handleRoleFilterChange"
            style="width: 200px"
          >
            <el-option label="全部角色" value="" />
            <el-option
              v-for="role in roleOptions"
              :key="role.key"
              :label="role.label"
              :value="role.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段">
          <el-select
            v-model="filters.phase_key"
            placeholder="全部阶段"
            clearable
            @change="handlePhaseFilterChange"
            style="width: 200px"
          >
            <el-option label="全部阶段" value="" />
            <el-option
              v-for="phase in phaseOptions"
              :key="phase.key"
              :label="phase.label"
              :value="phase.key"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="prompts" border v-loading="loading">
      <el-table-column label="游戏" min-width="120">
        <template #default="{ row }">
          {{ gameLabel(row.game_type) }}
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">
          <el-tag>{{ roleLabel(row.role_key) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阶段" min-width="160">
        <template #default="{ row }">
          {{ phaseLabel(row.phase_key) }}
        </template>
      </el-table-column>
      <el-table-column prop="content" label="提示词内容" min-width="400" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="prompt-manager__content">{{ row.content }}</div>
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
      v-if="!loading && prompts.length === 0"
      description="暂未配置提示词"
      class="prompt-manager__empty"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form label-width="90px" class="prompt-manager__form">
        <el-form-item label="游戏" required>
          <el-select
            v-model="form.game_type"
            placeholder="选择游戏"
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="game in gameOptions"
              :key="game.key"
              :label="game.label"
              :value="game.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.role_key"
            placeholder="输入或选择角色"
            allow-create
            filterable
            default-first-option
            style="width: 220px"
          >
            <el-option
              v-for="role in dialogRoleOptions"
              :key="role.key"
              :label="role.label"
              :value="role.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段" required>
          <el-select
            v-model="form.phase_key"
            placeholder="输入或选择阶段"
            allow-create
            filterable
            default-first-option
            style="width: 220px"
          >
            <el-option
              v-for="phase in dialogPhaseOptions"
              :key="phase.key"
              :label="phase.label"
              :value="phase.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :autosize="{ minRows: 8, maxRows: 16 }"
            placeholder="建议包含 {{personality}}、{{phase_key}} 等占位符，描述 AI 应如何在该阶段行动。"
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
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";
import {
  createPromptTemplate,
  deletePromptTemplate,
  fetchPromptDictionary,
  fetchPromptTemplates,
  updatePromptTemplate,
  type AiPromptDictionary,
  type AiPromptGameOption,
  type AiPromptPhaseOption,
  type AiPromptRoleOption,
  type AiPromptTemplate,
  type AiPromptTemplatePayload
} from "../api/manage";
import { notifyError, notifySuccess } from "../services/notifications";

const loading = ref(false);
const dictionaryLoading = ref(false);
const prompts = ref<AiPromptTemplate[]>([]);
const dictionary = ref<AiPromptDictionary | null>(null);
const dialogVisible = ref(false);
const submitting = ref(false);
const mode = ref<"create" | "edit">("create");
const editingId = ref<number | null>(null);

const filters = reactive({
  game_type: "",
  role_key: "",
  phase_key: ""
});

const form = reactive<AiPromptTemplatePayload>({
  game_type: "",
  role_key: "",
  phase_key: "",
  content: ""
});

const dialogTitle = computed(() => (mode.value === "create" ? "新增提示词" : "编辑提示词"));

const gameOptions = computed<AiPromptGameOption[]>(() => dictionary.value?.games ?? []);

const currentGame = computed<AiPromptGameOption | undefined>(() =>
  gameOptions.value.find((game) => game.key === filters.game_type)
);

const roleOptions = computed<AiPromptRoleOption[]>(() => {
  const base = currentGame.value?.roles ?? [];
  return [...base];
});

const phaseOptions = computed<AiPromptPhaseOption[]>(() => {
  const base = currentGame.value?.phases ?? [];
  return [...base];
});

const dialogRoleOptions = computed<AiPromptRoleOption[]>(() => {
  const selectedGame = gameOptions.value.find((game) => game.key === form.game_type);
  return selectedGame ? [...selectedGame.roles] : [];
});

const dialogPhaseOptions = computed<AiPromptPhaseOption[]>(() => {
  const selectedGame = gameOptions.value.find((game) => game.key === form.game_type);
  return selectedGame ? [...selectedGame.phases] : [];
});

watch(
  () => filters.game_type,
  (gameKey) => {
    if (!gameKey) {
      prompts.value = [];
      return;
    }
    filters.role_key = "";
    filters.phase_key = "";
    void loadPrompts();
  }
);

onMounted(() => {
  void init();
});

async function init() {
  dictionaryLoading.value = true;
  try {
    dictionary.value = await fetchPromptDictionary();
    if (dictionary.value.games.length > 0) {
      filters.game_type = dictionary.value.games[0].key;
    }
  } catch (error) {
    console.error("Failed to load prompt dictionary", error);
  } finally {
    dictionaryLoading.value = false;
  }
}

async function loadPrompts() {
  loading.value = true;
  try {
    const params: Record<string, string> = { game_type: filters.game_type };
    if (filters.role_key) {
      params.role_key = filters.role_key;
    }
    if (filters.phase_key) {
      params.phase_key = filters.phase_key;
    }
    prompts.value = await fetchPromptTemplates(params);
  } catch (error) {
    console.error("Failed to load prompt templates", error);
  } finally {
    loading.value = false;
  }
}

function refreshPrompts() {
  if (!filters.game_type) {
    notifyError("请先选择游戏");
    return;
  }
  void loadPrompts();
}

function handleRoleFilterChange() {
  if (!filters.game_type) {
    return;
  }
  void loadPrompts();
}

function handlePhaseFilterChange() {
  if (!filters.game_type) {
    return;
  }
  void loadPrompts();
}

function openCreateDialog() {
  if (!filters.game_type) {
    notifyError("请先选择游戏");
    return;
  }
  mode.value = "create";
  editingId.value = null;
  form.game_type = filters.game_type;
  form.role_key = dictionary.value?.default_role_key ?? "general";
  const selectedGame = gameOptions.value.find((game) => game.key === form.game_type);
  form.phase_key = selectedGame?.phases[0]?.key ?? "";
  form.content = "";
  dialogVisible.value = true;
}

function openEditDialog(template: AiPromptTemplate) {
  mode.value = "edit";
  editingId.value = template.id;
  form.game_type = template.game_type;
  form.role_key = template.role_key;
  form.phase_key = template.phase_key;
  form.content = template.content;
  dialogVisible.value = true;
}

async function submitForm() {
  if (!form.game_type || !form.phase_key || !form.content.trim()) {
    notifyError("请完整填写提示词信息");
    return;
  }
  submitting.value = true;
  const payload: AiPromptTemplatePayload = {
    game_type: form.game_type,
    phase_key: form.phase_key,
    content: form.content.trim(),
    role_key: form.role_key && form.role_key.trim() ? form.role_key.trim() : dictionary.value?.default_role_key
  };
  try {
    if (mode.value === "create") {
      await createPromptTemplate(payload);
      notifySuccess("已新增提示词");
    } else if (editingId.value !== null) {
      await updatePromptTemplate(editingId.value, payload);
      notifySuccess("已更新提示词");
    }
    dialogVisible.value = false;
    await reloadDictionary();
    await loadPrompts();
  } catch (error) {
    console.error("Failed to save prompt template", error);
  } finally {
    submitting.value = false;
  }
}

async function confirmDelete(template: AiPromptTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${gameLabel(template.game_type)} - ${roleLabel(template.role_key)} - ${phaseLabel(template.phase_key)}」的提示词吗？`,
      "确认删除",
      {
        type: "warning"
      }
    );
    await deletePromptTemplate(template.id);
    notifySuccess("已删除提示词");
    await reloadDictionary();
    await loadPrompts();
  } catch (error) {
    if (error !== "cancel") {
      console.error("Failed to delete prompt template", error);
    }
  }
}

async function reloadDictionary() {
  try {
    dictionary.value = await fetchPromptDictionary();
    if (dictionary.value.games.length > 0) {
      const exists = dictionary.value.games.some((game) => game.key === filters.game_type);
      if (!exists) {
        filters.game_type = dictionary.value.games[0].key;
      }
    } else {
      filters.game_type = "";
    }
  } catch (error) {
    console.error("Failed to refresh dictionary", error);
  }
}

function gameLabel(key: string) {
  return gameOptions.value.find((item) => item.key === key)?.label ?? key;
}

function roleLabel(key: string) {
  const game = gameOptions.value.find((item) => item.key === filters.game_type) ??
    gameOptions.value.find((item) => item.roles.some((role) => role.key === key));
  const match = game?.roles.find((role) => role.key === key);
  return match?.label ?? key;
}

function phaseLabel(key: string) {
  const game = gameOptions.value.find((item) => item.key === filters.game_type) ??
    gameOptions.value.find((item) => item.phases.some((phase) => phase.key === key));
  const match = game?.phases.find((phase) => phase.key === key);
  return match?.label ?? key;
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
</script>

<style scoped lang="scss">
.prompt-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.prompt-manager__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.prompt-manager__actions {
  display: flex;
  gap: 12px;
}

.prompt-manager__filters {
  border-radius: 12px;
  border: 1px solid rgba(31, 47, 61, 0.08);
}

.prompt-manager__content {
  white-space: pre-wrap;
  line-height: 1.6;
}

.prompt-manager__empty {
  margin-top: 24px;
}

.prompt-manager__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
