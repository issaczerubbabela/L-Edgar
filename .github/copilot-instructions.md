---
name: L-Edgar Android Project
description: Modern Android expense tracking app with native Compose UI, Kotlin Coroutines, Room database, and Hilt dependency injection.
---

# L-Edgar Project Customization

This workspace includes 17 specialized skills for Android development, architecture, testing, and performance optimization.

## Core Android Architecture & Setup

- **[android-architecture](./skills/android-architecture/)** — Clean Architecture and Hilt setup guidance. Use when designing project structure, setting up modules, or implementing dependency injection.
- **[android-gradle-logic](./skills/android-gradle-logic/)** — Scalable Gradle build logic using Convention Plugins and Version Catalogs. Use when configuring build scripts or optimizing gradle setup.
- **[gradle-build-performance](./skills/gradle-build-performance/)** — Debug and optimize Android/Gradle build performance. Use when builds are slow or analyzing CI/CD bottlenecks.

## Data & Networking

- **[android-data-layer](./skills/android-data-layer/)** — Repository pattern, Room database, Retrofit synchronization, and offline-first strategies. Use when implementing data layer or database interactions.
- **[android-retrofit](./skills/android-retrofit/)** — Type-safe HTTP networking with Retrofit, OkHttp configuration, and Hilt integration. Use when setting up API clients or network requests.

## UI & Compose

- **[android-design-system](./skills/android-design-system/)** — Enforce Material Design 3 and design token usage in Jetpack Compose apps. Use when implementing M3 components, color schemes, or design tokens in Android.
- **[compose-ui](./skills/compose-ui/)** — Best practices for Jetpack Compose including state hoisting, performance optimization, and theming. Use when building or refactoring Composable functions.
- **[compose-navigation](./skills/compose-navigation/)** — Navigation Compose implementation with argument passing and deep links. Use when setting up multi-screen navigation or passing data between screens.
- **[compose-performance-audit](./skills/compose-performance-audit/)** — Diagnose and fix slow rendering, janky scrolling, and excessive recompositions. Use when troubleshooting Compose performance issues.
- **[coil-compose](./skills/coil-compose/)** — Image loading in Jetpack Compose with state handling and optimization. Use when implementing image loading from URLs or optimizing image performance.
- **[xml-to-compose-migration](./skills/xml-to-compose-migration/)** — Convert Android XML layouts to Jetpack Compose. Use when migrating Views to Compose or modernizing UI code.
- **[android-accessibility](./skills/android-accessibility/)** — Accessibility auditing and fixes for Jetpack Compose. Use when implementing accessible UI or fixing accessibility issues.

## Charting & Visualization

- **[vico-ui](./skills/vico-ui/)** — Vico charting library for Cartesian charts (line, bar, column). Use when building line/bar charts, formatting axes with Material 3 themes, or implementing custom markers and interactive tooltips.

## State Management & ViewModels

- **[android-viewmodel](./skills/android-viewmodel/)** — ViewModel best practices with StateFlow and SharedFlow. Use when implementing ViewModels or managing UI state.
- **[android-coroutines](./skills/android-coroutines/)** — Production Kotlin Coroutines patterns, structured concurrency, and lifecycle integration. Use when working with async/await, coroutines, or background operations.
- **[kotlin-concurrency-expert](./skills/kotlin-concurrency-expert/)** — Kotlin Coroutines review and remediation. Use when reviewing concurrency usage, fixing coroutine bugs, or improving thread safety.

## Asynchronous Code

- **[rxjava-to-coroutines-migration](./skills/rxjava-to-coroutines-migration/)** — Migrate from RxJava to Kotlin Coroutines and Flow. Use when converting RxJava code (Observables, Singles, Subjects) to Coroutines.

## Testing & Automation

- **[android-testing](./skills/android-testing/)** — Comprehensive testing strategy covering Unit, Integration, Hilt, and Screenshot tests. Use when implementing or reviewing test coverage.
- **[android-emulator-skill](./skills/android-emulator-skill/)** — Production-ready automation scripts for testing, building, and UI navigation. Use when automating app testing, build processes, or emulator lifecycle management.

## Quick Links

All skills are located in `[.github/skills/](./skills/)` and are automatically discovered and loaded by Copilot based on context and request keywords.

### Using Skills

Skills are available as:

- **Automatic loading**: Triggered when request keywords match skill descriptions
- **Slash commands**: Type `/` in the chat and search for skill by name
- **Explicit mentions**: Reference skill name directly in your request

## Mandatory Changelog Policy

For every prompt that results in any repository change (feature, fix, refactor, UI tweak, schema update, sync logic update, docs/instruction updates), the agent MUST:

1. Update `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt` in the same run.
2. Add entries under the latest release (`features`, `fixes`, or `qol`) or create a new release when scope is significant.
3. Perform this before finalizing the response.

If no repository files were changed, changelog update is not required.
