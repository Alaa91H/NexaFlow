package com.nexaflow.domain.models

/**
 * Earlier versions persisted an English sentence generated from enum names as
 * an automation description. It is not user-authored content and becomes wrong
 * as soon as the app language changes, so presentation layers replace it with
 * their localized live summary.
 */
fun String.isLegacyGeneratedAutomationDescription(): Boolean =
    matches(Regex("When (configured|.+), then \\d+ action\\(s\\)"))

/** True only for a real description that should be displayed verbatim. */
fun Automation.hasUserAuthoredDescription(): Boolean =
    description.isNotBlank() && !description.isLegacyGeneratedAutomationDescription()
