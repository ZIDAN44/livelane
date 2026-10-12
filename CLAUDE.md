# CLAUDE.md

The shared agent instructions live in AGENTS.md (one source of truth for every coding agent):

@AGENTS.md

## Claude Code specifics

- Subproject rules: @backend/AGENTS.md and @frontend/AGENTS.md
- `.claude/launch.json` defines the `frontend` dev server for the preview browser; start the backend separately.
- On Windows, multiple JDKs may be installed — run Maven with `JAVA_HOME` pointing at JDK 27.
- Commit only when asked; follow the Conventional Commits rules in AGENTS.md (the `commit-msg` hook rejects others).
