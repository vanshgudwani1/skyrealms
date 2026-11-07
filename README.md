# SkyRealms — Master Repository

> **Tagline:** Build. Automate. Explore. Conquer.  
> **Platform:** Minecraft Java Edition 1.21.x (Paper)  
> **Language:** Java 21 LTS  
> **Architecture:** Modular Monolith using separate Paper Plugin JARs  
> **Build System:** Gradle Kotlin DSL  

---

## 1. Project Overview

**SkyRealms** is a premium, custom Minecraft SkyBlock server delivering an RPG and industrial progression experience. Inspired by the depth of Hypixel SkyBlock, SkyRealms incorporates original progression systems, custom crops and mutations, tiered industrial machinery, automated minions, dungeons, bosses, and an active player economy.

---

## 2. Repository Layout

```
skyrealms/
├── platform/
│   ├── api/                   # Contracts: SkyModule, EventBus, ServiceRegistry
│   ├── common/                # Core implementations: DAG DependencyResolver, EventBus
│   ├── database-api/          # PostgreSQL connection & transaction abstractions
│   ├── integrations-api/      # Third-party plugin adapters (Vault, LuckPerms)
│   └── testing/               # Test harness, Mock ModuleContext, and assertions
├── plugins/
│   └── SkyRealmsCore/         # Primary runtime plugin & platform classloader host
├── docs/
│   └── decisions/             # Architecture Decision Records (ADRs)
├── ARCHITECTURE.md            # System architecture and threading model
├── ROADMAP.md                 # Milestone schedule and deliverables (M0 - M11)
├── PROJECT_STATUS.md          # Live task tracker
└── CONTRIBUTING.md            # Development standards and guidelines
```

---

## 3. Prerequisites

* **JDK 21 LTS** (Eclipse Adoptium Temurin 21 recommended)
* **Git 2.40+**
* Dedicated Minecraft Server: Paper 1.21.1+

---

## 4. Build and Deployment

### Compile and Run Unit Tests
```bash
./gradlew test
```

### Build and Deploy SkyRealmsCore to Server
```bash
./gradlew build
```
*(The build script automatically runs tests, packages `SkyRealmsCore-1.0.0.jar` with all internal platform classes, and copies it to `../plugins/`).*

---

## 5. First Playable Objective

Achieve the end-to-end player journey:
1. Join server -> Profile created.
2. Island created -> Starter kit given.
3. Build & farm -> Earn coins.
4. Purchase upgrade -> Save state.
5. Disconnect & reconnect -> Full state restored cleanly.
