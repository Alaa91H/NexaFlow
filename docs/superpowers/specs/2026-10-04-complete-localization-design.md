# NexaFlow Complete Application Localization Design

**Date:** 2026-10-04  
**Status:** Draft for user review  
**Scope:** Design specification only; no product implementation is authorized by this document.

## 1. Goal

Make every user-visible NexaFlow product surface professionally localized in every language the app currently supports. Users should be able to complete the same workflows, understand the same outcomes, and receive equivalent guidance regardless of selected language. Localization includes both translated copy and locale-correct formatting, accessibility text, and right-to-left behavior.

## 2. Current project baseline

The Android app declares English, Arabic, German, Spanish, French, Hindi, Japanese, Portuguese, Russian, Turkish, and Simplified Chinese (`zh-CN`) in `app/src/main/res/xml/locales_config.xml`. The repository has 178 `strings.xml` files across app and feature/core modules.

CI currently runs `scripts/check_strings_parity.py`, `scripts/audit_translation_completeness.py` (including key, untranslated-copy, script-leakage, placeholder, and empty-value checks), and `scripts/check_hardcoded_text.py`. The hardcoded-text check is currently Arabic-focused for shipped Kotlin/Java sources; this is useful protection but does not alone prove that all user-visible copy in all locales is localized. These checks are existing foundations, not evidence that every surface is already complete.

## 3. Scope

### Included

All shipped user-visible strings and spoken/read-out copy, across all app modules and supported locales, including:

- Screen titles, navigation, tabs, buttons, menus, labels, descriptions, hints, placeholders, tooltips, and empty/loading states.
- Dialogs, confirmations, validation errors, permission explanations, banners, snackbars, and other transient messages.
- Automation trigger/action names and configuration choices, templates, agent and provider setup, chat states/errors, execution history, and user-facing failure explanations.
- Notifications, notification channels, notification actions, foreground-service notices, shortcuts, widgets/tiles, and Wear surfaces shipped by NexaFlow.
- Accessibility labels, content descriptions, state descriptions, announcements, and spoken prompts.
- Manifest-provided user-visible labels, summaries, service/permission descriptions, and other Android system surfaces controlled by the app.
- Plurals, quantities, dates, times, durations, numbers, percentages, and other locale-sensitive formatting.

### Excluded

- User-authored or externally supplied content such as automation names, message bodies, agent prompts, provider/model identifiers, device names, plugin labels/content, and API responses. Preserve the content as entered/returned; localize NexaFlow framing and explanatory UI around it.
- Protocol constants, serialized identifiers, database keys, deep-link routes, log-only diagnostics, developer documentation, and third-party branding. Do not translate values whose exact spelling is required for interoperability.
- Adding languages beyond the eleven currently declared. Any future language addition must update app resources and the Android locale declaration together.

## 4. Design and implementation constraints

1. **Keep Android resources canonical.** Put stable user-facing copy in the owning module's Android resources and retrieve it through existing Compose/resource APIs. Do not create a second runtime translation catalog or move module-owned strings into a global module without a concrete ownership need.
2. **Localize at the point of presentation.** Persist stable semantic codes/typed outcomes where needed, then resolve them to localized presentation text. Do not persist rendered English messages as domain state.
3. **Preserve formatting contracts.** Use typed/plural resources and locale-aware Android formatting APIs for quantities, numbers, dates, times, and durations. Preserve format argument meaning and placeholder ordering across translations; escape literal percent signs and punctuation correctly.
4. **Handle RTL deliberately.** Arabic must work across screens, dialogs, notifications, widgets, and accessibility. Use direction-aware start/end layout and icons; isolate identifiers, URLs, MAC addresses, code, and other inherently LTR values so they remain readable in RTL contexts.
5. **Translate meaning, not word shape.** Each locale should use natural product terminology and appropriate formality. Keep NexaFlow and required technical/product identifiers unchanged where appropriate. Avoid machine-translated English copies and unexplained untranslated UI text.
6. **Treat user-generated strings as data.** Localization must not alter user-authored names, prompts, message text, device identifiers, or provider/model IDs.
7. **No silent locale regressions.** Default to English only through Android's normal fallback when the platform requests a locale not shipped by the app. For a shipped locale, missing translations are defects and must fail the localization audit rather than relying on runtime fallback.

