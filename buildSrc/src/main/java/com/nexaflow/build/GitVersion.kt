package com.nexaflow.build

import org.gradle.api.Project

/**
 * Derives the app version entirely from Git tags.
 *
 * **versionName** – produced by `git describe`:
 *   - On a tagged commit → the tag itself, e.g. `v3.26.0-alpha`
 *   - After a tag        → tag + distance, e.g. `v3.26.0-alpha-6-g150fcaa`
 *   - No tags exist       → fallback `0.0.1-<hash>`
 *
 * **versionCode** – monotonically increasing integer:
 *   - Major×100000 + Minor×1000 + Patch×10 + distanceSinceTag
 *   - Patch >9 is clamped to 9 (allows 10 distance increments before minor bump)
 *   - No tags exist → `1`
 */
data class GitVersionInfo(val versionName: String, val versionCode: Int)

fun Project.gitVersion(): GitVersionInfo {
    // Freebuff snapshot tags are workspace metadata, not release versions.
    // Restrict the lookup to semantic v* tags so local and CI builds produce
    // the same version when the worktree contains both kinds of tags.
    val tag = runGit("describe", "--tags", "--always", "--match", "v[0-9]*")
        .ifBlank { return GitVersionInfo("0.0.1-unknown", 1) }

    // 1) Distance commit: v3.26.0-alpha-6-g150fcaa  (most specific — check first)
    val distancePattern = Regex("^(?:v?)(\\d+)\\.(\\d+)\\.(\\d+)(?:-.+?)?-(\\d+)-g([0-9a-f]+)$")
    val distanceMatch = distancePattern.find(tag)
    if (distanceMatch != null) {
        val major = distanceMatch.groupValues[1].toInt()
        val minor = distanceMatch.groupValues[2].toInt()
        val patch = distanceMatch.groupValues[3].toInt().coerceAtMost(9)
        val distance = distanceMatch.groupValues[4].toInt()
        return GitVersionInfo(
            tag, // already prefixed with "v"
            major * 100_000 + minor * 1_000 + patch * 10 + distance
        )
    }

    // 2) Tag-only commit: v3.26.0-alpha
    val tagOnlyPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-.+)?$")
    val tagOnly = tagOnlyPattern.find(tag)
    if (tagOnly != null) {
        val major = tagOnly.groupValues[1].toInt()
        val minor = tagOnly.groupValues[2].toInt()
        val patch = tagOnly.groupValues[3].toInt().coerceAtMost(9)
        return GitVersionInfo(
            "v${major}.${minor}.${patch}" + (tag.substringAfter("${major}.${minor}.${patch}")),
            major * 100_000 + minor * 1_000 + patch * 10
        )
    }

    // 3) Fallback: detached HEAD or unusual format
    val hash = tag.take(7)
    return GitVersionInfo("0.0.1-$hash", 1)
}

private fun Project.runGit(vararg args: String): String {
    // Gradle 9 removed `Project.exec`; `providers.exec` is the supported
    // replacement and returns the captured output as a provider.
    return providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.orNull?.trim().orEmpty()
}
