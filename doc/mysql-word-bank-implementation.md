# MySQL 切换与“谁是卧底”词库后台管理交付说明

## MySQL 数据库配置

- `config/settings/base.py` 现已去除 SQLite 依赖，默认使用 `config/service_settings.py` 中定义的 MySQL 连接信息。
- 所有环境变量均以 `MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MYSQL_PASSWORD/MYSQL_DATABASE` 读取，可通过 `MYSQL_CONN_MAX_AGE` 调整长连接。
- Pytest 测试环境同样使用 MySQL，可通过 `MYSQL_TEST_DATABASE` 指定测试库名称。
- `config/service_settings.py` 统一封装了 MySQL/Redis/LLM 的环境读取逻辑，缺失必填项会在启动时抛出异常，避免误回退到 SQLite。

## 词库后台管理 API

- 新增 `apps.games.serializers.WordPairSerializer` 与 `apps.games.views.WordPairViewSet`，提供词对的增删改查、关键字过滤及导入/导出接口。
- `apps/games/urls.py` 通过 DRF Router 注册 `/api/games/word-pairs/`，`config/urls.py` 暴露该路由。
- 批量导入使用 POST `/api/games/word-pairs/import/`，支持同一请求创建多条词对；导出使用 GET `/api/games/word-pairs/export/`。
- `apps/games/tests/test_word_pair_api.py` 覆盖了筛选、CRUD、导入/导出和校验逻辑。

## 前端后台管理界面

- 新增页面 `frontend/src/pages/WordBankAdminPage.vue`，提供词库表格、过滤、创建/编辑弹窗以及文本批量导入和 JSON 导出功能。
- API 封装位于 `frontend/src/api/wordPairs.ts`，类型定义在 `frontend/src/types/word-pairs.ts`。
- 路由 `/admin/word-bank` 增设登录校验，导航栏及头像下拉菜单提供入口。
- `frontend/src/i18n/messages.ts` 增补中英文词条，确保管理界面提示一致。

## 后续建议

- 线上部署需确保提供可写的 MySQL 数据库，并在导入批量词对前做好数据备份。
- 若需更细粒度的权限，可在后端为词库 API 添加角色校验，并在前端入口根据角色控制显示。