## 5. Translation quality and review

The English resource is the source of truth for meaning and placeholders. Each supported language must have a complete, context-appropriate translation for each shipped string. Reviewers should check terminology consistency, grammar, tone, truncation-sensitive labels, technical terms, plural forms, and whether the translated control still communicates its action or state. Any intentional identical value must be justified through a narrow, reviewable allowlist; broad key/path exclusions are not acceptable.

No particular external translation service or vendor is mandated. Translation workflows may use tooling, but the committed resources and locale audit remain authoritative and reviewable in the repository.

## 6. Quality gates

The implementation plan should make localization checks part of the normal CI path and ensure they fail for each of these conditions:

- A shipped resource key is missing, unexpectedly extra, or empty in a supported locale.
- A user-facing translation is an accidental English copy, or text from another locale has leaked into a resource file, except for narrowly approved identical terms.
- A formatted string has a different placeholder multiset/type contract, a malformed plural, or invalid resource markup.
- A newly introduced hardcoded user-visible literal bypasses resources in shipped source, including text in every supported script (not just Arabic). Protocol constants, technical identifiers, test fixtures, and explicitly approved non-UI values must use narrow documented exemptions.
- The locale list declared for per-app language selection drifts from the locale directories expected for shipped application resources.

Each scanner must have focused self-tests proving that representative defects are detected, and CI must invoke both the self-tests and repository-wide checks. Android resource compilation/lint remains required to catch platform-level resource and formatting errors.

## 7. Verification and acceptance criteria

The implementation is complete when all of the following are true:

1. Every shipped user-visible surface in scope has been inventoried and mapped to its owning module/resource namespace.
2. All eleven supported locales have complete, non-empty, reviewed translations for shipped user-facing keys; no unexplained English fallback remains in those locales.
3. Resource keys, placeholders, plurals, markup, and declared locale configuration pass automated parity and Android resource validation.
4. CI detects new hardcoded UI text across supported scripts, with narrowly documented exemptions.
5. A locale smoke-test matrix covers English, Arabic RTL, and representative Latin, Indic, Cyrillic, Japanese, and Simplified Chinese locales. The full set is checked for resource resolution; visual review checks representative screens, system surfaces, clipping, directionality, and accessibility labels.
6. Tests demonstrate that dynamic user-created/provider content remains unchanged while surrounding NexaFlow UI is translated.
7. Translation changes are reviewed by people competent in the affected languages or by an explicitly defined equivalent quality-review process before release.

Passing static CI alone does not establish visual quality on every screen or linguistic quality; those are separate acceptance evidence and must be reported distinctly.

## 8. Delivery boundaries

This document defines desired behavior and acceptance criteria, not a file-by-file implementation sequence. After the user approves this written specification, prepare a separately reviewable implementation plan, then wait for approval of that plan and the execution method before changing product code. Translation work should be divided into auditable batches by module or surface so regressions and review quality remain manageable.

## 9. Open decisions for implementation planning

These do not block review of the product goal, but must be resolved in the implementation plan using repository evidence:

- Whether a translation-management format/tool is warranted or the existing resource scripts are sufficient.
- The concrete owner/review process for each language and how any unreviewed locale is handled in release gating.
- The exact scope and safe allowlist model for expanding the hardcoded-text scanner beyond Arabic.
- Which Android resource roots are shipped production surfaces versus test/sample-only resources, so acceptance gates cover the product without false positives.
- The representative visual/device matrix available for validating notifications, widgets, Wear, RTL, and accessibility.
