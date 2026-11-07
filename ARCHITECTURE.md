# SkyRealms System Architecture

## 1. Architectural Philosophy

SkyRealms is designed as a **modular monolith** with strong encapsulation:
* **Decoupled Modules**: Each feature area is encapsulated in a `SkyModule` with an explicit lifecycle, metadata, and dependency declarations.
* **Separation of API and Implementation**: Modules expose clean Java interfaces in `platform:api` or module-specific API subprojects. Implementation details remain private to the module.
* **Service Locator & Event Bus**: Modules communicate via a type-safe `ServiceRegistry` and a prioritized `EventBus`, eliminating direct circular dependencies.
* **Database Isolation**: Persistent database access uses connection pooling (HikariCP) and Flyway migrations. No direct SQL or blocking I/O is ever permitted on the Minecraft main thread.

---

## 2. Module Lifecycle & Dependency Engine

Every module implements `SkyModule`:

```
   [ UNLOADED ]
        |
        v  (register with ModuleRegistry)
   [ INITIALIZING ]
        |
        +--> (DAG Topological Sort via DirectedGraphDependencyResolver)
        |
        +--> onEnable()
        |
        v
   [ ENABLED ]  <----> onReload()
        |
        v  (Reverse Topological Order)
   [ DISABLED ]
```

### Fault Isolation & Graceful Degradation
* If a module fails during `onEnable()`, it enters the `FAILED` state.
* Dependent modules that require the failed module are prevented from enabling and marked `FAILED` with a diagnostic explanation.
* Independent modules continue to enable and serve traffic normally.

---

## 3. Classloading and Runtime Packaging Strategy

To avoid duplicate class instances and `ClassCastException` across Paper plugin classloaders:
1. `SkyRealmsCore` acts as the root host and runtime provider for `platform:api`, `platform:common`, `platform:database-api`, and `platform:integrations-api`.
2. Downstream feature plugins (e.g., `SkyRealmsDatabase`, `SkyRealmsPlayer`) compile against platform APIs using `compileOnly`.
3. In `paper-plugin.yml`, downstream plugins declare `SkyRealmsCore` as a required dependency with `join-classpath: true`:
   ```yaml
   dependencies:
     server:
       SkyRealmsCore:
         load: BEFORE
         required: true
         join-classpath: true
   ```
4. This ensures a single shared classloader space for the platform APIs, zero duplicated bytecodes in memory, and fast JVM JIT optimization.

---

## 4. Threading Model & Safety Rules

1. **Main Thread Non-Blocking Rule**:
   * NEVER execute SQL queries, HTTP calls, disk writes, or expensive math on the Minecraft server thread.
   * Asynchronous tasks use dedicated worker pools or Paper's async scheduler.
2. **Main Thread Mutation Rule**:
   * World block changes, player inventory updates, and Bukkit entity spawns MUST be synchronized back to the server thread using Paper's task scheduler.
3. **Idempotent State Changes**:
   * Economy transactions and critical gameplay operations require idempotency keys to eliminate race conditions and double-spending.
