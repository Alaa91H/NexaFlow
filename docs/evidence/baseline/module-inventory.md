# Gradle module inventory — 2026-10-05

Generated from the current `settings.gradle.kts`; LOC counts Kotlin/Java files under each module `src/main` tree. Project dependencies are those declared as `project(...)` in the module build file.

| Module | Kotlin/Java main LOC | Project dependencies |
|---|---:|---|
| `app` | 6433 | :core:agent-api, :core:agent-relay, :core:agent-runtime, :core:agent-security, :core:ai-runtime, :core:automation-control, :core:automation-engine, :core:capability-manager, :core:common, :core:database, :core:datastore, :core:execution, :core:logging, :core:plugin-sdk, :core:rom-integration, :core:security, :core:ui-components, :core:wear-protocol, :data, :domain, :feature:ai, :feature:automation-builder, :feature:automations, :feature:dashboard, :feature:history, :feature:icons, :feature:settings, :feature:themes, :feature:widgets |
| `baseline-profile` | 0 | external/root config only |
| `core/agent-api` | 3705 | :core:agent-security, :core:automation-control, :core:execution, :domain |
| `core/agent-relay` | 561 | :core:agent-api, :core:security |
| `core/agent-runtime` | 408 | :core:ai-runtime |
| `core/agent-security` | 1025 | :core:security |
| `core/ai-runtime` | 4566 | :core:common |
| `core/automation-control` | 2504 | :core:database, :core:execution, :domain |
| `core/automation-engine` | 13083 | :core:common, :core:datastore, :core:execution, :core:plugin-sdk, :core:rom-integration, :core:security, :core:wear-protocol, :domain |
| `core/capability-manager` | 341 | :core:automation-engine, :core:datastore, :core:execution, :core:rom-integration, :core:ui-components, :domain |
| `core/common` | 538 | external/root config only |
| `core/compatibility` | 560 | :core:rom-integration |
| `core/database` | 1658 | :domain |
| `core/datastore` | 2705 | :core:ai-runtime |
| `core/execution` | 22095 | :core:common, :core:compatibility, :core:database, :core:datastore, :core:logging, :core:plugin-sdk, :core:rom-integration, :core:security, :core:wear-protocol, :domain |
| `core/logging` | 556 | external/root config only |
| `core/plugin-sdk` | 1621 | external/root config only |
| `core/rom-integration` | 7858 | :core:security, :domain |
| `core/security` | 316 | external/root config only |
| `core/ui-components` | 2491 | external/root config only |
| `core/wear-protocol` | 313 | external/root config only |
| `data` | 2599 | :core:agent-runtime, :core:automation-control, :core:database, :core:datastore, :core:plugin-sdk, :core:security, :domain |
| `domain` | 20904 | external/root config only |
| `feature/ai` | 501 | :core:ai-runtime, :core:ui-components |
| `feature/automation-builder` | 16465 | :core:automation-control, :core:automation-engine, :core:common, :core:execution, :core:plugin-sdk, :core:rom-integration, :core:security, :core:ui-components, :domain |
| `feature/automations` | 2119 | :core:automation-control, :core:datastore, :core:execution, :core:rom-integration, :core:ui-components, :domain |
| `feature/dashboard` | 2778 | :core:automation-control, :core:common, :core:datastore, :core:execution, :core:ui-components, :data, :domain, :feature:automations |
| `feature/history` | 1677 | :core:database, :core:execution, :core:logging, :core:ui-components, :domain, :feature:automations |
| `feature/icons` | 281 | :core:ui-components, :domain |
| `feature/settings` | 7395 | :core:agent-api, :core:agent-runtime, :core:agent-security, :core:ai-runtime, :core:automation-control, :core:automation-engine, :core:compatibility, :core:database, :core:datastore, :core:execution, :core:plugin-sdk, :core:rom-integration, :core:security, :core:ui-components, :data, :domain |
| `feature/themes` | 396 | :core:datastore, :core:ui-components, :domain |
| `feature/widgets` | 773 | :core:automation-control, :core:execution, :core:ui-components, :domain |
| `macrobenchmark` | 111 | :domain |
| `sample-plugins/nfc-toggle` | 347 | external/root config only |
| `test-fixtures/locale-plugin-fixture` | 153 | external/root config only |
| `wear` | 1162 | :core:wear-protocol |

Revalidated on clean `main` at `66f0dbd1327365b770901b2fa7d8d528a531ae9b`: `./gradlew projects --console=plain` completed successfully in 49 seconds and listed 36 included Gradle projects. A fresh Kotlin/Java `src/main` LOC scan returned 36 module rows and matched every LOC value above. Runtime behavior and device coverage are not implied by this inventory.
