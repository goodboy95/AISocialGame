import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import "./styles/global.scss";
import { i18n } from "./i18n";
import pinia from "./store";

const app = createApp(App);

app.use(pinia);
app.use(router);
app.use(i18n);

app.mount("#app");
