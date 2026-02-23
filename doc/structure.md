# 项目结构说明

> 更新时间：2026-02-23

## 结构树

AISocialGame/
├── backend/                                  # Spring Boot 后端
│   ├── sql/                                  # 后端 SQL（已从根目录 sql/ 收敛到此）
│   ├── src/main/java/com/aisocialgame/
│   │   ├── controller/                       # HTTP/WS 入口（含 auth、wallet、admin）
│   │   ├── service/                          # 业务流程（SSO、本地积分、AI、后台运营）
│   │   ├── integration/                      # Consul + gRPC 调用封装
│   │   ├── model/                            # JPA 实体（含 credit 领域）
│   │   ├── repository/                       # 数据访问层
│   │   ├── dto/                              # 请求/响应模型
│   │   └── config/                           # 应用配置、CORS、WS 配置
│   ├── src/main/resources/application.yml    # 默认连接外部依赖与域名
│   └── src/test/java/com/aisocialgame/       # 单测与集测
├── frontend/                                 # React + Vite 前端
│   ├── src/pages/                            # 大厅、房间、钱包、个人页、后台页
│   ├── src/components/                       # 游戏、社交、钱包、后台 UI 组件
│   ├── src/services/                         # API 调用封装
│   ├── tests/                                # Playwright E2E
│   └── playwright.config.ts                  # E2E 配置（含 ignoreHTTPSErrors）
├── doc/                                      # 项目文档（结构/API/模块/测试）
├── design-doc/                               # 设计草案与历史方案文档
├── docker-compose.yml                        # 仅编排本项目前后端容器
├── env.txt                                   # 部署环境变量（可覆盖默认配置）
├── build.sh                                  # 测试域名部署脚本（Linux）
├── build_prod.sh                             # 正式域名部署脚本（Linux）
├── build_local.ps1                           # 本地 PowerShell 部署脚本
├── README.md
├── AGENTS.md
└── projectStructure.md

## 目录约束与当前状态

- 前端代码均位于 `frontend/`。
- 后端代码与 SQL 均位于 `backend/`（包含 `backend/sql/`）。
- 根目录保留文档、部署脚本、`env.txt`、测试产物目录与项目元信息文件。
- `build.sh` 与 `build_prod.sh` 已保持同步，当前唯一差异为默认域名：
  - `build.sh` -> `aisocialgame.seekerhut.com`
  - `build_prod.sh` -> `aisocialgame.aienie.com`
