<template>
  <div class="speech-timeline">
    <h4 v-if="showHeader">{{ title }}</h4>
    <template v-if="speeches.length">
      <el-timeline>
        <el-timeline-item
          v-for="speech in speeches"
          :key="speech.timestamp + '-' + speech.player_id"
          :timestamp="formatTime(speech.timestamp)"
        >
          <strong>{{ resolveName(speech.player_id) }}</strong>：{{ speech.content }}
        </el-timeline-item>
      </el-timeline>
    </template>
    <el-empty v-else :description="emptyText" />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { UndercoverSpeech } from "../types/rooms";

type Props = {
  speeches: UndercoverSpeech[];
  resolveName: (playerId: number) => string;
  formatTime: (timestamp: string) => string;
  title: string;
  emptyText: string;
  showHeader?: boolean;
};

const props = defineProps<Props>();

const showHeader = computed(() => props.showHeader !== false);
const speeches = computed(() => props.speeches ?? []);
const title = computed(() => props.title);
const emptyText = computed(() => props.emptyText);
const resolveName = computed(() => props.resolveName);
const formatTime = computed(() => props.formatTime);
</script>

<style scoped>
.speech-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.speech-timeline :deep(.el-timeline-item__timestamp) {
  color: var(--el-text-color-secondary);
}
</style>
