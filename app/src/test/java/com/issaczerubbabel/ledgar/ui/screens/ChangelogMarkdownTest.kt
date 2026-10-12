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

    /**
     * The writing rules in CLAUDE.md ("Changelog style"), checked so every new entry stays short and
     * scannable. Each failure names the entry and the rule it breaks.
     */
    @Test
    fun entriesFollowTheChangelogStyle() {
        val problems = mutableListOf<String>()
        changelogReleases.forEach { release ->
            val sections = mapOf(
                "added" to release.added,
                "changed" to release.changed,
                "fixed" to release.fixed,
                "developer" to release.developer
            )
            if (sections.values.all { it.isEmpty() }) problems += "${release.version} has no entries"
            sections.forEach { (section, items) ->
                if (items.size > MAX_ITEMS_PER_SECTION) {
                    problems += "${release.version} $section has ${items.size} entries (at most $MAX_ITEMS_PER_SECTION): merge related ones"
                }
                val limit = if (section == "developer") MAX_DEVELOPER_LENGTH else MAX_USER_LENGTH
                items.forEach { item ->
                    val where = "${release.version} $section \"$item\""
                    if (item.length > limit) problems += "$where is ${item.length} characters (at most $limit)"
                    if (item.trim() != item || "  " in item || '\n' in item) problems += "$where has stray whitespace"
                    if (item.endsWith(".")) problems += "$where ends with a full stop"
                    if (Regex("""\. \S""").findAll(item).count() > 1) problems += "$where has more than two sentences"
                    if (section != "developer" && !item.first().isUpperCase()) problems += "$where should start with a capital letter"
                }
            }
        }
        assertTrue(problems.joinToString("\n", prefix = "Changelog style problems:\n"), problems.isEmpty())
    }

    @Test
    fun noEntryIsListedTwice() {
        val seen = mutableMapOf<String, String>()
        val repeats = mutableListOf<String>()
        changelogReleases.forEach { release ->
            (release.added + release.changed + release.fixed + release.developer).forEach { item ->
                val key = item.lowercase().trim()
                seen[key]?.let { repeats += "\"$item\" is in both $it and ${release.version}" }
                seen.putIfAbsent(key, release.version)
            }
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    @Test
    fun releaseDatesAreValidAndNewestFirst() {
        val dates = changelogReleases.map { release ->
            assertTrue("${release.version} date ${release.date} is not yyyy-MM-dd", Regex("""\d{4}-\d{2}-\d{2}""").matches(release.date))
            java.time.LocalDate.parse(release.date)
        }
        assertEquals("Release dates must not increase down the list", dates.sortedDescending(), dates)
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

    private companion object {
        const val MAX_USER_LENGTH = 110
        const val MAX_DEVELOPER_LENGTH = 140
        const val MAX_ITEMS_PER_SECTION = 16
    }
}
