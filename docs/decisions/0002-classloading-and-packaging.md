# ADR 0002: Classloading and Packaging Strategy

## Context
In Bukkit/Paper, each plugin JAR is loaded by its own `PluginClassLoader`. When multiple plugins shade the same shared library (such as `platform:api` or `platform:common`), the JVM creates distinct class objects in memory for each plugin. This results in:
1. `ClassCastException` when exchanging objects across plugin boundaries (e.g. `(SkyModule) otherModule` fails because the classes were loaded by different classloaders).
2. Redundant memory consumption in Metaspace and Heap.
3. Multiple uncoordinated static states.

## Decision
1. **Core-as-Platform Host**: `SkyRealmsCore` is the authoritative host plugin that bundles and exports all foundational platform bytecodes (`platform:api`, `platform:common`, `platform:database-api`, `platform:integrations-api`).
2. **Compile-Only Downstream Dependencies**: All downstream SkyRealms plugins depend on platform modules with `compileOnly` scope during build time.
3. **Paper Shared Classpath**: Downstream plugins declare `SkyRealmsCore` as a required dependency with `join-classpath: true` in their `paper-plugin.yml`.

## Consequences
* **Positive**: Single class identity across all plugins; zero bytecode duplication; full type-safety across Bukkit and SkyRealms service calls.
* **Negative**: `SkyRealmsCore` must be loaded and enabled before any other SkyRealms plugin.
