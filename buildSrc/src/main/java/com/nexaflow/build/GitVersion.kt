package com.nexaflow.build

import org.gradle.api.Project

data class GitVersionInfo(val versionName: String, val versionCode: Int)

fun Project.gitVersion(): GitVersionInfo {
    val tag = runGit("describe", "--tags", "--always", "--match", "v[0-9]*")
        .ifBlank { return GitVersionInfo("0.0.1-unknown", 1) }

    val distancePattern = Regex("^(?:v?)(\\d+)\\.(\\d+)\\.(\\d+)(?:-.+?)?-(\\d+)-g([0-9a-f]+)$")
    val distanceMatch = distancePattern.find(tag)
    if (distanceMatch != null) {
        val major = distanceMatch.groupValues[1].toInt()
        val minor = distanceMatch.groupValues[2].toInt()
        val patch = distanceMatch.groupValues[3].toInt().coerceAtMost(9)
        val distance = distanceMatch.groupValues[4].toInt()
        return GitVersionInfo(tag, major * 100_000 + minor * 1_000 + patch * 10 + distance)
    }

    val versionPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")
    val versionMatch = versionPattern.find(tag)
    if (versionMatch != null) {
        val major = versionMatch.groupValues[1].toInt()
        val minor = versionMatch.groupValues[2].toInt()
        val patch = versionMatch.groupValues[3].toInt().coerceAtMost(9)
        return GitVersionInfo(tag, major * 100_000 + minor * 1_000 + patch * 10)
    }

    return GitVersionInfo("0.0.1-${tag.takeLast(7)}", 1)
}

private fun Project.runGit(vararg args: String): String =
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
