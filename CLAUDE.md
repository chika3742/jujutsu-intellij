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

`JjCommands` and `JjJsonCommand` are marked `@ApiStatus.Internal`. The one intentional exception to the layering rule is `JjVersion.detect()`, which calls `JjCommands.version(path)` before any repo is determined.

`JjCommands` exposes typed methods that return domain objects directly (e.g. `recentLog(repo, count): List<JjCommit>`,
`fileHistory(repo, rel, revset): List<JjHistoryEntry>`). Templates are not passed in; each command resolves the template
from the corresponding model's companion object (see "Template system" below). JSON decoding is done by
`JjJsonCommand.executeJsonList<T>(request)` which is `inline fun <reified T>` and uses
`kotlinx.serialization` (`Json { ignoreUnknownKeys = true }`).

**`JjRepository` use cases** (in `repo/JjRepository.kt`):

- Working copy: `workingCopyDescription()`, `describe(msg, rev?)`, `newChange(rev?)`, `commit(msg)` (sets the description on `@` and creates a new working-copy on top — i.e. describe + new), `abandon(revset)`, `edit(rev)`, `restore(paths)` (restores `@` files to their `@-` parent state), `squash(from, into)`, `rebase(revisions, destination, mode)`.
- Diff: `workingCopyChanges()` → `List<JjFileChange>`, `diffSummary(from, to)`.
- File: `fileHistory(rel)`, `annotateFile(rel, rev?)`, `showFile(rev, rel)`.
- Log: `recentLog(N)`, `allTimedCommits()`, `logByIds(ids)`, `logByRevset(revset)`, `workingCopyCommit()`, `workingCopyConflictedFiles()` — all returning `JjCommit`.
- Bookmarks: `listBookmarks()` (uses `jj bookmark list -T <json>`), `listBookmarksByCommitId(id)`, `currentBranch()`, `createBookmark`, `deleteBookmark`, `setBookmark`, `renameBookmark`, `forgetBookmark`, `trackBookmark`, `untrackBookmark` — returning `JjCommitRef`.
- Config: `configGet(key)`, `currentUser()`.
- Git: `gitFetch(remote?)`, `gitPush(bookmarks, remote?)`.

Mutation methods throw `JjOperationException` on CLI failure; reads return domain objects or `null` for "missing config" / "empty result" cases.

**Adding a new `jj` use case**:
1. If the operation returns structured data, define an `@Serializable` domain type in `repo/model/`. Put the corresponding jj template as `companion object { val TEMPLATE: String by lazy { JjTemplates.commitJsonLine { obj { ... } } } }` next to the data class so the JSON shape and the deserialization target stay in one file. Use `@SerialName` if a JSON key has to differ from the Kotlin field name; annotate `Date` fields with `@Serializable(with = JjDateSerializer::class)`.
2. Add a method on `JjCommands` taking `repo: JjRepository` that builds a `JjCli.Request` (via the `request(...)` private helper) and either calls `execute(...)` (for raw text output) or `JjJsonCommand.getInstance().executeJsonList<YourModel>(request)` (for NDJSON output). For JSON commands, reference `YourModel.TEMPLATE` directly inside the request; do not surface the template as a parameter on `JjCommands`.
3. Add a use-case method on `JjRepository` that delegates to the new `JjCommands` method and exposes the domain type. Mutation methods should call `.orThrow("operation name")`.

### Domain model (`repo/model/`)

Public domain types returned from `JjRepository` — `JjCommit` (log entry), `JjCommitRef` (bookmark/tag ref), `JjHistoryEntry`, `JjAnnotationLine`, `JjFileChange`, `JjUser`.

Types backed by NDJSON output from `jj` are annotated with `kotlinx.serialization` `@Serializable` and carry their own jj template in a `companion object` as `TEMPLATE: String`. JSON keys typically match Kotlin field names; `@SerialName("timestamp")` is used on `JjHistoryEntry.date` / `JjAnnotationLine.date` to match the template. `Date` fields use `@Serializable(with = JjDateSerializer::class)` to decode ISO 8601 strings (jj `Timestamp.format("%+")` output). Author display strings come from the top-level `formatAuthor(authorName, authorEmail)` helper in `repo/model/JjAuthor.kt`, exposed as the `.author` computed property on `JjHistoryEntry` / `JjAnnotationLine`.

For bookmarks, the `commitRefJsonLine` template yields one flat row per ref, decoded directly into `JjCommitRef`; `JjRepository.listBookmarks()` returns the list of refs.

