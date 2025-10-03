<template>
  <section class="room" v-if="room">
    <header class="room__header">
      <div>
        <h2>{{ room.name }}</h2>
        <p>
          {{ t("room.ui.roomCode") }}: <strong>{{ room.code }}</strong>
          · {{ t("room.ui.owner") }}: {{ room.owner.displayName }}
        </p>
      </div>
      <div class="room__actions">
        <el-tag :type="socketConnected ? 'success' : 'info'">
          {{ socketConnected ? t("room.ui.online") : t("room.ui.offline") }}
        </el-tag>
        <el-tag type="warning">{{ phaseDisplay }}</el-tag>
        <el-button
          v-if="room.status === 'waiting' && room.isOwner"
          type="primary"
          @click="handleStart"
        >
          {{ t("room.ui.startGame") }}
        </el-button>
        <el-button @click="handleLeave">{{ t("room.ui.leaveRoom") }}</el-button>
      </div>
    </header>
    <el-row :gutter="16" class="room__layout">
      <el-col :span="6" class="room__sidebar">
        <el-card class="room__panel room__panel--info">
          <template #header>
            <span>{{ t("room.ui.myIdentity") }}</span>
          </template>
          <p>{{ t("room.ui.role") }}: <strong>{{ selfRoleDisplay }}</strong></p>
          <template v-if="isUndercover">
            <p>{{ t("room.ui.word") }}: <strong>{{ selfWordDisplay }}</strong></p>
            <p v-if="undercoverState?.word_pair?.topic">
              {{ t("room.ui.topic") }}: {{ undercoverState.word_pair.topic }}
            </p>
          </template>
          <template v-else-if="isWerewolf">
            <p v-if="werewolfPrivateRoleName">
              {{ t("room.ui.factionHint") }}: <strong>{{ werewolfPrivateRoleName }}</strong>
            </p>
            <p v-if="isWolf && werewolfAllyNames">{{ t("room.ui.allies") }}: {{ werewolfAllyNames }}</p>
            <p v-if="isSeerRole && seerLastResult">{{ t("room.ui.lastInspection") }}: {{ seerLastResult }}</p>
            <p v-if="isWitchRole">
              {{ t("room.ui.antidote") }}: {{ werewolfAntidoteAvailable ? t("room.ui.available") : t("room.ui.exhausted") }}
              · {{ t("room.ui.poison") }}:
              {{ werewolfPoisonAvailable ? t("room.ui.available") : t("room.ui.exhausted") }}
            </p>
            <p v-if="werewolfPendingKillName">{{ t("room.ui.pendingTarget") }}: {{ werewolfPendingKillName }}</p>
          </template>
          <el-alert v-if="winnerDescription" :title="winnerDescription" type="success" show-icon />
        </el-card>
        <el-card class="room__panel room__panel--players">
          <template #header>
            <div class="room__panel-header">
              <span>{{ t("room.ui.members") }}</span>
              <span>{{ t("room.ui.roundLabel", { round: gameSession?.round ?? room.currentRound }) }}</span>
            </div>
          </template>
          <ul class="room__members">
            <li
              v-for="player in room.players"
              :key="player.id"
              :class="['room__member', { 'room__member-host': player.isHost, 'room__member-current': activeSpeakerId === player.id }]"
            >
              <div class="room__member-name">
                <el-icon v-if="player.isHost" class="room__member-host-icon"><Trophy /></el-icon>
                <span>{{ player.displayName }}</span>
                <el-tag v-if="player.isAi" size="small" type="info">AI</el-tag>
              </div>
              <div class="room__member-meta">
                <el-tag size="small" :type="playerStatusMap.get(player.id)?.isAlive ? 'success' : 'danger'">
                  {{ playerStatusMap.get(player.id)?.isAlive ? t("room.ui.alive") : t("room.ui.eliminated") }}
                </el-tag>
                <small>{{ t("room.ui.seat") }} {{ player.seatNumber }}</small>
              </div>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="10" class="room__game">
        <el-card class="room__panel room__panel--stage">
          <template #header>
            <div class="room__panel-header">
              <span>{{ t("room.ui.phase") }}</span>
              <el-tag type="info">{{ phaseDisplay }}</el-tag>
            </div>
          </template>
          <p class="room__phase-desc">{{ phaseDescription }}</p>
          <div v-if="gameState?.winner" class="room__winner">
            <el-result :icon="winnerIcon" :title="winnerTitle">
              <template #sub-title>
                <span>{{ winnerDescription }}</span>
              </template>
            </el-result>
          </div>
          <template v-else>
            <template v-if="isUndercover">
              <div v-if="currentPhase === 'preparing'" class="room__phase-block">
                <el-button v-if="room.isOwner" type="primary" @click="handleReady">
                  {{ t("room.ui.notifyStart") }}
                </el-button>
                <el-alert v-else :title="t('room.ui.waitHost')" type="info" show-icon />
              </div>
              <div v-else-if="currentPhase === 'speaking'" class="room__phase-block">
                <p>
                  {{ t("room.ui.currentSpeaker") }}:
                  <strong>{{ currentSpeakerName }}</strong>
                </p>
                <div v-if="canSpeak" class="room__speak-form">
                  <el-input
                    v-model="speechInput"
                    type="textarea"
                    :rows="3"
                    maxlength="120"
                    show-word-limit
                    :placeholder="t('room.ui.speakPlaceholder')"
                  />
                  <el-button type="primary" :disabled="!speechInput.trim()" @click="handleSubmitSpeech">
                    {{ t("room.ui.submitSpeech") }}
                  </el-button>
                </div>
                <el-alert v-else :title="t('room.ui.waitSpeech')" type="info" show-icon />
              </div>
              <div v-else-if="currentPhase === 'voting'" class="room__phase-block">
                <p>{{ t("room.ui.selectSuspect") }}</p>
                <div class="room__vote-grid">
                  <el-button
                    v-for="assignment in aliveAssignments"
                    :key="assignment.playerId"
                    :type="voteTarget === assignment.playerId ? 'primary' : 'default'"
                    :disabled="hasVoted || assignment.playerId === selfPlayer?.id"
                    @click="handleVote(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <p class="room__vote-summary">
                  {{ t("room.ui.voteProgress") }}
                  {{ (gameState as any)?.voteSummary?.submitted ?? 0 }} /
                  {{ (gameState as any)?.voteSummary?.required ?? aliveAssignments.length }}
                </p>
              </div>
              <div v-else-if="currentPhase === 'result'" class="room__phase-block">
                <el-alert :title="t('room.ui.roundCompleted')" type="success" show-icon />
              </div>
            </template>
            <template v-else-if="isWerewolf">
              <div v-if="werewolfStage === 'night.wolves'" class="room__phase-block">
                <p>{{ t("room.ui.wolfInstruction") }}</p>
                <div v-if="isWolf">
                  <el-button
                    v-for="assignment in werewolfKillOptions"
                    :key="assignment.playerId"
                    :type="werewolfPendingKill === assignment.playerId ? 'primary' : 'default'"
                    @click="handleWolfTarget(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                  <p class="room__phase-tip">
                    {{ t("room.ui.currentSelection") }}:
                    {{ werewolfPendingKillName || t("room.ui.none") }}
                  </p>
                </div>
                <el-alert v-else :title="t('room.ui.waitWolf')" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'night.seer'" class="room__phase-block">
                <p>{{ t("room.ui.seerInstruction") }}</p>
                <div v-if="isSeerRole">
                  <el-button
                    v-for="assignment in werewolfInspectOptions"
                    :key="assignment.playerId"
                    @click="handleSeerTarget(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <el-alert v-else :title="t('room.ui.waitSeer')" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'night.witch'" class="room__phase-block">
                <p>{{ t("room.ui.witchInstruction") }}</p>
                <div v-if="isWitchRole">
                  <div class="room__witch-actions">
                    <el-button
                      :disabled="!werewolfAntidoteAvailable || !werewolfPendingKill"
                      type="primary"
                      @click="handleWitchSave"
                    >
                      {{
                        t("room.ui.antidoteAction", {
                          target: werewolfPendingKillName || t("room.ui.none"),
                        })
                      }}
                    </el-button>
                    <div class="room__witch-poison">
                      <el-select
                        v-model="witchPoisonTarget"
                        :placeholder="t('room.ui.poisonPlaceholder')"
                        :disabled="!werewolfPoisonAvailable || werewolfPoisonOptions.length === 0"
                        style="width: 160px"
                      >
                        <el-option
                          v-for="assignment in werewolfPoisonOptions"
                          :key="assignment.playerId"
                          :label="assignment.displayName"
                          :value="assignment.playerId"
                        />
                      </el-select>
                      <el-button
                        type="danger"
                        :disabled="!werewolfPoisonAvailable || !witchPoisonTarget"
                        @click="handleWitchPoison"
                      >
                        {{ t("room.ui.poisonAction") }}
                      </el-button>
                    </div>
                  </div>
                  <el-button type="info" plain @click="handleWitchSkip">{{ t("room.ui.skip") }}</el-button>
                </div>
                <el-alert v-else :title="t('room.ui.waitWitch')" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'day.discussion'" class="room__phase-block">
                <p>
                  {{ t("room.ui.currentSpeaker") }}:
                  <strong>{{ currentSpeakerName }}</strong>
                </p>
                <div v-if="canSpeak" class="room__speak-form">
                  <el-input
                    v-model="speechInput"
                    type="textarea"
                    :rows="3"
                    maxlength="160"
                    show-word-limit
                    :placeholder="t('room.ui.daySpeakPlaceholder')"
                  />
                  <el-button type="primary" :disabled="!speechInput.trim()" @click="handleSubmitSpeech">
                    {{ t("room.ui.submitSpeech") }}
                  </el-button>
                </div>
                <el-alert v-else :title="t('room.ui.waitSpeech')" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'day.vote'" class="room__phase-block">
                <p>{{ t("room.ui.voteInstruction") }}</p>
                <div class="room__vote-grid">
                  <el-button
                    v-for="assignment in aliveAssignments"
                    :key="assignment.playerId"
                    :type="voteTarget === assignment.playerId ? 'primary' : 'default'"
                    :disabled="hasVoted || assignment.playerId === selfPlayer?.id"
                    @click="handleVote(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <p class="room__vote-summary">
                  {{ t("room.ui.voteProgress") }}
                  {{ werewolfState?.voteSummary?.submitted ?? 0 }} /
                  {{ werewolfState?.voteSummary?.required ?? aliveAssignments.length }}
                </p>
              </div>
              <div v-else class="room__phase-block">
                <el-alert :title="t('room.ui.waitNextStage')" type="info" show-icon />
              </div>
            </template>
          </template>
          <div v-if="speeches.length" class="room__speeches">
            <h4>{{ t("room.ui.speechLog") }}</h4>
            <el-timeline>
              <el-timeline-item
                v-for="speech in speeches"
                :key="speech.timestamp + '-' + speech.player_id"
                :timestamp="formatTime(speech.timestamp)"
              >
                <strong>{{ resolvePlayerName(speech.player_id) }}</strong>：{{ speech.content }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="room__chat">
          <template #header>
            <div class="room__panel-header">
              <span>{{ t("room.chatTitle") }}</span>
            </div>
          </template>
          <div v-if="room?.isOwner" class="room__chat-tools">
            <el-form inline :model="aiForm" class="room__chat-form">
              <el-form-item :label="t('room.aiStyle')">
                <el-select v-model="aiForm.style" :placeholder="t('room.selectStyle')" clearable size="small">
                  <el-option v-for="style in aiStyles" :key="style.key" :label="style.label" :value="style.key" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="aiForm.displayName"
                  size="small"
                  :placeholder="t('room.addAi')"
                />
              </el-form-item>
              <el-form-item>
                <el-button size="small" type="success" :loading="addingAi" @click="handleAddAi">
                  {{ t("room.addAi") }}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
          <div ref="chatContainer" class="room__chat-history">
            <div
              v-for="message in messages"
              :key="message.id"
              :class="['room__chat-message', `room__chat-message--${message.type}`]"
            >
              <template v-if="message.type === 'chat'">
                <div class="room__chat-avatar">
                  {{ message.sender?.displayName?.slice(0, 1) ?? '?' }}
                </div>
                <div class="room__chat-bubble">
                  <div class="room__chat-meta">
                    <span class="room__chat-name">{{ message.sender?.displayName }}</span>
                    <span class="room__chat-time">{{ formatTime(message.timestamp) }}</span>
                  </div>
                  <p class="room__chat-text">{{ message.content }}</p>
                </div>
              </template>
              <template v-else>
                <div class="room__chat-system">
                  <span>{{ translateSystemMessage(message) }}</span>
                  <span class="room__chat-time">{{ formatTime(message.timestamp) }}</span>
                </div>
              </template>
            </div>
          </div>
          <div class="room__chat-input">
            <el-input
              v-model="messageInput"
              type="textarea"
              :rows="2"
              :placeholder="t('room.chatPlaceholder')"
              @keyup.enter.exact.prevent="handleSend"
            />
            <el-button type="primary" :disabled="!messageInput.trim()" @click="handleSend">
              {{ t("room.send") }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
  <el-empty :description="t('room.ui.loadingRoom')" v-else />
</template>

<script setup lang="ts">
import { Trophy } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useI18n } from "vue-i18n";

import type {
  ChatMessage,
  UndercoverAssignmentView,
  UndercoverStateView,
  WerewolfAssignmentView,
  WerewolfPrivateInfo,
  WerewolfStateView
} from "../types/rooms";
import { useRoomsStore } from "../store/rooms";
import { useAuthStore } from "../store/user";
import { useMetaStore } from "../store/meta";

const route = useRoute();
const router = useRouter();
const roomsStore = useRoomsStore();
const authStore = useAuthStore();
const metaStore = useMetaStore();
const { t } = useI18n();

const { currentRoom, messages, socketConnected } = storeToRefs(roomsStore);
const messageInput = ref("");
const chatContainer = ref<HTMLDivElement | null>(null);
const speechInput = ref("");
const witchPoisonTarget = ref<number | null>(null);
const aiForm = reactive({ style: "", displayName: "" });
const addingAi = ref(false);

const room = computed(() => currentRoom.value);
const gameSession = computed(() => room.value?.gameSession ?? null);
const gameEngine = computed(() => gameSession.value?.engine ?? "undercover");
const isUndercover = computed(() => gameEngine.value === "undercover");
const isWerewolf = computed(() => gameEngine.value === "werewolf");

const undercoverState = computed<UndercoverStateView | null>(() =>
  isUndercover.value ? ((gameSession.value?.state as UndercoverStateView | undefined) ?? null) : null
);
const werewolfState = computed<WerewolfStateView | null>(() =>
  isWerewolf.value ? ((gameSession.value?.state as WerewolfStateView | undefined) ?? null) : null
);
const gameState = computed(() => undercoverState.value ?? werewolfState.value ?? null);

const currentPhase = computed(() => gameState.value?.phase ?? "preparing");
const werewolfStage = computed(() => (isWerewolf.value ? werewolfState.value?.stage ?? "" : ""));
const activeSpeakerId = computed(() =>
  (isUndercover.value ? undercoverState.value?.current_player_id : werewolfState.value?.current_player_id) ?? null
);

const profile = computed(() => authStore.profile);
const selfPlayer = computed(() =>
  room.value?.players.find((player) => player.userId && player.userId === profile.value?.id) ?? null
);

const assignments = computed<(UndercoverAssignmentView | WerewolfAssignmentView)[]>(() => {
  if (isUndercover.value) {
    return undercoverState.value?.assignments ?? [];
  }
  if (isWerewolf.value) {
    return werewolfState.value?.assignments ?? [];
  }
  return [];
});

const playerStatusMap = computed(() => {
  const map = new Map<number, { isAlive: boolean }>();
  assignments.value.forEach((assignment) => {
    map.set(assignment.playerId, { isAlive: assignment.isAlive });
  });
  return map;
});

const aliveAssignments = computed(() => assignments.value.filter((assignment) => assignment.isAlive));
const speeches = computed(() => (gameState.value && Array.isArray((gameState.value as any).speeches) ? (gameState.value as any).speeches : []));
const hasVoted = computed(() => Boolean((gameState.value as any)?.voteSummary?.selfTarget));
const voteTarget = computed(() => (gameState.value as any)?.voteSummary?.selfTarget ?? null);
const aiStyles = computed(() => metaStore.aiStyles);

const phaseMap = computed(() => ({
  preparing: t("room.phases.preparing"),
  speaking: t("room.phases.speaking"),
  voting: t("room.phases.voting"),
  result: t("room.phases.result"),
  ended: t("room.phases.ended"),
  night: t("room.phases.night"),
  day: t("room.phases.day"),
}));

const werewolfStageMap = computed(() => ({
  "night.wolves": t("room.werewolfStages.nightWolves"),
  "night.seer": t("room.werewolfStages.nightSeer"),
  "night.witch": t("room.werewolfStages.nightWitch"),
  "day.discussion": t("room.werewolfStages.dayDiscussion"),
  "day.vote": t("room.werewolfStages.dayVote"),
  "day.result": t("room.werewolfStages.dayResult"),
}));

const phaseDisplay = computed(() => {
  if (room.value?.status === "waiting") {
    return t("room.waiting");
  }
  if (isUndercover.value) {
    return phaseMap.value[currentPhase.value] ?? t("room.inProgress");
  }
  if (isWerewolf.value) {
    return (
      werewolfStageMap.value[werewolfStage.value] ??
      (phaseMap.value[currentPhase.value] ?? t("room.inProgress"))
    );
  }
  return t("room.notStarted");
});

const phaseDescription = computed(() => {
  if (isUndercover.value) {
    switch (currentPhase.value) {
      case "speaking":
        return t("room.descriptions.undercover.speaking");
      case "voting":
        return t("room.descriptions.undercover.voting");
      case "result":
        return t("room.descriptions.undercover.result");
      case "preparing":
        return t("room.descriptions.undercover.preparing");
      default:
        return t("room.descriptions.generic");
    }
  }
  if (isWerewolf.value) {
    switch (werewolfStage.value) {
      case "night.wolves":
        return isWolf.value
          ? t("room.descriptions.werewolf.wolvesSelf")
          : t("room.descriptions.werewolf.wolvesOthers");
      case "night.seer":
        return isSeerRole.value
          ? t("room.descriptions.werewolf.seerSelf")
          : t("room.descriptions.werewolf.seerOthers");
      case "night.witch":
        return isWitchRole.value
          ? t("room.descriptions.werewolf.witchSelf")
          : t("room.descriptions.werewolf.witchOthers");
      case "day.discussion":
        return t("room.descriptions.werewolf.dayDiscussion");
      case "day.vote":
        return t("room.descriptions.werewolf.dayVote");
      default:
        return currentPhase.value === "night"
          ? t("room.descriptions.werewolf.nightFallback")
          : t("room.descriptions.werewolf.dayFallback");
    }
  }
  return t("room.descriptions.generic");
});

const currentSpeakerName = computed(() => {
  if (!activeSpeakerId.value) {
    return t("room.ui.pendingSpeaker");
  }
  const found = assignments.value.find((item) => item.playerId === activeSpeakerId.value);
  return (
    found?.displayName ??
    t("room.ui.playerFallback", { id: activeSpeakerId.value })
  );
});

const isSpeakingStage = computed(() =>
  (isUndercover.value && currentPhase.value === "speaking") ||
  (isWerewolf.value && werewolfStage.value === "day.discussion")
);

const canSpeak = computed(() =>
  isSpeakingStage.value && selfPlayer.value && selfPlayer.value.id === activeSpeakerId.value
);

const selfAssignment = computed(() =>
  assignments.value.find((assignment) => assignment.playerId === selfPlayer.value?.id) ?? null
);

function translateRole(role?: string | null) {
  if (!role) {
    return "";
  }
  const key = `room.roles.${role}`;
  const translated = t(key);
  return translated === key ? role : translated;
}

const selfRoleDisplay = computed(() => {
  if (!selfAssignment.value) {
    return t("room.roles.unknown");
  }
  if (!selfAssignment.value.role) {
    return t("room.roles.secret");
  }
  return translateRole(selfAssignment.value.role);
});

const selfWordDisplay = computed(() =>
  isUndercover.value
    ? (selfAssignment.value as UndercoverAssignmentView | null)?.word ?? t("room.word.waiting")
    : t("room.word.placeholder")
);

const werewolfPrivate = computed<WerewolfPrivateInfo>(() => (werewolfState.value?.private as WerewolfPrivateInfo) ?? { role: null });
const werewolfPrivateRoleName = computed(() => {
  const role = werewolfPrivate.value.role;
  if (!role) return "";
  return translateRole(role);
});
const werewolfAllies = computed(() => werewolfPrivate.value.wolves?.allies ?? []);
const werewolfOtherWolves = computed(() =>
  werewolfAllies.value.filter((ally) => ally.playerId !== selfPlayer.value?.id)
);
const werewolfAllyNames = computed(() => werewolfOtherWolves.value.map((ally) => ally.displayName).join("、"));
const werewolfPendingKill = computed(() => werewolfPrivate.value.wolves?.selectedTarget ?? null);
const werewolfPendingKillName = computed(() => (werewolfPendingKill.value ? resolvePlayerName(werewolfPendingKill.value) : ""));
const werewolfAntidoteAvailable = computed(() => Boolean(werewolfPrivate.value.witch?.antidoteAvailable));
const werewolfPoisonAvailable = computed(() => Boolean(werewolfPrivate.value.witch?.poisonAvailable));
const werewolfKillOptions = computed(() => {
  const wolfIds = new Set(werewolfAllies.value.map((ally) => ally.playerId));
  return aliveAssignments.value.filter((assignment) => !wolfIds.has(assignment.playerId));
});
const werewolfInspectOptions = computed(() =>
  aliveAssignments.value.filter((assignment) => assignment.playerId !== selfPlayer.value?.id)
);
const werewolfPoisonOptions = computed(() =>
  aliveAssignments.value.filter((assignment) => assignment.playerId !== selfPlayer.value?.id)
);
const seerLastResult = computed(() => {
  if (!werewolfPrivate.value.seer?.lastResult) {
    return "";
  }
  const raw = werewolfPrivate.value.seer.lastResult as Record<string, any>;
  const playerId = raw.player_id ?? raw.playerId;
  const role = raw.role ?? "unknown";
  const name = playerId ? resolvePlayerName(playerId) : t("room.roles.unknown");
  return `${name} → ${translateRole(role) || t("room.roles.unknown")}`;
});

const isWolf = computed(() => werewolfPrivate.value.role === "werewolf");
const isSeerRole = computed(() => werewolfPrivate.value.role === "seer");
const isWitchRole = computed(() => werewolfPrivate.value.role === "witch");

const winnerIcon = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "info";
  if (isUndercover.value) {
    return winner === "civilian" ? "success" : "warning";
  }
  if (isWerewolf.value) {
    return winner === "villager" ? "success" : "warning";
  }
  return "info";
});

