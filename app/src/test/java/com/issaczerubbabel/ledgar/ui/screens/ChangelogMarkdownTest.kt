package com.issaczerubbabel.ledgar.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CHANGELOG.md is ChangelogData.kt rendered as Keep a Changelog Markdown. The release workflow
 * publishes a version's section as its GitHub Release notes, so the two must not drift.
 *
 * After editing ChangelogData.kt, regenerate the file with:
 *   UPDATE_CHANGELOG=1 ./gradlew testDebugUnitTest --tests "*ChangelogMarkdownTest"
 */
class ChangelogMarkdownTest {

    private val file = File("../CHANGELOG.md").takeIf { it.exists() } ?: File("CHANGELOG.md")

    @Test
    fun changelogMarkdownMatchesTheInAppChangelog() {
        val expected = render(changelogReleases)
        if (System.getenv("UPDATE_CHANGELOG") == "1") file.writeText(expected)
        assertEquals(
            "CHANGELOG.md is out of date. Regenerate it: UPDATE_CHANGELOG=1 ./gradlew testDebugUnitTest --tests \"*ChangelogMarkdownTest\"",
            expected,
            file.readText().replace("\r\n", "\n")
        )
    }

    @Test
    fun releasesAreNewestFirstWithSemanticVersions() {
        val versions = changelogReleases.map { it.version }
        versions.forEach { assertTrue("$it is not vX.Y.Z", Regex("""v\d+\.\d+\.\d+""").matches(it)) }
        val sorted = versions.sortedWith(compareByDescending<String> { v -> v.removePrefix("v").split(".").map(String::toInt).let { it[0] * 1_000_000 + it[1] * 1_000 + it[2] } })
        assertEquals(sorted, versions)
    }

    @Test
    fun theNewestReleaseMatchesTheAppVersion() {
        val gradle = (File("build.gradle.kts").takeIf { it.exists() } ?: File("app/build.gradle.kts")).readText()
        val versionName = Regex("""val appVersionName = "([^"]+)"""").find(gradle)!!.groupValues[1]
        assertEquals("v$versionName", changelogReleases.first().version)
    }

    private fun render(releases: List<ChangelogRelease>): String = buildString {
        appendLine("# Changelog")
        appendLine()
        appendLine("All notable changes to L.Edgar. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),")
        appendLine("and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).")
        appendLine()
        appendLine("This file is generated from `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt`,")
        appendLine("which is also what the app shows under More > Changelog. Developer notes appear here only.")
        releases.forEach { release ->
            appendLine()
            appendLine("## [${release.version.removePrefix("v")}] - ${release.date}")
            section("Added", release.added)
            section("Changed", release.changed)
            section("Fixed", release.fixed)
            section("Developer", release.developer)
        }
        appendLine()
        releases.forEach { release ->
            appendLine("[${release.version.removePrefix("v")}]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/${release.version}")
        }
    }

    private fun StringBuilder.section(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine()
        appendLine("### $title")
        appendLine()
        items.forEach { appendLine("- $it") }
    }
}
