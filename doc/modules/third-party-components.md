# 第三方组件对齐说明

> 更新时间：2026-02-23

## 依赖清单

当前项目对接以下基础组件（部署在 `192.168.5.141`）：

- MySQL
- Redis
- Qdrant
- Consul

## 对齐结果

- 后端默认配置与脚本默认值已统一：
  - MySQL：`192.168.5.141:3306`
  - Redis：`192.168.5.141:6379`
  - Qdrant：`http://192.168.5.141:6333`
  - Consul：`http://192.168.5.141:60000`
- 配置落点：
  - `backend/src/main/resources/application.yml`
  - `env.txt`
  - `build.sh` / `build_prod.sh`
- 运行时可通过 `env.txt` 覆盖所有连接参数。

## 说明

- 本仓库当前不维护第三方依赖的独立 `deploy/` 目录，依赖服务由外部 Docker 环境统一提供。
- `build.sh` 仅负责本项目前后端部署，并包含 MySQL 库/账号引导步骤（可通过 `MYSQL_BOOTSTRAP_ENABLED` 控制）。
