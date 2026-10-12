# AGENTS.md — frontend

React 19 + TypeScript 7 + Vite 8 + Tailwind 4 SPA (see root [AGENTS.md](../AGENTS.md) for repo-wide rules).

## Commands (Node 26)

```bash
npm ci
npm run dev        # :5173, proxies /api, /ws, /actuator, /swagger-ui to BACKEND_URL (default http://localhost:8080)
npm run lint       # oxlint
npm run typecheck  # tsc -b (TypeScript 7 native compiler)
npm test           # vitest
npm run build
```

## Layout

- `src/lib/` — framework-free logic: `api.ts` (fetch wrapper, token refresh), `auth.tsx`, `realtime.tsx` (STOMP),
  `board.ts` (pure reducers for board state, unit-tested), `fractional.ts`, `types.ts` (mirrors backend DTOs).
- `src/components/` — `Board.tsx` (dnd-kit), `TaskModal.tsx`, `ProjectSettings.tsx`, `ui.tsx` (shared primitives).
- `src/pages/` — route components.

## Rules

- Server state lives in TanStack Query; query keys: `['board', projectId]`, `['task', id]`, `['tasks', projectId, params]`,
  `['activity', projectId, ...]`, `['notifications', ...]`. Realtime events patch the cache via `applyEvent`
  or invalidate it — don't add parallel global stores.
- Never store the access token in `localStorage`/cookies; use `getAccessToken()` from `lib/api.ts`.
- Drag & drop: send `{columnId, afterTaskId}` only; the optimistic key from `keyBetween` is replaced by the server's.
- Styling: Tailwind utilities + the semantic tokens in `src/index.css` (`bg-surface`, `text-muted`, `border-line`,
  `btn-*`, `input`, `card`). Support light and dark — no hard-coded colours without a `dark:` variant.
- `verbatimModuleSyntax` is on: use `import type` for type-only imports.
- Keep components accessible: labels for inputs, `aria-label` on icon buttons, keyboard-operable dialogs.
- Put pure logic in `src/lib` with a `*.test.ts` next to it.
