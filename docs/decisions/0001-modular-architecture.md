# ADR 0001: Modular Monolith Architecture

## Context
A full-featured SkyBlock server requires diverse systems (Economy, Islands, RPG, Machines, Dungeons, Farming, Mining, Social). Building everything into a single monolithic plugin leads to tight coupling, high regression risks, and unmaintainable code. Conversely, extreme microservices introduce significant network latency and operational complexity that is unsuited for a Minecraft tick loop (50ms budget).

## Decision
Adopt a **modular monolith** architecture:
* Systems are broken down into discrete Paper plugin modules (e.g. `SkyRealmsCore`, `SkyRealmsPlayer`, `SkyRealmsEconomy`, `SkyRealmsIslands`).
* All modules share a common platform layer (`platform:api`, `platform:common`).
* Inter-module communication occurs strictly via:
  1. Strongly-typed interfaces registered in the `ServiceRegistry`.
  2. Decoupled domain events dispatched through the `EventBus`.
* Modules define explicit lifecycle methods (`onEnable`, `onDisable`, `onReload`) and dependency constraints managed by a DAG `DependencyResolver`.

## Consequences
* **Positive**: High cohesion within modules; clear ownership boundaries; clean separation of concerns; individual modules can be tested in isolation.
* **Negative**: Requires strict discipline to prevent developers from bypassing APIs and introducing backdoor dependencies.