### Template system (`cli/template/`)

jj has no built-in JSON output flag; the plugin builds jj template strings programmatically:

- `JjTemplateExpr.kt` — sealed type hierarchy (`StringExpr`, `IntegerExpr`, `BooleanExpr`, `CommitExpr`, `CommitRefExpr`, `AnnotationLineExpr`, `ListExpr<T>`, `LambdaExpr<...>`, etc.) with extension functions that render to jj template syntax.
- `JjJsonTemplate.kt` — `JjJsonValue` types (`obj`, `string`, `bool`, `serialized`) that compose template expressions into JSON-producing jj templates. Three entry points on `object JjTemplates`: `commitJsonLine {}`, `annotationJsonLine {}`, `commitRefJsonLine {}`.
- `lambda(::commitExpr) { it.commitId() }` builds typed `LambdaExpr` values from Kotlin lambdas. Use with `ListExpr.map(...)` for `.map(|p| ...)`-style jj template expressions.
- `serialized(list: ListExpr<SerializableExpr>)` emits a JSON array via jj's `json()` function. Used for `parents` → `["<id1>","<id2>"]`. For convenience, `ListExpr<CommitExpr>.commitIds()` maps each parent commit to its `commit_id()`.
- `TimestampExpr.iso8601()` renders `<ts>.format("%+")` so the JSON value is RFC 3339 / ISO 8601 — `JjDateSerializer` parses this single format with `DateTimeFormatter.ISO_OFFSET_DATE_TIME`.

Example: `JjTemplates.commitJsonLine { obj { "commitId" to string(commitId); "parentIds" to serialized(parents.commitIds()); "authorTime" to string(author.timestamp().iso8601()) } }` produces a jj template that emits one `{"commitId":"<id>","parentIds":["<p1>","<p2>"],"authorTime":"2025-…+09:00"}\n` line per commit.

Templates are owned by their corresponding `@Serializable` domain types (`JjCommit.TEMPLATE`, `JjCommitRef.TEMPLATE`, `JjHistoryEntry.TEMPLATE`, `JjAnnotationLine.TEMPLATE`). `JjCommands` references them directly; `JjRepository`, providers, and UI never pass templates around.

### Actions layer (`actions/`)

User-facing actions extend one of two base classes (both route work through `JjRepository` and share the off-EDT helper `runJjInBackground(project, title, errorTitle, block)`, which runs the block in a background task, calls `JjChangeWatcher.forceRefresh()`, and shows an error dialog on failure):

- **`JjRepositoryAction`** — for non-log actions (toolbar / main menu / project view). Resolves the repo from the file context via `findRepo(e)`, with a default `update` that enables the action when a repo resolves.
- **`JjLogCommitAction`** — for actions invoked on a VCS Log selection; exposes the selected commit(s) as the target.
- **`JjRebaseAction`** — `JjLogCommitAction` specialization parameterized by `RebaseMode` (`-r` / `-s`).

New actions extend the matching base and are registered in `plugin.xml` under `<actions>` (groups `Jujutsu.Menu`, `Jujutsu.ContextMenu`, `Jujutsu.LogContextMenu`).

### VCS provider implementations (`vcs/`)

All providers go through `JjRepository`:

- **`JjChangeProvider`** — populates Local Changes via `repo.workingCopyChanges()`. Synthesizes in-memory unsaved document changes. In co-located repos delegates to `GitIgnoredScanner` for ignored-file coloring.
- **`JjCheckinEnvironment`** — maps "Commit" to `repo.commit(msg)` (one `jj commit -m <msg>` invocation, replacing the prior describe + new pair). Selected files are intentionally ignored; jj auto-tracks all working-copy changes.
- **`JjRollbackEnvironment`** — maps "Revert" to `repo.restore("@-", paths)`.
- **`JjHistoryProvider`** / **`JjAnnotationProvider`** — call `repo.fileHistory(rel)` / `repo.annotateFile(rel, rev)`; receive `List<JjHistoryEntry>` / `List<JjAnnotationLine>` directly.
- **`JjDiffProvider`** / **`JjContentRevision`** / **`JjFileRevision`** — diff support; `JjContentRevision`/`JjFileRevision` lazily call `repo.showFile(rev, rel)` for file content at a specific revision.
- **`JjGitCoexistence`** — startup activity that detects co-located git+jj roots and redirects the VCS mapping from Git to Jujutsu (unless `enableGitDualMode` is set).
- Others: **`JjRootChecker`** (`.jj` root detection), **`JjDirectoryIndexExcludePolicy`** (excludes `.jj` from indexing), **`JjMergeProvider`** (conflict resolution), **`JjUpdateEnvironment`** (Update Project), **`JjCommitMessageProvider`** (prefills the commit message), **`JjFileAnnotation`** (blame UI model), and **`JjConflictTracker`** (project service tracking conflicted files).

