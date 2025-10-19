# Frontend Agent Guide

## Project overview
- Vue 3 + TypeScript single-page application served by Vite.
- Pinia stores handle auth (`user`) and room state (`rooms`); WebSocket logic lives in `src/services/realtime.ts`.
- Element Plus is auto-imported via Vite plugins; styles are customised under `src/styles`.

## Build & run
- Install Node.js 18+ (project ships with `package-lock.json`).
- Install dependencies: `npm install`
- Start dev server: `npm run dev`
- Build production bundle: `npm run build`
- Preview production build: `npm run preview`
- Docker-based workflow: from repo root run `docker-compose up --build`

## Code style & conventions
- Use the Composition API and `<script setup>` for new components.
- Strongly type store state/actions with TypeScript interfaces located in `src/types`.
- Keep REST clients under `src/api`; wrap HTTP calls in functions returning typed promises.
- Respect the existing normalizer helpers in `rooms` store when adding new backend fields (handle both snake_case & camelCase).
- Component SCSS should import shared variables from `src/styles/theme.scss` instead of redefining colours.

## Testing & quality
- No automated tests are configured yet. If you add Vitest/Cypress, extend `package.json` scripts and document usage in `frontend/README.md`.
- Use Vite's ESLint/Volar integration for type-checking during development.
- When changing realtime flows, test multi-tab scenarios to ensure `socketConnected` toggles correctly.

## Integration tips
- `.env` files drive API/WebSocket base URLs (`VITE_API_BASE_URL`, `VITE_WS_BASE_URL`). Update docs when adding new variables.
- WebSocket connections require a JWT token; the helper automatically appends `token` query params using the auth store.
- Update `src/router/index.ts` if you introduce new pages, and add route guards for authenticated sections.

## Documentation & PR notes
- Keep `frontend/README.md` and `doc/` in sync with UI or workflow changes.
- Follow the repository's PR guidance: descriptive summary plus key testing commands.
- Include screenshots (using the provided tooling) whenever you modify visible UI components.
