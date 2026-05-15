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
./gradlew test --tests "net.chikach.jujutsuintellij.cli.JjJsonParsingTest"

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
- `JjRepositoryManager` / `JjRepository` — project-scoped repository registry. `JjRepository` is the **public use-case API**: working-copy / diff / file / log / bookmark / config / git operations are exposed as methods on it. Get a repo handle via `JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)` or `getRepositoryForFile(file)`.
- `JjCli` — low-level synchronous process runner for `jj`; handles executable resolution, environment, timeout, stdin, and captured output. Never call from the EDT.
- `JujutsuBundle` — i18n helper; add strings to `src/main/resources/messages/JujutsuBundle.properties`.

### Layered architecture

Callers should always go through **`JjRepository`** — providers, actions, and UI widgets do not touch the CLI layer directly.

```
caller (provider / action / UI)
  → JjRepository (use-case methods, returns repo/model/ domain objects)
    → JjCommands (internal: typed wrapper, takes JjRepository; builds JjCli.Request inline)
      → JjCli (subprocess execution)
```

`JjCommands` and `JjJsonCommand` are marked `@ApiStatus.Internal`; the `*Json` intermediate types in `JjJsonDecoders.kt` are `internal`. The one intentional exception is `JjVersion.detect()`, which calls `JjCommands.version(path)` before any repo is determined.

**`JjRepository` use cases** (in `repo/JjRepository.kt`):

- Working copy: `workingCopyDescription()`, `describe(msg)`, `newChange()`, `commit(msg)` (uses `jj commit`), `abandon(revset)`, `restore(fromRev, paths)`.
- Diff: `workingCopyChanges()` → `List<JjFileChange>`, `diffSummary(from, to)`.
- File: `fileHistory(rel)`, `annotateFile(rel, rev?)`, `showFile(rev, rel)`.
- Log: `recentLog(N)`, `allTimedCommits()`, `logByIds(ids)`.
- Bookmarks: `listBookmarks()` (uses `jj bookmark list -T <json>`), `bookmarksForLog()`, `createBookmark`, `deleteBookmark`, `setBookmark`.
- Config: `configGet(key)`, `currentUser()`.
- Git: `gitFetch(remote?)`, `gitPush(bookmark?, remote?)`.

Mutation methods throw `JjOperationException` on CLI failure; reads return domain objects or `null` for "missing config" / "empty result" cases.

**Adding a new `jj` use case**:
1. Add a method on `JjCommands` taking `repo: JjRepository` that builds a `JjCli.Request` (via the `request(...)` private helper) and calls `execute(...)` / `executeObjects(...)`.
2. Add a use-case method on `JjRepository` that maps result/output to a domain type in `repo/model/`.

### Domain model (`repo/model/`)

Public domain types returned from `JjRepository` — `JjLogEntry`, `JjTimedCommit`, `JjHistoryEntry`, `JjAnnotationLine`, `JjFileChange`, `JjBookmark` (with `JjBookmarkRemoteRef`), `JjBookmarkLogRef`, `JjUser`. Callers should depend on these instead of the internal `*Json` decoder records.

### Template system (`cli/template/`)

jj has no built-in JSON output flag; the plugin builds jj template strings programmatically:

- `JjTemplateExpr.kt` — sealed type hierarchy (`StringExpr`, `IntegerExpr`, `BooleanExpr`, `CommitExpr`, `CommitRefExpr`, `AnnotationLineExpr`, `ListExpr<T>`, `LambdaExpr<...>`, etc.) with extension functions that render to jj template syntax.
- `JjJsonTemplate.kt` — `JjJsonValue` types (`obj`, `arr`, `string`, `num`, `bool`, `serialized`) that compose template expressions into JSON-producing jj templates. Three entry points: `JjTemplates.commitJsonLine {}`, `annotationJsonLine {}`, `bookmarkRefJsonLine {}`.
- `lambda(::commitExpr) { it.commitId() }` builds typed `LambdaExpr` values from Kotlin lambdas. Use with `ListExpr.map(...)` for `.map(|p| ...)`-style jj template expressions.
- `serialized(list: ListExpr<SerializableExpr>)` emits a JSON array via jj's `json()` function. Used for `parents` → `["<id1>","<id2>"]`. For convenience, `ListExpr<CommitExpr>.commitIds()` maps each parent commit to its `commit_id()`.

Example: `JjTemplates.commitJsonLine { obj { "ci" to string(commitId); "p" to serialized(parents.commitIds()) } }` produces a jj template that emits `{"ci":"<id>","p":["<parent1>","<parent2>"]}\n` per commit.

All current templates live as `private val ... by lazy` in `JjRepository.kt`'s companion object — providers do not own templates.

### VCS provider implementations (`vcs/`)

All providers go through `JjRepository`:

- **`JjChangeProvider`** — populates Local Changes via `repo.workingCopyChanges()`. Synthesizes in-memory unsaved document changes. In co-located repos delegates to `GitIgnoredScanner` for ignored-file coloring.
- **`JjCheckinEnvironment`** — maps "Commit" to `repo.commit(msg)` (one `jj commit -m <msg>` invocation, replacing the prior describe + new pair). Selected files are intentionally ignored; jj auto-tracks all working-copy changes.
- **`JjRollbackEnvironment`** — maps "Revert" to `repo.restore("@-", paths)`.
- **`JjHistoryProvider`** / **`JjAnnotationProvider`** — call `repo.fileHistory(rel)` / `repo.annotateFile(rel, rev)`; receive `List<JjHistoryEntry>` / `List<JjAnnotationLine>` directly.
- **`JjDiffProvider`** / **`JjContentRevision`** / **`JjFileRevision`** — diff support; `JjContentRevision`/`JjFileRevision` lazily call `repo.showFile(rev, rel)` for file content at a specific revision.
- **`JjGitCoexistence`** — startup activity that detects co-located git+jj roots and redirects the VCS mapping from Git to Jujutsu (unless `enableGitDualMode` is set).

### UI layer (`ui/`)

- **`JjLogProvider`** — implements `VcsLogProvider` using `repo.recentLog(N)` / `repo.allTimedCommits()` / `repo.logByIds(ids)` / `repo.bookmarksForLog()` / `repo.currentUser()`.
- **`JjStatusBarWidget`** — status bar entry showing the working-copy description (`@`). Refreshes via `JjWorkingCopyDescription`, which debounces background `repo.workingCopyDescription()` calls by 200 ms.
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