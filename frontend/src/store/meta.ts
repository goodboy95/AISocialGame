import { defineStore } from "pinia";

import { fetchAiStyles, type AIStyleMeta } from "../api/meta";

interface MetaState {
  aiStyles: AIStyleMeta[];
  loaded: boolean;
}

export const useMetaStore = defineStore("meta", {
  state: (): MetaState => ({
    aiStyles: [],
    loaded: false,
  }),
  actions: {
    async loadAiStyles() {
      if (this.loaded) {
        return;
      }
      this.aiStyles = await fetchAiStyles();
      this.loaded = true;
    },
    styleLabel(style: string | null | undefined) {
      if (!style) {
        return "-";
      }
      return this.aiStyles.find((meta) => meta.key === style)?.label ?? style;
    },
  },
});
