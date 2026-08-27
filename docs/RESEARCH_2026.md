# NexaFlow Competitive & Platform Research — 2026

## Scope

This working record captures externally verified findings used to prioritize NexaFlow improvements. It will be consolidated into the final English release report.

## Competitor findings

| Product | Verified differentiators | Product implication for NexaFlow | Source |
|---|---|---|---|
| Tasker | Implements sets of actions based on contexts such as application, time, date, location, event, and gesture; offers user-defined profiles, clickable home-screen widgets, TaskerNet, pre-made projects, a plugin list, and developer resources. Its public examples combine multiple contexts and actions, including child-safety lock screens, time/location-based sound profiles, caller/SMS/Bluetooth announcements, and home-widget toggles. | NexaFlow should retain its approachable routine editor while improving portability, discovery, reusable templates, and ecosystem contracts. | https://tasker.joaoapps.com/ |
| MacroDroid | The official public landing page is available but did not yield machine-readable detail in this session. The search result describes a trigger–action–constraint automation model and templates; this should be verified against the official documentation before stating quantitative claims. | A constraint layer, templates, and a clear onboarding model are strategic reference points. | https://macrodroid.com/ |

## Initial architectural observations

NexaFlow already exposes a broad trigger/action model, editable automations, exit behaviors, plugins, widgets, scheduling, history, localization, explicit Room migrations, and a substantial Android CI pipeline. The first release scope should therefore favor **reliability, portability, observability, and maintainability** over adding speculative duplicate feature surfaces.

## References

1. Tasker, *Tasker for Android*, https://tasker.joaoapps.com/.
2. MacroDroid, *Android Automation App*, https://macrodroid.com/.

## Android platform findings

| Area | Official guidance | Release implication | Source |
|---|---|---|---|
| Exact alarms | On Android 14+ for a newly installed app targeting API 33+, `SCHEDULE_EXACT_ALARM` is denied by default unless the app qualifies for an exemption/pre-grant. This permission is also denied after backup-and-restore to Android 14. Apps should check `canScheduleExactAlarms()`, react to the permission-state-change broadcast, request the special permission only when needed, and degrade gracefully using an inexact scheduling method when denied. | The scheduler must distinguish precise user-facing routines from work that can be delayed; it must preserve an explicit permission state, record fallback scheduling, and make outcomes visible. | https://developer.android.com/about/versions/14/changes/schedule-exact-alarms |
| Persistent background work | WorkManager persists scheduled work across application restarts and device reboots, supports one-time and repeat work with flexible windows, respects power saving, and is intended for work that must run reliably after the app exits. Expedited work is for important user-visible work that completes within minutes. | Use WorkManager as the durable, observable fallback/reconciliation channel where exact time is not materially required; never misrepresent it as an exact alarm substitute. | https://developer.android.com/develop/background-work/background-tasks/persistent |

## Prioritization hypothesis

The highest-confidence improvements are not broad new trigger catalogues. They are: **(1)** make scheduling outcomes observable and recoverable under changing Android permission state; **(2)** provide safe, versioned, privacy-preserving portability for user-created routines; and **(3)** enforce the same release-quality contracts locally and in CI.

## Additional references

3. Android Developers, *Schedule exact alarms are denied by default*, https://developer.android.com/about/versions/14/changes/schedule-exact-alarms.
4. Android Developers, *Task scheduling*, https://developer.android.com/develop/background-work/background-tasks/persistent.

## Open-source landscape findings

| Reference | What its public repository indicates | Design lesson for NexaFlow | Source |
|---|---|---|---|
| ChaoMixian/vFlow | A large, recently active Android project describing a highly extensible graphical workflow environment that composes action modules into workflows. | Keep NexaFlow's routine-first experience, but make extensibility, composition, and safe portability first-class so advanced users do not outgrow the product. | https://github.com/ChaoMixian/vFlow |
| d4rken-org/bluemusic | A focused Android automation application for Bluetooth-device-specific actions. | High-quality specialist routines remain a useful benchmark for predictable status, device-specific configuration, and small-surface reliability. | https://github.com/d4rken-org/bluemusic |
| TashinMahmud/FlowZen | A recently updated Android workflow-automation project that advertises orchestration and on-device AI. | AI-assisted creation is an emerging direction, but should follow deterministic foundations, explicit capability boundaries, and privacy disclosure—not precede them. | https://github.com/TashinMahmud/-FlowZen---Advanced-Android-Workflow-Automation-App |
| Triple-T/gradle-play-publisher | A widely adopted open-source Gradle plugin for automating Android bundle upload, promotion, and listing publication. | The existing GitHub release workflow can be extended later to a Play delivery pipeline once service-account ownership, tracks, and review policy are explicitly provided. This is not safe to enable implicitly. | https://github.com/Triple-T/gradle-play-publisher |

