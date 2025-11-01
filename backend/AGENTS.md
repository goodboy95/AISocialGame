# Backend Agent Guide

## Project overview
- Spring Boot 3 service providing REST + WebSocket APIs for the AI Social Game platform.
- Key packages: `controller`, `service`, `realtime`, `security`, `entity`, `dto`, `repository`, `config`.
- JWT-secured endpoints expect `/api` prefix; WebSocket endpoints live under `/ws/rooms/{id}` and require a JWT passed via the `token` query param.
- For a detailed breakdown of the project structure, see `projectStructure.md` in the root directory.
- Management APIs live under `/manage/**` (`ManageController` + `ManageService`) and are guarded by the new `ROLE_ADMIN` authority backed by the `UserAccount.isAdmin` flag.
- AI 提示词模板通过 `AiPromptService` 统一读取，表结构 `ai_prompt_templates` 以 `(game_type, role_key, phase_key)` 唯一组合区分场景，`ManageController` 暴露 `/manage/prompts/**` 接口供后台维护。

## Build & run
- Install Java 21. Use the Maven Wrapper shipped in the repo.
- Start the app: `./mvnw spring-boot:run`
- Run the test suite: `./mvnw test`
- Docker services can be launched from the repository root using `docker-compose up --build`.

## Code style & conventions
- Follow typical Spring conventions: controllers should remain thin, heavy logic belongs in the service layer.
- Prefer constructor injection. Avoid field injection and static state in services.
- Use `slf4j` logging (`LoggerFactory.getLogger`) rather than `System.out.println`.
- Validate input with Spring validation annotations where possible (`@Valid`, `@NotBlank`, etc.).
- When adding DTOs, keep snake_case fields to maintain compatibility with existing frontend normalizers.

## Testing guidance
- Use `@SpringBootTest` for integration tests that hit repositories or WebSocket flows.
- For repository-only logic prefer `@DataJpaTest` with in-memory H2.
- WebSocket behaviour is mediated through `RoomRealtimeEvents` and `RoomRealtimeListener`; consider publishing events in tests to assert broadcast payloads.

## Auth smoke test flow
1. From `backend/`, ensure Java 21 is available (`./mvnw -v`) and decide on the datasource; for quick checks export the following to stick with an in-memory H2 schema:
   ```bash
   export SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb"
   export SPRING_DATASOURCE_USERNAME="sa"
   export SPRING_DATASOURCE_PASSWORD=""
   export SPRING_DATASOURCE_DRIVER="org.h2.Driver"
   export SPRING_DATASOURCE_TYPE="com.zaxxer.hikari.HikariDataSource"
   ```
   If you lack write access to `/opt/seekerhut/aisocialgame/logs`, either create the directories with elevated privileges or override logging by pointing `LOGGING_CONFIG` (or `LOGBACK_CONFIGURATION_FILE`) at a console-only config placed in the project root.
2. Launch the API with `./mvnw spring-boot:run` and wait until the logs report “Tomcat started on port 8100 (http) with context path '/api'”. Leave the process running.
3. In another shell, exercise registration:
   ```bash
   curl -s -o /tmp/register.json -w "%{http_code}\n" \
     http://127.0.0.1:8100/api/auth/register/ \
     -H 'Content-Type: application/json' \
     -d '{"username":"tester","email":"tester@example.com","password":"Passw0rd!","display_name":"Tester"}'
   ```
   Expect a `200` status and a JSON profile in `/tmp/register.json`.
4. Verify login token issuance:
   ```bash
   curl -s -o /tmp/login.json -w "%{http_code}\n" \
     http://127.0.0.1:8100/api/auth/token/ \
     -H 'Content-Type: application/json' \
     -d '{"username":"tester","password":"Passw0rd!"}'
   ```
   Confirm the response body contains both `access` and `refresh` fields.
5. Validate the access token against `/auth/me/` (replace the subshell with your preferred JSON parser if `python3` is unavailable):
   ```bash
   ACCESS=$(python3 -c 'import json,sys;print(json.load(open("/tmp/login.json"))["access"])')
   curl -s -o /tmp/me.json -w "%{http_code}\n" \
     http://127.0.0.1:8100/api/auth/me/ \
     -H "Authorization: Bearer ${ACCESS}"
   ```
   A `200` status with the previously registered profile confirms the flow.
6. When finished, stop the Spring process (`Ctrl+C` in the run shell). Because the run used an in-memory H2 instance, the test data vanishes after shutdown. If you require persistence, swap the datasource exports for your MySQL configuration before starting the service.

## Security considerations
- Public endpoints are explicitly listed in `SecurityConfig`. Update both the config and tests if you add new anonymous routes.
- JWT secrets and TTLs are injected via environment variables (`JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`). Never hardcode secrets.
- WebSocket access is authenticated in `WebSocketAccessTokenInterceptor`; any new channel must perform equivalent validation.
- Adjusting database schema requires adding a matching MySQL-compatible migration script under `src/main/resources/migration/`; remember to include alterations (e.g. `is_admin`, AI 模型/角色表) when evolving entities.

## Documentation & PR notes
- Update `backend/README.md` when adding new modules, endpoints, or environment variables.
- Keep architectural docs in `doc/` aligned with any significant backend change.
- Follow conventional commit summaries (≤72 characters) and include a brief description of affected modules in the body when submitting PRs.
