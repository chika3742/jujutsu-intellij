# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

IntelliJ Platform Plugin for Jujutsu VCS integration. Built with Kotlin using the IntelliJ Platform Gradle Plugin v2 (`org.jetbrains.intellij.platform` v2.x). Targets IntelliJ IDEA 2025.2.4+ (build 252.25557+), JVM 21.

## Commands

```bash
# Run plugin in a sandboxed IDE instance
./gradlew runIde

# Build plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "net.chikach.jujutsuintellij.cli.JjCommandFactoryTest"

# Verify plugin compatibility
./gradlew verifyPlugin

# Check for issues
./gradlew check
```

Gradle Configuration Cache and Build Cache are both enabled (`gradle.properties`).

## Architecture

### Key entry points

- `plugin.xml` — declares all extensions, actions, listeners, and dependencies. Every new extension point registration must go here.
- `JujutsuVcs` — VCS implementation registered as `name="Jujutsu"` in `plugin.xml`. Routes IDE calls to project-level service implementations.
- `JjRepositoryManager` / `JjRepository` — project-scoped repository registry plus per-root context helpers. Get a repo handle via `JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)` or `getRepositoryForFile(file)`.
- `JjCommands` — app-level facade for repo-aware `jj` operations. All new `jj` use cases go here.
- `JjCli` — low-level synchronous process runner for `jj`; handles executable resolution, environment, timeout, stdin, and captured output. Never call from the EDT.
- `JujutsuBundle` — i18n helper; add strings to `src/main/resources/messages/JujutsuBundle.properties`.

### CLI layer

Three classes form a strict stack:

1. **`JjCli`** (app service) — spawns and captures the `jj` process. Sets `NO_COLOR=1` and `JJ_PAGER=` for deterministic output. Resolves the executable via `JujutsuAppSettings.resolvedExecutablePath()`.
2. **`JjCommandFactory`** (internal object) — constructs `JjCli.Request` values for every known `jj` command shape. Add or change command shapes here.
3. **`JjCommands`** (app service) — calls `JjCommandFactory` and executes via `JjCli` (or `JjJsonCommand` for JSON output). This is the public API surface; callers never touch `JjCli` or `JjCommandFactory` directly.

For commands that produce NDJSON output, `JjJsonCommand` wraps `JjCli` and delegates line-by-line parsing to `JjJsonParser`. Decoded domain objects are defined in `JjJsonDecoders.kt` (`LogEntryJson`, `HistoryEntryJson`, `AnnotationEntryJson`, etc.).

### Template system (`cli/template/`)

jj has no built-in JSON output flag; the plugin builds jj template strings programmatically:

- `JjTemplateExpr.kt` — sealed type hierarchy (`StringExpr`, `IntegerExpr`, `BooleanExpr`, `CommitExpr`, `AnnotationLineExpr`, etc.) with extension functions that render to jj template syntax.
- `JjJsonTemplate.kt` — `JjJsonValue` types (`obj`, `arr`, `string`, `num`, `bool`) that compose template expressions into JSON-producing jj templates. `JjTemplates.commitJsonLine {}` and `JjTemplates.annotationJsonLine {}` are the main entry points used by providers.

Example: `JjTemplates.commitJsonLine { obj { "ci" to string(commitId) } }` produces a jj template string that emits `{"ci":"<escaped-commit-id>"}\n` per commit.

### VCS provider implementations (`vcs/`)

- **`JjChangeProvider`** — populates Local Changes via `jj diff --summary --from @- --to @`. Synthesizes in-memory unsaved document changes. In co-located repos delegates to `GitIgnoredScanner` for ignored-file coloring.
- **`JjCheckinEnvironment`** — maps "Commit" to `jj describe -m <msg>` followed by `jj new`.
- **`JjRollbackEnvironment`** — maps "Revert" to `jj restore --from @- <paths>`.
- **`JjHistoryProvider`** / **`JjAnnotationProvider`** — file history and blame via `jj log` / `jj file annotate` with JSON templates.
- **`JjDiffProvider`** / **`JjContentRevision`** / **`JjFileRevision`** — diff support; `JjContentRevision` lazily reads file content at a specific revision via `jj file show`.
- **`JjGitCoexistence`** — startup activity that detects co-located git+jj roots and redirects the VCS mapping from Git to Jujutsu (unless `enableGitDualMode` is set).

### UI layer (`ui/`)

- **`JjLogProvider`** — implements `VcsLogProvider` (Git Log tab) using `JjCommands.recentLog` / `allLog` / `logByIds`.
- **`JjStatusBarWidget`** — status bar entry showing the working-copy description (`@`). Refreshes via `JjWorkingCopyDescription`, which debounces background `jj log -r @ -T description` calls by 200 ms.
- **`JjRevisionWidget`** — toolbar action added to `MainToolbarLeft`.

### Settings

`JujutsuAppSettings` (app-level persistent state in `jujutsu.xml`) stores: `executablePath`, `commandTimeoutMs` (default 30 s), `defaultLogRevset` (default `::@`), and `enableGitDualMode`. Auto-detection is performed by `JujutsuExecutableDetector` and cached for the session.

### Repository/path handling

- Centralize repo-relative path logic in `JjRepository` / `JjPathUtil`.
- Prefer `repo.relativize(...)`, `repo.normalizeRelativePath(...)`, and `repo.resolveRelativePath(...)` over duplicating string slicing in providers.

### Extension model

New IDE integrations (actions, services, file types, VCS handlers, etc.) are declared in `plugin.xml` under `<extensions defaultExtensionNs="com.intellij">` and implemented as Kotlin classes. The plugin depends on `com.intellij.modules.platform` and `com.intellij.modules.vcs`; add bundled plugin dependencies in `build.gradle.kts` under `intellijPlatform { bundledPlugin(...) }`.

### IntelliJ Platform Gradle Plugin v2 specifics

- Platform dependency is declared in `build.gradle.kts` via `intellijPlatform { intellijIdea("2025.2.4") }` — change the version string here to retarget.
- `sinceBuild` in `build.gradle.kts` controls the minimum compatible IDE build number.
- Use `testFramework(TestFrameworkType.Platform)` for UI/platform tests.