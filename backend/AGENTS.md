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

## Security considerations
- Public endpoints are explicitly listed in `SecurityConfig`. Update both the config and tests if you add new anonymous routes.
- JWT secrets and TTLs are injected via environment variables (`JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`). Never hardcode secrets.
- WebSocket access is authenticated in `WebSocketAccessTokenInterceptor`; any new channel must perform equivalent validation.
- Adjusting database schema requires adding a matching MySQL-compatible migration script under `src/main/resources/migration/`; remember to include alterations (e.g. `is_admin`, AI 模型/角色表) when evolving entities.

## Documentation & PR notes
- Update `backend/README.md` when adding new modules, endpoints, or environment variables.
- Keep architectural docs in `doc/` aligned with any significant backend change.
- Follow conventional commit summaries (≤72 characters) and include a brief description of affected modules in the body when submitting PRs.
