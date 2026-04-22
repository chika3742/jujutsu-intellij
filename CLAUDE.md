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

# Verify plugin compatibility
./gradlew verifyPlugin

# Check for issues
./gradlew check
```

Gradle Configuration Cache and Build Cache are both enabled (`gradle.properties`).

## Architecture

### Key entry points

- `plugin.xml` — declares all extensions, actions, listeners, and dependencies. Every new extension point registration must go here.
- `MyToolWindowFactory` — registers the tool window (`id="MyToolWindow"`) and constructs its UI content.
- `MyMessageBundle` — i18n helper wrapping `DynamicBundle`. Add localizable strings to `src/main/resources/messages/MyMessageBundle.properties` and access them via `MyMessageBundle.message("key")`.

### Extension model

New IDE integrations (actions, services, file types, VCS handlers, etc.) are declared in `plugin.xml` under `<extensions defaultExtensionNs="com.intellij">` and implemented as Kotlin classes. The plugin currently depends only on `com.intellij.modules.platform`; add bundled plugin dependencies in `build.gradle.kts` under `intellijPlatform { bundledPlugin(...) }`.

### IntelliJ Platform Gradle Plugin v2 specifics

- Platform dependency is declared in `build.gradle.kts` via `intellijPlatform { intellijIdea("2025.2.4") }` — change the version string here to retarget.
- `sinceBuild` in `build.gradle.kts` controls the minimum compatible IDE build number.
- Use `testFramework(TestFrameworkType.Platform)` for UI/platform tests.