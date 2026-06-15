# Jujutsu for IntelliJ IDEA

IntelliJ Platform plugin that brings first-class [Jujutsu (jj)](https://jj-vcs.github.io/jj/) VCS support to IntelliJ
IDEA and other JetBrains IDEs.

> [!NOTE]
> Targets IntelliJ Platform 2025.2.4+ (build `252.25557+`) on JVM 21. Requires the `jj` CLI to be installed and
> reachable; install instructions are available in the [Jujutsu docs](https://docs.jj-vcs.dev/latest/install-and-setup/).

## Features

### Working copy and commits

- **Local Changes view** populated from `jj status` — auto-tracks every modification, no staging required.
- **Commit** action runs `jj commit -m <message>`. Selected files are intentionally ignored; jj snapshots the whole
  working copy.
- **Revert** maps to `jj restore --from @-`, restoring files from the parent of the working-copy commit.
- **New change**, **Describe**, **Abandon**, **Edit**, **Squash**, and **Rebase** (`-r` / `-s` modes) are available from
  the VCS menus and the Log context menu.
- **Resolve Conflicts** opens the merge tool for files reported as conflicted by jj.

### History, log & annotations

- **VCS Log** tab powered by a dedicated `VcsLogProvider`. Working-copy and conflicted commits get distinct foreground
  colors so they stand out at a glance.
- **File History** and **Annotate** show jj-native history rather than the Git history of the backing store.
- **Diff** view compares against any jj revision, including `@-`.

### Bookmarks & remotes

- **Create / Delete / Move / Rename / Forget** bookmarks from the main menu or the Log context menu.
- **Track / Untrack** remote bookmarks (`<bookmark>@<remote>` notation) directly from the Log.
- **Fetch** (`jj git fetch`) and **Push** (`jj git push`) for all configured Git remotes, with a push dialog that lets
  you pick which bookmarks to publish.
- Toolbar widget on the main toolbar shows the current working-copy description and offers a quick-access bookmark
  popup.

### IDE integration

- A commit message provider pre-fills the IDE's commit message field with the current `@` description, so editing the
  description is the same gesture as editing a commit message.

## Installation

The plugin is not yet published on the JetBrains Marketplace. For now, build and install it locally:

1. Install the `jj` CLI (>= 0.21 recommended). Verify with `jj --version`.
2. Clone this repository and run `./gradlew buildPlugin`. The artifact is produced at
   `build/distributions/Jujutsu-<version>.zip`.
3. In IntelliJ IDEA, open **Settings | Plugins**, click the gear icon, choose **Install Plugin from Disk…**, and pick
   the generated ZIP.
4. Restart the IDE.

### Configuring the plugin

Open **Settings | Tools | Jujutsu** to configure:

- **Executable path** — the location of the `jj` binary. Auto-detected on first launch; override here if you keep `jj`
  outside your `PATH`.
- **Command timeout (ms)** — how long the plugin waits for each `jj` subprocess (default 30 000 ms).
- **Default log revset** — the revset used by the Log tab (default `::@`). Can be overridden per-project.
- **Enable Git dual mode** — when enabled, both the Git and Jujutsu VCS roots stay active for the same directory.
  Disabled by default; the plugin normally redirects co-located roots from Git to Jujutsu at startup.

### Opening a project

- For a fresh jj-only repository, simply open the workspace root in the IDE. The plugin detects `.jj` and registers the
  Jujutsu VCS root automatically.
- For a **co-located** Git + jj repository, the `JjGitCoexistence` startup activity rewrites the VCS mapping from Git to
  Jujutsu unless **Enable Git dual mode** is on.

## Development

### Prerequisites

- JDK 21 (Temurin, Zulu, or any other distribution is fine).
- IntelliJ IDEA 2025.2.4+ for editing the project — earlier versions cannot resolve some platform symbols.
- A local `jj` CLI is helpful for end-to-end testing but not required for compilation.

### Common Gradle tasks

```bash
# Launch a sandboxed IDE with the plugin installed
./gradlew runIde

# Compile, run tests, and package the plugin
./gradlew check buildPlugin

# Run a single test class
./gradlew test --tests "net.chikach.jujutsuintellij.cli.JjJsonParsingTest"

# Verify plugin compatibility against the target IDE build
./gradlew verifyPlugin
```

Both the Gradle Configuration Cache and Build Cache are enabled. The build cache can hide test compilation failures, so
when in doubt, re-run tests with `--rerun-tasks`.

### Project layout

```
src/main/kotlin/net/chikach/jujutsuintellij/
├── actions/       User-facing actions (menus, Log context menu, toolbar)
├── caches/        Project services that cache jj state for the UI
├── cli/           JjCli subprocess runner, JjCommands, JjJsonCommand
├── cli/template/  Typed builder for jj template strings (NDJSON output)
├── config/        Settings UI and persistent state components
├── repo/          JjRepository (public API), JjRepositoryManager, JjChangeWatcher
├── repo/model/    Domain types (JjCommit, JjCommitRef, JjFileChange, ...)
├── ui/            Log provider, toolbar widget, project view tweaks
└── vcs/           Bridge to the IntelliJ VCS extension points
```

The architectural rule of thumb is that **callers always go through `JjRepository`**. Actions, providers, and UI never
touch `JjCli` or `JjCommands` directly. See [`CLAUDE.md`](CLAUDE.md) for the detailed layering rules and conventions for
adding a new `jj` use case.

### Useful links

- [Jujutsu documentation](https://docs.jj-vcs.dev)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin v2](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

## License

See [LICENSE](LICENSE).
