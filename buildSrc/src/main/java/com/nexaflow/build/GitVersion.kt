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
 * **versionCode** – monotonically increasing integer for semantic release lines:
 *   - Major×100000000 + Minor×100000 + Patch×100 + distanceSinceTag
 *   - Supports major 0..20, minor/patch 0..999, and 0..99 commits after a tag
 *   - Unsupported ranges fail loudly instead of silently colliding
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
        val patch = distanceMatch.groupValues[3].toInt()
        val distance = distanceMatch.groupValues[4].toInt()
        return GitVersionInfo(
            tag, // already prefixed with "v"
            encodeVersionCode(major, minor, patch, distance)
        )
    }

    // 2) Tag-only commit: v3.26.0-alpha
    val tagOnlyPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-.+)?$")
    val tagOnly = tagOnlyPattern.find(tag)
    if (tagOnly != null) {
        val major = tagOnly.groupValues[1].toInt()
        val minor = tagOnly.groupValues[2].toInt()
        val patch = tagOnly.groupValues[3].toInt()
        val suffix = tag.substringAfter("${major}.${minor}.${patch}")
        return GitVersionInfo(
            "v${major}.${minor}.${patch}$suffix",
            encodeVersionCode(major, minor, patch, 0)
        )
    }

    // 3) Fallback: detached HEAD or unusual format
    val hash = tag.take(7)
    return GitVersionInfo("0.0.1-$hash", 1)
}


internal fun encodeVersionCode(
    major: Int,
    minor: Int,
    patch: Int,
    distanceSinceTag: Int = 0
): Int {
    require(major in 0..20) { "major version must be between 0 and 20" }
    require(minor in 0..999) { "minor version must be between 0 and 999" }
    require(patch in 0..999) { "patch version must be between 0 and 999" }
    require(distanceSinceTag in 0..99) {
        "more than 99 commits since the nearest release tag; create a new semantic release tag"
    }

    val code =
        major * 100_000_000 +
            minor * 100_000 +
            patch * 100 +
            distanceSinceTag
    require(code in 1..2_100_000_000) { "versionCode is outside Android's supported range" }
    return code
}

private fun Project.runGit(vararg args: String): String {
    // Gradle 9 removed `Project.exec`; `providers.exec` is the supported
    // replacement and returns the captured output as a provider.
    return providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.orNull?.trim().orEmpty()
}
