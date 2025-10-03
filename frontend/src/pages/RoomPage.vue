<template>
  <section class="room">
    <header class="room__header">
      <div>
        <h2>房间 {{ id }}</h2>
        <p>实时交互和游戏将在下一阶段实现。</p>
      </div>
      <el-tag type="success">准备中</el-tag>
    </header>
    <el-card>
      <p>房间成员、聊天以及 AI 助手功能将在后续步骤逐步完善。</p>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const id = computed(() => route.params.id);
</script>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.room__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
