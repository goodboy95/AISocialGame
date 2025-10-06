<template>
  <teleport to="body">
    <div class="global-notifications" v-if="items.length">
      <transition-group name="slide-down" tag="div">
        <div
          v-for="item in items"
          :key="item.id"
          class="global-notification"
          :class="`global-notification--${item.type}`"
        >
          <span class="global-notification__message">{{ item.message }}</span>
          <button class="global-notification__close" type="button" @click="close(item.id)">
            ×
          </button>
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";

import { useNotificationStore } from "../store/notifications";

const store = useNotificationStore();
const { items } = storeToRefs(store);

function close(id: string) {
  store.remove(id);
}
</script>

<style scoped lang="scss">
.global-notifications {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(480px, calc(100vw - 32px));
}

.global-notification {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(31, 47, 61, 0.12);
  background-color: #ffffff;
  border: 1px solid rgba(31, 47, 61, 0.08);
  color: #1f2f3d;
  font-size: 14px;
}

.global-notification__message {
  flex: 1;
  line-height: 1.5;
}

.global-notification__close {
  appearance: none;
  border: none;
  background: transparent;
  color: inherit;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.global-notification--error {
  border-color: rgba(220, 38, 38, 0.45);
  background-color: rgba(254, 242, 242, 0.95);
  color: #991b1b;
}

.global-notification--warning {
  border-color: rgba(234, 179, 8, 0.45);
  background-color: rgba(254, 249, 195, 0.95);
  color: #854d0e;
}

.global-notification--success {
  border-color: rgba(34, 197, 94, 0.45);
  background-color: rgba(220, 252, 231, 0.95);
  color: #166534;
}

.global-notification--info {
  border-color: rgba(14, 165, 233, 0.45);
  background-color: rgba(224, 242, 254, 0.95);
  color: #075985;
}

.slide-down-enter-from {
  opacity: 0;
  transform: translate(-50%, -20px);
}

.slide-down-enter-to {
  opacity: 1;
  transform: translate(-50%, 0);
}

.slide-down-leave-from {
  opacity: 1;
  transform: translate(-50%, 0);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translate(-50%, -20px);
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s ease;
}
</style>