## Research decision

The current release will prioritize **verifiable platform resilience and routine portability**. A broad AI or accessibility-driven screen-control feature is deliberately out of scope: it changes the threat model, permissions, user-consent surface, and test burden substantially, while the current product already has a mature native trigger/action core.

## Additional references

5. ChaoMixian, *vFlow*, https://github.com/ChaoMixian/vFlow.
6. d4rken-org, *bluemusic*, https://github.com/d4rken-org/bluemusic.
7. TashinMahmud, *FlowZen*, https://github.com/TashinMahmud/-FlowZen---Advanced-Android-Workflow-Automation-App.
8. Triple-T, *Gradle Play Publisher*, https://github.com/Triple-T/gradle-play-publisher.

## Expanded product-pattern research — 2026-08-26

| Product | Verified pattern | Relevance to NexaFlow | Source |
|---|---|---|---|
| Samsung Modes and Routines | Provides recommended routines that users can review and adapt, alongside custom routines constructed from conditions and actions. It also lets the user name and iconize a routine. | NexaFlow should make first-run success easier with safe, editable starter routines rather than forcing every user to begin from an empty builder. | https://www.samsung.com/levant/support/mobile-devices/how-to-use-routines-on-your-samsung-galaxy-device/ |
| MacroDroid | Documents a trigger–action–constraint model, reusable Action Blocks, global variables, templates, action testing, a system log, automatic local backups, export/import, and troubleshooting helpers. Its documentation states that constraints can limit a macro or individual trigger/action. | The highest-confidence gaps are a curated local starter-template experience, a visible diagnostic/reason layer, and durable user-owned backup ergonomics. General webhooks, scripting, cloud backup, and AI construction have materially wider permission, privacy, or operational scope and should be separate initiatives. | https://macrodroidforum.com/wiki/index.php/Overview |

### Decision note

The next release candidate should prioritize a **local, deterministic starter-template capability** and/or **actionable execution diagnostics**. Both address verified competitive expectations without introducing remote execution, account data, elevated-device control, or an AI data-processing surface.

## Additional references

9. Samsung, *How to use Routines on your Samsung Galaxy device*, https://www.samsung.com/levant/support/mobile-devices/how-to-use-routines-on-your-samsung-galaxy-device/.
10. MacroDroid Wiki, *Overview*, https://macrodroidforum.com/wiki/index.php/Overview.

| Product | Verified pattern | Relevance to NexaFlow | Source |
|---|---|---|---|
| vFlow | Defines a modular workflow registry with typed inputs/outputs, permission requirements, reusable modules, visual editing, variable data flow, control flow, import/export, and a coroutine-based executor with logging and timeout controls. | NexaFlow already has a safer routine-first model. It should borrow only the low-risk ideas of explicit reusable building blocks and execution explanation, avoiding wholesale screen-automation or privileged-process architecture. | https://github.com/ChaoMixian/vFlow |
| TaskerNet | Lets users share automation artifacts with a description, view a summary before import, confirm import, and explicitly advises review before enabling a shared automation. | Local NexaFlow templates should carry a human-readable description and capability/dependency summary, and must stay disabled after installation until the user explicitly enables them. | https://tasker.joaoapps.com/taskernet.html |

### Safe-template acceptance criteria

A template feature is safe for the current product only if the template source is bundled locally, the user can read a description and inspect its requirements before creating it, the generated routine is given a distinct ID, and it is disabled by default. No network access, account, server, script interpreter, accessibility control, or privileged action should be added as part of this release.

## Additional references

11. ChaoMixian, *vFlow*, https://github.com/ChaoMixian/vFlow.
12. Tasker, *TaskerNet File Sharing System*, https://tasker.joaoapps.com/taskernet.html.

## Release-scope decision: guided local starter routines

### Verified gap

NexaFlow already has a typed `RoutineTemplateCatalog`, capability filtering, and a builder route that accepts `templateId`. However, the navigation audit found no production entry point that supplies a template identifier. The capability is therefore effectively undiscoverable to ordinary users. In addition, newly saved routines are normally enabled immediately, which is inappropriate for an imported or prebuilt routine that a user has not yet reviewed in the dashboard.

### Selected implementation

The next release will make the existing local catalog discoverable **inside the new-routine builder**. It will present only templates whose declared requirements are currently executable, never overwrite a manual draft, apply the selection only to a new draft, prefill a localized routine name, and make the resulting newly saved routine **disabled by default**. Manual creation and editing retain their current enablement behavior. The user will therefore review the exact trigger/action configuration in the builder and must explicitly enable the saved routine from the dashboard.

