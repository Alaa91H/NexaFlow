# Master plan open questions

This file tracks external decisions and unavailable evidence required by the supplied NexaFlow Master Execution Plan. A question stays open until answered with a recorded decision or artifact; silence is not treated as approval.

| ID | Question / dependency | Why it blocks acceptance | Owner / evidence needed | Status |
|---|---|---|---|---|
| OQ-01 | Which physical Android test devices and OEM builds are available for the 72-hour telephony soak, the three-OEM reliability matrix, and API 36/37 permission checks? | P0-04, P1-01, P1-03, P2-01, P3-15 and P5/P6 require real-device evidence. | Maintainer: provide device models/builds and test window; attach sanitized logs under `docs/evidence/`. | OPEN — no device connected in current session. |
| OQ-02 | Is Google Play a required distribution channel, in addition to GitHub APK releases? | P2-02/P9 require a Play-compliant flavor, current policy decision, Data Safety and staged rollout details. | Project owner: select channels; validate against the live Play Console policy/account. | OPEN — current release evidence is GitHub only. |
| OQ-03 | Which host/credential setup is approved for full-history secret scanning and dependency/security alert review? | P0-05/P7-01 require full-history gitleaks/trufflehog evidence and response for any findings. | Maintainer: make scanner available or provide CI result; never place credentials in repository. | OPEN — scanners unavailable in current local environment. |
| OQ-04 | What model-provider accounts and budget are authorized for the live AI proof and any nightly live eval? | P3-01, P3-10, P3-12 and P3-15 require real provider behavior while constraining data and spend. | Project owner: select provider, test account, approved data, and spending ceiling. | OPEN — no live credentials/device proof collected. |
| OQ-05 | Is the intended first-party distribution GitHub-only, Play, F-Droid, or multiple channels? | Determines permission/flavor split, metadata, signing and progressive rollout scope. | Project owner decision; record policy review before implementing distribution changes. | OPEN. |

## Decisions already supported by source or CI

- The active release path is a single workflow, `.github/workflows/nexaflow-ci.yml`; tagged v3.91.9 published only the phone and Wear APKs.
- The repository already contains both the in-app LLM/tool path and an external agent gateway. Their source/tests do not fulfill the plan's live-device proof requirement.
- LAN agent access remains disabled until TLS transport exists; do not enable cleartext LAN as a shortcut.
- Canonical workflow execution must continue through the existing engine/router. No parallel agent execution path is authorized by this plan.
