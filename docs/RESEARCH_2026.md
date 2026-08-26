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
