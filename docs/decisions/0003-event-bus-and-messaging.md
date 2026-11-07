# ADR 0003: Event Bus, Versioned Events, and Outbox Pattern

## Context
Modules need to react to state changes in other systems (e.g. `SkyRealmsEconomy` needs to know when an island upgrade is purchased in `SkyRealmsIslands`; `SkyRealmsProgression` needs to know when crops are harvested in `SkyRealmsFarming`). Directly invoking concrete classes couples modules together and creates circular dependencies.

## Decision
1. **Internal Event Bus**: Implement an in-memory `EventBus` supporting priority levels (`LOWEST` to `MONITOR`), cancellable events, and async publishing.
2. **Versioned Event Identifiers**: All events implement `SkyEvent` and define an explicit version string following the pattern `skyrealms.<domain>.<action>.v<version>` (e.g. `skyrealms.player.created.v1`, `skyrealms.economy.transaction.completed.v1`).
3. **Transient vs. Durable Events**:
   * *Transient events*: In-memory only, discarded once handled (e.g. UI click events, temporary damage calculation tweaks).
   * *Durable events*: Mark `isDurable() = true`. In M1+, durable events will be written to an transactional database outbox table before being acknowledged, ensuring zero event loss even during an abrupt server crash or database disconnection.

## Consequences
* **Positive**: Fully decoupled event-driven communication; safe schema evolution through versioned event names; auditability and crash resilience for critical transactions.
* **Negative**: Requires maintaining version compatibility when upgrading event schemas.
