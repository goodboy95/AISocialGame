<template>
  <div class="game-config">
    <div class="game-config__header">
      <div class="game-config__title">
        <el-button text @click="('back')">返回</el-button>
        <div>
          <h2>游戏配置</h2>
          <p>集中管理平台内各类游戏的配置项。</p>
        </div>
      </div>
    </div>
    <el-tabs v-model="activeGame" class="game-config__tabs">
      <el-tab-pane label="谁是卧底" name="undercover">
        <div class="game-config__section-header">
          <div class="game-config__card-title">谁是卧底配置</div>
          <div class="game-config__card-desc">维护词库与可用的 AI 角色。</div>
        </div>
        <el-tabs v-model="undercoverTab" class="game-config__sub-tabs">
          <el-tab-pane label="词库管理" name="word-bank">
            <undercover-word-bank-manager />
          </el-tab-pane>
          <el-tab-pane label="AI 角色配置" name="ai-roles">
            <undercover-ai-role-manager />
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
      <el-tab-pane label="狼人杀" name="werewolf">
        <div class="game-config__card">
          <el-result icon="info" title="开发中..." sub-title="狼人杀配置模块正在建设中" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="提示词管理" name="prompts">
        <ai-prompt-template-manager />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import UndercoverWordBankManager from './UndercoverWordBankManager.vue';
import UndercoverAiRoleManager from './UndercoverAiRoleManager.vue';
import AiPromptTemplateManager from './AiPromptTemplateManager.vue';

const activeGame = ref('undercover');
const undercoverTab = ref('word-bank');
</script>

<style scoped lang="scss">
.game-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.game-config__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.game-config__title {
  display: flex;
  align-items: center;
  gap: 16px;
}

.game-config__tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}

.game-config__card-title {
  font-size: 18px;
  font-weight: 600;
}

.game-config__card-desc {
  color: #606266;
  margin-top: 4px;
}

.game-config__section-header {
  margin-bottom: 12px;
}

.game-config__card {
  background: #fff;
  border: 1px solid rgba(31, 47, 61, 0.08);
  border-radius: 12px;
  padding: 32px 16px;
}

.game-config__sub-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}
</style>
