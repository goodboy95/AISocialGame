# 集成测试清单（2025-11-27）

## 2025-11-27 后端单测（prompt.yml 生效回归）
- 命令：`echo '123456' | sudo -S docker run --rm -v "$PWD/backend":/workspace -v "$PWD/.cache/.m2":/root/.m2 -w /workspace maven:3.9-eclipse-temurin-21 mvn -q test`
- 结果：通过（覆盖 AuthService/GameController/GamePlay 等；新 PromptProperties 注入 & prompt.yml 加载无回归）。
- 备注：宿主 Java classworlds 缺失，使用 Docker Maven 运行。

## 2025-11-27 后端单测（AI 昵称 & 卧底顺序发言）
- 环境：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`（避免 Windows Java 导致 Maven classworlds 缺失）。
- 命令：`cd backend && mvn -q test`
- 结果：通过（含新增 `GamePlayServiceUndercoverTest`，覆盖 AI 逐人发言、全员投票后再结算）。

## 2025-11-25 本地后端单测
- 环境：`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH`
- 命令：`cd backend && mvn test`
- 结果：通过（AuthServiceTest/GameControllerTest/RoomServiceTest 全绿）。
- 备注：默认 `java` 指向 /usr/local 的 Oracle 发行版会导致 Maven 启动找不到 classworlds，需显式切换到 openjdk 可执行文件。

## 2025-11-26 Playwright MCP 端到端（修复后复测）
- 环境：`./build.sh`（后端补丁持久化玩家投票）、Compose 启动后通过 MCP 浏览器在 `http://socialgame.seekerhut.com` 操作。
- 账号：新注册一键账号（昵称自动玩家5127，密码 `Password123!`）。
- 谁是卧底：创建 6 人房 → 添加 5 个 AI → 开始游戏；本人描述一次后 AI 自动描述与投票，第一轮即找出卧底并结算，胜方平民；截图：`screenshot/undercover-room-full-fixed.png`、`screenshot/undercover-settlement-fixed.png`。
- 狼人杀：创建 6 人房 → 添加 5 个 AI → 夜晚自动行动，白天发言+投票一轮即结算，胜方狼人；截图：`screenshot/werewolf-settlement-fixed.png`。此前“DAY_VOTE 阶段停滞导致夜晚 400”已通过在 `vote*` 中持久化人类投票修复，复测未再出现。

## 已执行
- **2025-11-25 本地完整链路（MySQL+Redis 持久化）**
  - 后端：`sudo docker run ... mvn test`（H2 测试 profile）运行 `AuthServiceTest`、`RoomServiceTest`、`GameControllerTest` 全通过。
  - docker-compose：新增 MySQL(3307)/Redis(6380) 数据节点，挂载 `/var/lib/aisocialgame/{mysql,redis}`；前后端镜像重建后 `http://socialgame.seekerhut.com:8080/actuator/health` 返回 UP。
  - Playwright：`PLAYWRIGHT_BASE_URL=http://socialgame.seekerhut.com pnpm test:e2e` 通过 `tests/basic.spec.ts` 与 `tests/full-flow.spec.ts`（注册→登录→创建卧底/狼人杀房→添加 AI→开局→社区发帖）。
  - 脚本：根目录 `./system-test.sh` 固化以上流程（准备数据目录→后端单测→compose 启动→Playwright）。
- **2025-11-24 sudo ./build.sh**
  - 后端：Docker 内运行 `mvn clean test package` 通过。
  - 前端：`pnpm build` 成功（SWC/esbuild 脚本被 corepack 审批提示忽略，暂不影响构建）。
  - docker-compose：前后端镜像重建并重启；`http://socialgame.seekerhut.com` 首页可访问。
  - 健康检查：`http://socialgame.seekerhut.com:8080/actuator/health` 连续 40 次超时/未启用（旧版本）。
  - Playwright：`PLAYWRIGHT_BASE_URL=http://socialgame.seekerhut.com pnpm test:e2e` 通过（首页进入房间列表）。
- **后端单元/接口测试（Maven）**：`GameControllerTest`、`RoomServiceTest` 通过（`./build.sh` 自动运行）。为解决容器内 JVMTI 限制，新增 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 强制使用 subclass mock maker。
- **Playwright e2e（本地）**：`frontend/tests/basic.spec.ts`
  - 场景：打开首页 → 点击热门游戏“进入大厅” → 跳转房间列表。
  - 结果：通过（`PLAYWRIGHT_BASE_URL=http://localhost pnpm test:e2e`，容器前后端已启动）。
- **手工联调（容器）**
  - `http://localhost`：首页可打开；房间列表与创建房间流程正常。
  - `http://localhost:8080/api/games`：返回游戏列表 200。
  - `http://localhost:8080/actuator/health`：返回 500（后端未启用 actuator；`build.sh` 中该检查为非阻塞）。
- **外网域名检查**
  - `http://socialgame.seekerhut.com`：仍不可达（公网无 DNS 记录，MCP/Playwright 无法读取本机 hosts）。

## 2025-11-24 追加完整流程（Playwright MCP 手动）
- 注册 + 登录：注册 tester43@example.com → 自动登录成功（内存存储，重启会清空）。
- 狼人杀：创建房间 → 连续添加 5 个 AI 补位 → 6/12 后点击“开始游戏”成功进入夜晚流程。
- 谁是卧底：创建房间 → 添加 3 个 AI（共 4 人）→ 开始游戏成功进入发牌/描述阶段。
- 社区：输入动态并点击“发布”→ 成功提示并在推荐流顶部追加新帖。

## 待补充/下一步
- WebSocket 实时同步与多玩家并发场景（当前为内存模拟）。
- 私有房间密码校验、房主开局/踢人等后台流程。
- 更多 e2e：表单校验、异常分支覆盖（当前全流程 happy path 已覆盖）。
- 若要对外域名访问，需要在 DNS 配置 `socialgame.seekerhut.com -> 部署节点 IP` 或提供 hosts 覆写后再测。
