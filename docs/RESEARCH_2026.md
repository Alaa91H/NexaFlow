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