### UI layer (`ui/`)

- **`ui/log/JjLogProvider`** — implements `VcsLogProvider` using `repo.recentLog(N)` / `repo.allTimedCommits()` / `repo.logByIds(ids)` / `repo.listBookmarks()` / `repo.currentUser()`. Accompanied by **`JjLogRefManager`**, **`JjLogStyleHighlighter`** (commit coloring), and **`JjBookmarkRefType`**.
- **`JjRevisionWidget`** — toolbar action added to `MainToolbarLeft`; shows the working-copy (`@`) description and opens **`JjToolbarPopup`** (bookmark menu). Reads from the `caches/` layer (see below) rather than calling the CLI directly.
- **`JjProjectViewProvider`** — hides the `.jj` directory in the Project View.

### Caches (`caches/`)

Project services that hold `jj` results so the UI can read them off the EDT without re-shelling:

- **`JjWorkingCopyCache`** — the `@` description / commit id / current branch, refreshed in the background with a 200 ms debounce and pushed to listeners on the EDT.
- **`JjCommitCache`** — commit id → description / bookmarks.
- **`JjBookmarkCache`** — local bookmark names.

Refresh is driven by `repo/JjChangeWatcher` (+ `JjChangeWatcherStartup`, a `postStartupActivity`): because `.jj` is excluded and emits no VFS events, the cache is refreshed on window focus and after each jj operation.

### Settings

`JujutsuAppSettings` (app-level persistent state in `jujutsu.xml`) stores: `executablePath`, `commandTimeoutMs` (default 30 s), `defaultLogRevset` (default `::@`), and `enableGitDualMode`. `JujutsuProjectSettings` (project-level) stores a `customLogRevset` that overrides `defaultLogRevset` when set. Auto-detection is performed by `JujutsuExecutableDetector` and cached for the session; the settings UI is `JujutsuConfigurable` (Settings > Tools > Jujutsu).

### Repository/path handling

- Centralize repo-relative path logic in `JjRepository` / `JjPathUtil`.
- Prefer `repo.relativize(...)`, `repo.normalizeRelativePath(...)`, and `repo.resolveRelativePath(...)` over duplicating string slicing in providers.

### Extension model

New IDE integrations (actions, services, file types, VCS handlers, etc.) are declared in `plugin.xml` under `<extensions defaultExtensionNs="com.intellij">` and implemented as Kotlin classes. The plugin depends on `com.intellij.modules.platform` and `com.intellij.modules.vcs`; add bundled plugin dependencies in `build.gradle.kts` under `intellijPlatform { bundledPlugin(...) }`.

### IntelliJ Platform Gradle Plugin v2 specifics

- Platform dependency is declared in `build.gradle.kts` via `intellijPlatform { intellijIdea("2025.2.4") }` — change the version string here to retarget.
- `sinceBuild` in `build.gradle.kts` controls the minimum compatible IDE build number.
- Use `testFramework(TestFrameworkType.Platform)` for UI/platform tests.

### Tests

`src/test` holds `cli/JjJsonParsingTest`, `cli/template/JjTemplatesTest`, `repo/JjPathUtilTest`, and `vcs/JjConflictTrackerTest`. Because the build cache can mask uncompiled tests, run test verification with `--rerun-tasks` rather than trusting an `UP-TO-DATE` result.

### Documents

- The source of IDEA Community is placed in `~/Documents/intellij-community`.
- Jujutsu Docs url is https://docs.jj-vcs.dev.
  - CLI reference: https://docs.jj-vcs.dev/latest/cli-reference/
  - Template language: https://docs.jj-vcs.dev/latest/templates/
  - Revset language: https://docs.jj-vcs.dev/latest/revsets/

### Important Principles

- Research existing patterns and follow them faithfully.
- Write all comments in English.
- Name variables, functions, etc. so their intent is conveyed accurately; do not abbreviate, but avoid overly long names.
- After changing behavior or fixing a bug, always update the tests accordingly.
