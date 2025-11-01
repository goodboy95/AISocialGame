# Repository Guidelines

## Project Structure & Module Organization
- `backend/` hosts the Java 21 Spring Boot service (`controller`, `service`, `realtime`, `security`, `dto`, `repository` under `src/main/java/com/aisocialgame/backend/`).
- `frontend/` holds the Vue 3 + Vite app (`src/pages`, `store`, `services`, `components`, `api`, `types`) plus shared styling.
- `doc/` stores architecture and gameplay notes; update it whenever backend flows or UI states shift.
- `docker-compose.yml` bootstraps the stack; run it for quick smoke checks before raising PRs.

## Build, Test, and Development Commands
- `docker-compose up --build` starts the full stack for quick verification.
- `cd backend && ./mvnw spring-boot:run` runs the API, and `./mvnw test` executes the Spring/JUnit suite plus jar packaging.
- `cd frontend && npm install && npm run dev` launches the Vite server (5173); `npm run build` validates production bundles.
- Copy `frontend/.env.example` to `.env` and tune `VITE_API_BASE_URL` or `VITE_WS_BASE_URL` before front-end-only work.

## Coding Style & Naming Conventions
- Java code sticks to 4-space indentation and constructor injection; keep controllers slim and move orchestration into services.
- DTOs keep snake_case fields to satisfy current payload normalizers—extend normalizers rather than renaming API responses.
- Log with `slf4j`, lean on Bean Validation (`@Valid`, `@NotBlank`), and avoid storing secrets or mutable static state.
- Vue components use PascalCase filenames, stores use camelCase actions, and shared helpers belong in `src/services` or `src/utils`.

## Testing Guidelines
- Place backend tests in `backend/src/test/java`; use `@SpringBootTest` for API/WebSocket paths and `@DataJpaTest` for repository logic.
- Cover room lifecycle changes and realtime payload mappers when tweaking gameplay, asserting both REST responses and broadcast shapes.
- Frontend lacks automated coverage—if you add Vitest or Cypress, expose them via `npm run test` and document fixtures in `doc/`.
- 验证“谁是卧底”完整流程：模拟房主开始、玩家依次调用 `POST /rooms/{id}/undercover/speech/` 与 `POST /rooms/{id}/undercover/vote/`，确认阶段切换与胜负判定同步到 WebSocket 事件。

## Commit & Pull Request Guidelines
- Keep commit subjects under ~72 chars; conventional prefixes (`fix:`, `docs:`, `feat:`) appear alongside concise Chinese summaries—mirror that pattern.
- Mention affected modules in commit bodies (e.g., `backend/service`, `frontend/store`) and split refactors from behaviour changes.
- PRs should list purpose, highlights, manual test notes, and UI captures when visuals shift.
- Link issues or discussions, and update `doc/` or README files whenever behaviour or deployment steps change.

## Environment & Security Tips
- Backend reads secrets from environment variables (`JWT_SECRET`, `SPRING_DATASOURCE_*`); supply them via Compose overrides or runners, never commit them.
- WebSocket clients must pass the JWT as the `token` query param; keep new channels synced between `RoomRealtimeListener` and frontend stores before review.
