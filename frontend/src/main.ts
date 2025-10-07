import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import "./styles/global.scss";
import { i18n } from "./i18n";
import pinia from "./store";
import { useAuthStore } from "./store/user";

const app = createApp(App);

app.use(pinia);
const authStore = useAuthStore(pinia);
void authStore.initialize();
app.use(router);
app.use(i18n);

app.mount("#app");
