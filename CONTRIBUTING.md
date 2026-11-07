# Contributing to SkyRealms

## 1. Code Standards

* **Language**: Java 21 LTS (`--release 21`).
* **Formatting**: Standard Google Java Style (4-space indentation, no wildcard imports).
* **Nullability**: Annotate public API parameters and return values with `@NotNull` and `@Nullable` (`org.jetbrains.annotations`).
* **Immutability**: Favor records, immutable collections (`Collections.unmodifiableList`, `Set.copyOf`), and builder patterns for domain objects.
* **Logging**: Use SLF4J (`org.slf4j.Logger`) exclusively. Do not use `System.out.println` or raw Bukkit logger.

---

## 2. Architecture Principles

1. **Never Call Database or Network on the Main Thread**:
   * Any database query, I/O operation, or external API call must be executed asynchronously.
2. **Never Mutate World State from Async Threads**:
   * World block modification, entity spawns, and inventory changes must be scheduled on the primary Paper server thread.
3. **API / Implementation Decoupling**:
   * Shared contracts belong in `platform:api` or module API packages.
   * Do not create circular compile-time dependencies between plugins.
4. **Always Write Automated Tests**:
   * Every business logic class, dependency resolver, or state machine must have corresponding JUnit 5 unit tests before submission.

---

## 3. Commit and PR Workflow

* Branch names: `feature/m<number>-<topic>` or `fix/<topic>`.
* Verify local build before pushing:
  ```bash
  ./gradlew check
  ```