const winnerTitle = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "";
  if (isUndercover.value) {
    return winner === "civilian"
      ? t("room.winner.undercover.civilian")
      : t("room.winner.undercover.undercover");
  }
  if (isWerewolf.value) {
    return winner === "villager"
      ? t("room.winner.werewolf.villager")
      : t("room.winner.werewolf.werewolf");
  }
  return "";
});

const winnerDescription = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "";
  if (isUndercover.value) {
    return winner === "civilian"
      ? t("room.winnerDescriptions.undercover.civilian")
      : t("room.winnerDescriptions.undercover.undercover");
  }
  if (isWerewolf.value) {
    return winner === "villager"
      ? t("room.winnerDescriptions.werewolf.villager")
      : t("room.winnerDescriptions.werewolf.werewolf");
  }
  return "";
});

onMounted(async () => {
  const roomId = Number(route.params.id);
  if (Number.isNaN(roomId)) {
    ElMessage.error(t("room.messages.invalidRoom"));
    router.push({ name: "lobby" });
    return;
  }
  if (!authStore.accessToken) {
    ElMessage.warning(t("room.messages.loginRequired"));
    router.push({ name: "login" });
    return;
  }
  try {
    await metaStore.loadAiStyles();
    const detail = await roomsStore.loadRoomDetail(roomId);
    if (!detail.isMember) {
      await roomsStore.joinRoom(roomId);
    }
    roomsStore.resetMessages();
    roomsStore.connectSocket(roomId);
  } catch (error) {
    console.error(error);
    ElMessage.error(t("room.messages.joinFailed"));
    router.push({ name: "lobby" });
  }
});