| Criterion | Acceptance condition |
|---|---|
| Discoverability | A new empty builder exposes a starter-routines entry when compatible templates exist. |
| Capability honesty | Candidates come from the existing capability-filtered catalog. |
| Data safety | A selection is available only before manual draft content exists and never replaces an edit. |
| Review before activation | A routine created from a starter template is stored disabled; manual routines and edits preserve current behavior. |
| Localization | All visible new strings are present in every shipped locale. |
| Regression coverage | Tests cover template metadata mapping and initial-enabled policy. |

### Explicitly deferred

Remote template stores, cloud backup, webhooks, scripting, shared links, AI generation, accessibility-driven screen control, and privileged-process changes are not included. They require separate threat modeling, privacy policy, authentication/operations design, or platform-policy analysis.

## Execution observability research — 2026-08-26

| Product | Verified diagnostic pattern | Relevance to NexaFlow | Source |
|---|---|---|---|
| Tasker | Run Log records profile state changes plus task and action execution; it distinguishes service, profile, task, and action status, identifies error/rejection states, allows navigation to the corresponding configured item, and filters entries. | NexaFlow should make an existing execution record explainable in one place: what ran, what was blocked, where it failed, and the user-controlled next step. A compact outcome-oriented layer is preferable to verbose always-on raw logging. | https://tasker.joaoapps.com/userguide/en/activity_runlog.html |
| MacroDroid | System Log supports detailed, standard, warning, and error-only levels; it can include/exclude trigger/action/constraint data, filter individual macros and variables, and suppress noisy macros. | Severity-first filtering and low-noise per-routine diagnostic views are established patterns. For NexaFlow, a deterministic readiness explanation can be surfaced without expanding persistent logging or collecting new data. | https://macrodroidforum.com/wiki/index.php/System_log |

### Decision note

The product already contains `AutomationHealthReport`, `ExecutionTimeline`, capability snapshots, and execution history. The next investigation must first confirm whether these domain capabilities already reach the routine-detail UI. If not, a **readiness/reason card** with only derived local state is a lower-risk, higher-clarity improvement than a new raw log transport.

## Additional references

13. Tasker, *Run Log*, https://tasker.joaoapps.com/userguide/en/activity_runlog.html.
14. MacroDroid Wiki, *System Log*, https://macrodroidforum.com/wiki/index.php/System_log.
| Automate | Flow logs can be displayed, read, written, colored, and cleared as files; the published guide explicitly notes that mutable logs can let a malicious flow erase its own evidence. | NexaFlow should not introduce user- or workflow-writable diagnostic evidence. A read-only health summary derived from persisted execution records is the safer first release. | https://llamalab.com/automate/community/flows/43009 |

### Observability security decision

The proposed experience will not expose a writable raw log and will not add a background logging service, remote telemetry, or a new database table. It will surface the already-derived `AutomationHealthReport` in the routine detail screen and use only persisted `ExecutionRecord` data. This avoids mutable-evidence risks highlighted by Automate while still making repeated failures, skips, and last failure explanations visible.

15. Automate Community, *Flow Logs*, https://llamalab.com/automate/community/flows/43009.

## Release-scope decision: routine health summary in details

### Confirmed product gap

NexaFlow persists execution records, derives `AutomationHealthReport` values (completed, skipped, failed, consecutive failures, latest failure, and health status), and exposes a full execution-history screen. The automation-detail ViewModel does not currently consume `HealthRepository`, and the routine-detail screen does not render any health information. Users therefore cannot see, while inspecting a routine, whether it has never run, has repeatedly failed, has skips, or carries a latest failure explanation.

### Selected implementation

The next release will add a read-only **Execution health** card to the routine-detail screen. It will observe the existing health repository and summarize only locally-derived persisted history: no recorded runs, activity observed, or needs attention after repeated failures. It will show completed/skipped/failed counts and the latest failure text where relevant. The card will be present for every routine, with no network request, telemetry, permission, background worker, new database table, or mutable raw-log surface.

| Acceptance criterion | Required behavior |
|---|---|
| State accuracy | The screen subscribes to the existing health report for the displayed automation ID and updates reactively. |
| Clear interpretation | The title describes one of no recorded runs, activity observed, or needs attention; it never presents a single failure as a healthy success. |
| Evidence visibility | Counts for completed, skipped, and failed executions are displayed; the latest failure message is displayed only when one exists. |
| Safety | The feature is read-only and derives data from existing `ExecutionRecord` history. |
| Scope discipline | No change to the execution engine, scheduler, permissions, database schema, background execution, telemetry, or remote sharing. |
| Localization and regression | All new user-facing strings exist in every shipped locale and mapping logic is covered by unit tests. |

### Explicitly deferred

Filtering the paged History screen by a routine ID, raw log export, per-routine log-level controls, retry orchestration, remediation automation, remote diagnostics, and analytics are deferred. They either require navigation/data-layer changes, potentially sensitive export semantics, or broader operational design beyond a safe visibility release.

## Release-scope decision: filtered routine execution history

