<template>
  <section class="lobby">
    <header class="lobby__header">
      <div>
        <h2>游戏大厅</h2>
        <p>在这里浏览可加入的房间或创建自己的房间。</p>
      </div>
      <el-button type="primary" @click="handleCreate">创建房间</el-button>
    </header>
    <el-empty description="房间列表即将上线，敬请期待" v-if="rooms.length === 0" />
    <el-row :gutter="16" v-else>
      <el-col :span="8" v-for="room in rooms" :key="room.code">
        <el-card @click="enterRoom(room)">
          <h3>{{ room.name }}</h3>
          <p>房主：{{ room.owner }}</p>
          <p>房间号：{{ room.code }}</p>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { useRouter } from "vue-router";

interface RoomPreview {
  name: string;
  owner: string;
  code: string;
}

const router = useRouter();
const rooms = reactive<RoomPreview[]>([]);

function handleCreate() {
  console.log("create room placeholder");
}

function enterRoom(room: RoomPreview) {
  router.push({ name: "room-detail", params: { id: room.code } });
}
</script>

<style scoped>
.lobby {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lobby__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.el-card {
  cursor: pointer;
  transition: transform 0.2s ease;
}

.el-card:hover {
  transform: translateY(-4px);
}
</style>