onBeforeUnmount(() => {
  roomsStore.disconnectSocket();
  roomsStore.resetMessages();
});

watch(
  messages,
  async () => {
    await nextTick();
    const container = chatContainer.value;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  },
  { deep: true }
);

watch(isSpeakingStage, (speaking) => {
  if (!speaking) {
    speechInput.value = "";
  }
});

watch(werewolfStage, (stage) => {
  if (stage !== "night.witch") {
    witchPoisonTarget.value = null;
  }
});

function formatTime(timestamp: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(timestamp));
}

function translateSystemMessage(message: ChatMessage) {
  const actorName = message.sender?.displayName ?? t("room.messages.unknownPlayer");
  switch (message.event) {
    case "room_started":
      return t("room.systemJoined");
    case "player_joined":
      return t("room.messages.playerJoined", { name: actorName });
    case "player_left":
      return t("room.messages.playerLeft", { name: actorName });
    case "room_dissolved":
      return t("room.messages.roomDissolved");
    case "ai_player_added": {
      const aiContext = (message.context as any)?.aiPlayer;
      if (aiContext?.displayName) {
        return t("room.messages.aiAddedDetailed", {
          host: actorName,
          name: aiContext.displayName,
          style: aiContext.styleLabel ?? aiContext.style ?? "",
        });
      }
      return t("room.messages.aiAdded");
    }
    default:
      return message.content;
  }
}