### Confirmed product gap

The new execution-health card identifies a routine needing attention but the current History destination always streams every record. The routine-detail screen has no direct path to the evidence underlying its health summary. Tasker supports log filtering and MacroDroid supports macro-level filtering, so a direct, local routine-history view is a high-value follow-on to the read-only health summary.

### Persistence and performance review

`ExecutionDao` already exposes a Room `PagingSource` and retention is capped at 1,000 rows. A `WHERE automationId = :automationId ORDER BY executedAt DESC` query can therefore filter at the database layer without materializing a global list. The small, bounded table and existing reactive invalidation make a schema migration or index unnecessary for this narrowly scoped release; the query will be exercised through the same paging path as global history.

### Selected implementation

The next release will add an optional `automationId` argument to the existing History destination. When present and non-blank, the ViewModel will request a filtered PagingSource, the title will describe the selected routine's run history, and the routine-details health card will expose an accessible action to open it. The global Settings entry keeps the unfiltered History view.

| Acceptance criterion | Required behavior |
|---|---|
| Database-side filtering | The DAO query filters by `automationId` before Paging maps entities to domain records. |
| Backward compatibility | Calling History with no valid routine ID keeps the existing global history behavior. |
| Direct evidence | The health card opens `history?automationId=<encoded-id>` for its routine. |
| State clarity | The filtered screen identifies itself as routine history without relying on a raw log. |
| Scope and privacy | No schema migration, network call, telemetry, new permission, export, or raw log mutation. |
| Testability | Selection logic and title mapping are covered by unit tests; existing paging-state tests remain valid. |

### Explicitly deferred

Search text, multi-routine selection, date-range filtering, destructive actions, export, raw log access, and remote diagnostics remain separate work. They require a fuller query/user-consent design than a routine-scoped evidence link.

## Observability-filter research

Tasker documents a Run Log with status-coded task and action outcomes, a text filter, category toggles, and entry-to-configuration navigation. It distinguishes successful completion, errors, rejections, disabled actions, and queue/termination states. MacroDroid documents four log-detail levels and controls that can include or exclude triggers, actions, constraints, individual macros, and global variables. Together, these patterns confirm that low-noise, local filtering is a core troubleshooting capability, provided it is scoped and does not introduce mutable or remote logging [16] [17].

| Competitor lesson | NexaFlow implication |
|---|---|
| Tasker separates successful, rejected, skipped, and errored outcomes, then supplies category and text filters. | The first NexaFlow filter should be a small, explicit outcome filter using persisted execution semantics, not a free-form or remote query. |
| MacroDroid lets users drill down to individual macros and control log detail. | Routine-scoped history is a sound base; outcome filtering should apply inside this already-scoped local history rather than widening the global surface. |
| Both frame filtering as diagnostic assistance. | Preserve local-only, read-only history, transparent filter state, and an obvious route back to the complete routine history. |

[16]: https://tasker.joaoapps.com/userguide/en/activity_runlog.html "Tasker — Run Log"
[17]: https://macrodroidforum.com/wiki/index.php/System_log "MacroDroid Wiki — System Log"

## Release-scope decision: local failed-run filter

### Confirmed product gap

v3.44.0 lets users reach the evidence for one routine, but a routine with a long history can still bury the failures that caused an attention signal. `ExecutionRecord.success` is a persisted boolean: `false` denotes a failed run, while a skipped maintenance run is persisted as `success = true` with a separate `Skipped:` message prefix. The first filter must therefore expose the unambiguous, database-queryable **Failed** state rather than incorrectly label all `success = true` rows as completed.

### Selected implementation

The next release will add a two-state local outcome filter to History: **All runs** and **Failed**. It will work in both the global and routine-scoped history views, execute inside Room before Paging, and retain the selected filter as the screen state. The Execution health card will include a direct **View failures** action only when the report contains one or more failures; that action opens the same routine history with the Failed filter selected.

| Acceptance criterion | Required behavior |
|---|---|
| Outcome integrity | Failed means persisted `success = false`; skipped records remain visible in All runs and are not mislabeled as completed. |
| Database-side filtering | The optional failure predicate is passed into Room before Paging and domain mapping. |
| Direct troubleshooting | A routine with failures offers a direct route to `history?automationId=…&outcome=failed`. |
| Backward compatibility | The existing History entry and an absent/invalid query parameter open All runs. |
| Scope and privacy | No schema migration, network call, telemetry, permission, mutable log, export, or destructive history operation. |
| Localization and coverage | Filter labels and direct action exist in every shipped locale; DAO and filter-state logic receive regression coverage. |

### Explicitly deferred

Completed-only and skipped-only filters, free-text search, date ranges, multi-select filters, export, raw action logs, remote diagnostics, and automated retry remain deferred. They require a richer persisted outcome model or a separate consent and query-design review.
