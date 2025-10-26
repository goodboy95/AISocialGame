<template>
  <div class="word-bank-admin">
    <el-card shadow="never" class="word-bank-admin__card">
      <template #header>
        <div class="card-header">
          <div class="card-header__title">{{ t("admin.wordBank.title") }}</div>
          <div class="card-header__actions">
            <el-button type="primary" @click="openCreateDialog">
              {{ t("admin.wordBank.create") }}
            </el-button>
            <el-button @click="openImportDialog">{{ t("admin.wordBank.bulkImport") }}</el-button>
            <el-button @click="handleExport">{{ t("admin.wordBank.export") }}</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="filters" class="filters" @submit.prevent>
        <el-form-item :label="t('admin.wordBank.filters.topic')">
          <el-input v-model="filters.topic" clearable :placeholder="t('admin.wordBank.placeholders.topic')" />
        </el-form-item>
        <el-form-item :label="t('admin.wordBank.filters.difficulty')">
          <el-select v-model="filters.difficulty" clearable :placeholder="t('admin.wordBank.placeholders.difficulty')">
            <el-option v-for="option in difficultyOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.wordBank.filters.keyword')">
          <el-input v-model="filters.keyword" clearable :placeholder="t('admin.wordBank.placeholders.keyword')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadWordPairs">{{ t("admin.wordBank.actions.search") }}</el-button>
          <el-button @click="resetFilters">{{ t("admin.wordBank.actions.reset") }}</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="wordPairs" v-loading="loading" class="word-table" border>
        <el-table-column prop="topic" :label="t('admin.wordBank.columns.topic')" min-width="120" />
        <el-table-column prop="civilian_word" :label="t('admin.wordBank.columns.civilian')" min-width="140" />
        <el-table-column prop="undercover_word" :label="t('admin.wordBank.columns.undercover')" min-width="140" />
        <el-table-column :label="t('admin.wordBank.columns.difficulty')" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ difficultyLabel(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.wordBank.columns.updatedAt')" min-width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.updated_at) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.wordBank.columns.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openEditDialog(row)">
              {{ t("common.edit") }}
            </el-button>
            <el-button type="danger" text size="small" @click="handleDelete(row)">
              {{ t("common.delete") }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item :label="t('admin.wordBank.columns.topic')">
          <el-input v-model="form.topic" />
        </el-form-item>
        <el-form-item :label="t('admin.wordBank.columns.civilian')" required>
          <el-input v-model="form.civilian_word" />
        </el-form-item>
        <el-form-item :label="t('admin.wordBank.columns.undercover')" required>
          <el-input v-model="form.undercover_word" />
        </el-form-item>
        <el-form-item :label="t('admin.wordBank.columns.difficulty')" required>
          <el-select v-model="form.difficulty">
            <el-option v-for="option in difficultyOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">
          {{ t("common.confirm") }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" :title="t('admin.wordBank.bulkImport')" width="520px" destroy-on-close>
      <p class="import-tip">{{ t("admin.wordBank.importHint") }}</p>
      <el-input
        v-model="importText"
        type="textarea"
        :autosize="{ minRows: 6, maxRows: 12 }"
        :placeholder="t('admin.wordBank.importPlaceholder')"
      />
      <template #footer>
        <el-button @click="importDialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" @click="handleImport" :loading="importLoading">
          {{ t("admin.wordBank.actions.import") }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";

import {
  createWordPair,
  deleteWordPair,
  exportWordPairs,
  fetchWordPairs,
  importWordPairs,
  updateWordPair,
  type WordPairQuery,
} from "../api/wordPairs";
import type { WordPair, WordPairPayload } from "../types/word-pairs";
import { notifyError, notifySuccess } from "../services/notifications";

const { t } = useI18n();

const loading = ref(false);
const submitLoading = ref(false);
const importLoading = ref(false);
const dialogVisible = ref(false);
const importDialogVisible = ref(false);
const isEditing = ref(false);
const editingId = ref<number | null>(null);
const wordPairs = ref<WordPair[]>([]);
const importText = ref("");

const filters = reactive({
  topic: "",
  difficulty: "",
  keyword: "",
});

const form = reactive<WordPairPayload>({
  topic: "",
  civilian_word: "",
  undercover_word: "",
  difficulty: "easy",
});

const allowedDifficulties = ["easy", "medium", "hard"] as const;

const difficultyOptions = computed(() => [
  { value: "easy", label: t("admin.wordBank.difficulties.easy") },
  { value: "medium", label: t("admin.wordBank.difficulties.medium") },
  { value: "hard", label: t("admin.wordBank.difficulties.hard") },
]);

const dialogTitle = computed(() =>
  isEditing.value ? t("admin.wordBank.editTitle") : t("admin.wordBank.createTitle"),
);

function resetForm() {
  form.topic = "";
  form.civilian_word = "";
  form.undercover_word = "";
  form.difficulty = "easy";
}

function resetFilters() {
  filters.topic = "";
  filters.difficulty = "";
  filters.keyword = "";
  loadWordPairs();
}

function difficultyLabel(value: string) {
  const option = difficultyOptions.value.find((item) => item.value === value);
  return option ? option.label : value;
}

function formatTimestamp(input: string) {
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(
    date.getMinutes(),
  ).padStart(2, "0")}`;
}

async function loadWordPairs() {
  loading.value = true;
  try {
    const params: WordPairQuery = {};
    if (filters.topic) {
      params.topic = filters.topic;
    }
    if (filters.difficulty) {
      params.difficulty = filters.difficulty;
    }
    if (filters.keyword) {
      params.q = filters.keyword;
    }
    wordPairs.value = await fetchWordPairs(params);
  } catch (error) {
    console.error("Failed to load word pairs", error);
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  resetForm();
  isEditing.value = false;
  editingId.value = null;
  dialogVisible.value = true;
}

function openEditDialog(item: WordPair) {
  form.topic = item.topic;
  form.civilian_word = item.civilian_word;
  form.undercover_word = item.undercover_word;
  form.difficulty = item.difficulty;
  isEditing.value = true;
  editingId.value = item.id;
  dialogVisible.value = true;
}

async function submitForm() {
  submitLoading.value = true;
  try {
    if (!form.civilian_word || !form.undercover_word) {
      notifyError(t("admin.wordBank.messages.missingWords"));
      return;
    }
    if (form.civilian_word === form.undercover_word) {
      notifyError(t("admin.wordBank.messages.duplicated"));
      return;
    }

    const payload: WordPairPayload = {
      topic: form.topic,
      civilian_word: form.civilian_word,
      undercover_word: form.undercover_word,
      difficulty: form.difficulty,
    };

    if (isEditing.value && editingId.value) {
      await updateWordPair(editingId.value, payload);
      notifySuccess(t("admin.wordBank.messages.updated"));
    } else {
      await createWordPair(payload);
      notifySuccess(t("admin.wordBank.messages.created"));
    }
    dialogVisible.value = false;
    await loadWordPairs();
  } catch (error) {
    console.error("Failed to submit word pair", error);
  } finally {
    submitLoading.value = false;
  }
}

async function handleDelete(item: WordPair) {
  try {
    await deleteWordPair(item.id);
    notifySuccess(t("admin.wordBank.messages.deleted"));
    await loadWordPairs();
  } catch (error) {
    console.error("Failed to delete word pair", error);
  }
}

function openImportDialog() {
  importText.value = "";
  importDialogVisible.value = true;
}

function parseImportEntries() {
  const lines = importText.value
    .split(/\n|;/)
    .map((line) => line.trim())
    .filter(Boolean);
  const payload: WordPairPayload[] = [];
  for (const line of lines) {
    const parts = line.split(/,|\t/).map((part) => part.trim()).filter(Boolean);
    if (parts.length < 2) {
      throw new Error(t("admin.wordBank.messages.importFormatError"));
    }
    const [civilian, undercover, topic = "", difficulty = "easy"] = parts;
    if (!civilian || !undercover) {
      throw new Error(t("admin.wordBank.messages.importFormatError"));
    }
    const normalizedDifficulty = String(difficulty || "easy").toLowerCase();
    const finalDifficulty = allowedDifficulties.includes(
      normalizedDifficulty as (typeof allowedDifficulties)[number],
    )
      ? (normalizedDifficulty as WordPairPayload["difficulty"])
      : "easy";
    payload.push({
      civilian_word: civilian,
      undercover_word: undercover,
      topic,
      difficulty: finalDifficulty,
    });
  }
  return payload;
}

async function handleImport() {
  importLoading.value = true;
  try {
    const items = parseImportEntries();
    if (items.length === 0) {
      notifyError(t("admin.wordBank.messages.importEmpty"));
      return;
    }
    await importWordPairs({ items });
    notifySuccess(t("admin.wordBank.messages.imported", { count: items.length }));
    importDialogVisible.value = false;
    await loadWordPairs();
  } catch (error) {
    console.error("Failed to import word pairs", error);
    notifyError(error instanceof Error ? error.message : t("admin.wordBank.messages.importFailed"));
  } finally {
    importLoading.value = false;
  }
}

async function handleExport() {
  try {
    const params: WordPairQuery = {};
    if (filters.topic) {
      params.topic = filters.topic;
    }
    if (filters.difficulty) {
      params.difficulty = filters.difficulty;
    }
    if (filters.keyword) {
      params.q = filters.keyword;
    }
    const { items } = await exportWordPairs(params);
    const blob = new Blob([JSON.stringify(items, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `word-bank-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
    notifySuccess(t("admin.wordBank.messages.exported", { count: items.length }));
  } catch (error) {
    console.error("Failed to export word pairs", error);
  }
}

onMounted(() => {
  loadWordPairs();
});
</script>

<style scoped lang="scss">
.word-bank-admin {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.word-bank-admin__card {
  border: 1px solid rgba(31, 47, 61, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.card-header__title {
  font-size: 18px;
  font-weight: 600;
}

.card-header__actions {
  display: flex;
  gap: 12px;
}

.filters {
  margin-bottom: 16px;
}

.word-table {
  width: 100%;
}

.import-tip {
  margin-bottom: 12px;
  color: #606266;
  line-height: 1.5;
}
</style>