async function handleSend() {
  const content = messageInput.value.trim();
  if (!content) {
    return;
  }
  roomsStore.sendChat(content);
  messageInput.value = "";
}

async function handleAddAi() {
  if (!room.value) {
    return;
  }
  addingAi.value = true;
  try {
    await roomsStore.addAiPlayer(room.value.id, {
      style: aiForm.style || undefined,
      displayName: aiForm.displayName.trim() || undefined,
    });
    ElMessage.success(t("room.messages.aiAdded"));
    aiForm.displayName = "";
  } catch (error) {
    console.error(error);
    ElMessage.error(t("room.messages.addAiFailed"));
  } finally {
    addingAi.value = false;
  }
}

async function handleLeave() {
  if (!room.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(t("room.messages.leaveConfirm"), t("room.chatTitle"), {
      confirmButtonText: t("common.confirm"),
      cancelButtonText: t("common.cancel"),
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await roomsStore.leaveRoom(room.value.id);
    roomsStore.disconnectSocket();
    router.push({ name: "lobby" });
    ElMessage.success(t("room.messages.leaveSuccess"));
  } catch (error) {
    console.error(error);
    ElMessage.error(t("room.messages.leaveFailed"));
  }
}

async function handleStart() {
  if (!room.value) {
    return;
  }
  try {
    await roomsStore.startRoom(room.value.id);
    ElMessage.success(t("room.messages.startSuccess"));
  } catch (error) {
    console.error(error);
    ElMessage.error(t("room.messages.startFailed"));
  }
}

function handleReady() {
  roomsStore.sendGameEvent("ready");
}

function handleSubmitSpeech() {
  const content = speechInput.value.trim();
  if (!content) {
    ElMessage.warning(t("room.messages.speechRequired"));
    return;
  }
  roomsStore.sendGameEvent("submit_speech", { content });
  speechInput.value = "";
}

function handleVote(targetId: number) {
  if (hasVoted.value) {
    ElMessage.info(t("room.messages.voteDone"));
    return;
  }
  roomsStore.sendGameEvent("submit_vote", { target_id: targetId });
}

function handleWolfTarget(targetId: number) {
  roomsStore.sendGameEvent("submit_wolf_target", { target_id: targetId });
}

function handleSeerTarget(targetId: number) {
  roomsStore.sendGameEvent("submit_seer_target", { target_id: targetId });
}

function handleWitchSave() {
  if (!werewolfPendingKill.value) {
    ElMessage.info(t("room.ui.rescueNone"));
    return;
  }
  roomsStore.sendGameEvent("submit_witch_action", { use_antidote: true });
}

function handleWitchPoison() {
  if (!witchPoisonTarget.value) {
    ElMessage.warning(t("room.ui.needPoisonTarget"));
    return;
  }
  roomsStore.sendGameEvent("submit_witch_action", { use_poison: true, target_id: witchPoisonTarget.value });
  witchPoisonTarget.value = null;
}

function handleWitchSkip() {
  roomsStore.sendGameEvent("submit_witch_action", {});
}

function resolvePlayerName(playerId: number) {
  const assignment = assignments.value.find((item) => item.playerId === playerId);
  if (assignment) {
    return assignment.displayName;
  }
  const fallback = room.value?.players.find((player) => player.id === playerId);
  return (
    fallback?.displayName ??
    t("room.ui.playerFallback", { id: playerId })
  );
}
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
  background: var(--el-color-white);
  padding: 16px;
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
}

.room__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.room__layout {
  width: 100%;
}

.room__panel {
  margin-bottom: 16px;
}

.room__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.room__members {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  background: #f5f7fa;
}

.room__member-host {
  border: 1px solid var(--el-color-primary);
}

.room__member-current {
  background: rgba(64, 158, 255, 0.1);
}

.room__member-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.room__member-host-icon {
  color: var(--el-color-warning);
}

.room__member-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room__phase-desc {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
}

.room__phase-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.room__speak-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__vote-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.room__vote-summary {
  color: var(--el-text-color-secondary);
}

.room__speeches {
  margin-top: 16px;
}

.room__chat {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.room__chat-tools {
  margin-bottom: 12px;
}

.room__chat-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.room__chat-history {
  max-height: 420px;
  overflow-y: auto;
  margin-bottom: 12px;
  padding-right: 4px;
}

.room__chat-message {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

.room__chat-message:last-child {
  border-bottom: none;
}

.room__chat-message--system {
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room__chat-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}

.room__chat-bubble {
  flex: 1;
  background: #f7f9fc;
  border-radius: 12px;
  padding: 8px 12px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.04);
}

.room__chat-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.room__chat-name {
  font-weight: 600;
  color: #1f2329;
}

.room__chat-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.5;
}

.room__chat-system {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff7e6;
  border-radius: 8px;
  padding: 6px 12px;
}

.room__chat-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.room__chat-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__winner {
  margin: 16px 0;
}

.room__phase-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room__witch-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__witch-poison {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
