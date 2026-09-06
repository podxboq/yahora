/*
 * Copyright (C) 2026 podxboq
 *
 * This file is part of Yahora.
 *
 * Yahora is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.podxboq.yahora

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store listing is data F-Droid reads straight out of this repository, and
 * nothing else in the build looks at it: a description over the limit or a
 * release with no changelog is only found out when the merge request is
 * reviewed, days later. So the limits are pinned here instead.
 *
 * Every path is relative to the module directory, which is where Gradle runs
 * unit tests from.
 */
class FdroidMetadataTest {

    private val metadata = File("../fastlane/metadata/android")

    /** Locales F-Droid is expected to find. The first one is the fallback. */
    private val locales = listOf("en-US", "es-ES")

    // Limits F-Droid and the Play listing agree on.
    private val titleLimit = 30
    private val shortDescriptionLimit = 80
    private val fullDescriptionLimit = 4000
    private val changelogLimit = 500

    /**
     * Screenshots are PNG, not any format F-Droid would take. Cropping the
     * system bars off re-encodes the image, and a second round of JPEG lands on
     * flat colour and thin type — precisely what it handles worst.
     */
    private val screenshotFormat = "png"

    @Test
    fun `every locale carries the whole listing`() {
        for (locale in locales) {
            for (name in listOf("title.txt", "short_description.txt", "full_description.txt")) {
                val file = File(metadata, "$locale/$name")
                assertTrue("missing $locale/$name", file.isFile)
                assertTrue("empty $locale/$name", file.readText().isNotBlank())
            }
        }
    }

    @Test
    fun `titles stay within the listing limit`() {
        for (locale in locales) {
            val title = File(metadata, "$locale/title.txt").readText().trim()

            assertTrue("$locale title is ${title.length} characters", title.length <= titleLimit)
        }
    }

    @Test
    fun `short descriptions are one plain line within the limit`() {
        for (locale in locales) {
            val short = File(metadata, "$locale/short_description.txt").readText().trim()

            assertTrue("$locale is ${short.length} characters", short.length <= shortDescriptionLimit)
            assertTrue("$locale spans several lines", !short.contains('\n'))
            assertTrue("$locale ends in a full stop", !short.endsWith("."))
        }
    }

    @Test
    fun `full descriptions stay within the limit`() {
        for (locale in locales) {
            val full = File(metadata, "$locale/full_description.txt").readText().trim()

            assertTrue("$locale is ${full.length} characters", full.length <= fullDescriptionLimit)
        }
    }

    /**
     * A release with no changelog reaches users as a blank "what's new", so the
     * file is named after the version code the build declares — bumping one
     * without the other fails here rather than in front of the reviewer.
     */
    @Test
    fun `the version being built has a changelog in every locale`() {
        val versionCode = declaredVersionCode()

        for (locale in locales) {
            val changelog = File(metadata, "$locale/changelogs/$versionCode.txt")

            assertTrue("missing $locale changelog for version code $versionCode", changelog.isFile)
            val text = changelog.readText().trim()
            assertTrue("empty $locale changelog", text.isNotEmpty())
            assertTrue("$locale changelog is ${text.length} characters", text.length <= changelogLimit)
        }
    }

    /**
     * The listing icon is a PNG that has to be square and 512 pixels wide, and
     * a lone icon is exactly the kind of file nobody re-checks after editing
     * the drawing it came from.
     */
    @Test
    fun `the listing icon is a 512 pixel square PNG`() {
        val icon = File(metadata, "${locales.first()}/images/icon.png")
        assertTrue("missing icon.png", icon.isFile)

        val (width, height) = pngSize(icon)

        assertEquals(512, width)
        assertEquals(512, height)
    }

    /**
     * The feature graphic is Play's alone — F-Droid never asks for one — and
     * Play refuses anything that is not exactly 1024x500. It carries the app's
     * name and its own locale's tagline, so it is per-locale like the
     * screenshots and unlike the icon, and `tools/render-feature-graphic.py`
     * draws it from the launcher icon's drawables and the short description.
     * Regenerate it whenever either of those changes; a graphic that fell out
     * of step is not something the build would otherwise notice.
     */
    @Test
    fun `every locale carries a 1024 by 500 feature graphic`() {
        for (locale in locales) {
            val graphic = File(metadata, "$locale/images/featureGraphic.png")
            assertTrue("missing $locale/images/featureGraphic.png", graphic.isFile)

            val (width, height) = pngSize(graphic)

            assertEquals("$locale feature graphic width", 1024, width)
            assertEquals("$locale feature graphic height", 500, height)
        }
    }

    /**
     * Screenshots are the one part of the listing that cannot be rewritten from
     * a text editor: they have to be taken again on a device, with the demo data
     * and the language set up as they were. That makes a missing one expensive
     * and easy to miss, since an empty directory is not even a change git shows.
     */
    @Test
    fun `every locale shows screenshots`() {
        for (locale in locales) {
            val screenshots = screenshotsOf(locale)

            assertTrue("no phone screenshots for $locale", screenshots.isNotEmpty())
            for (screenshot in screenshots) {
                assertEquals(
                    "$locale/${screenshot.name} is not a $screenshotFormat",
                    screenshotFormat,
                    screenshot.extension.lowercase(),
                )
                assertTrue("empty $locale/${screenshot.name}", screenshot.length() > 0)
            }
        }
    }

    /**
     * The listing shows screenshots in file name order, so matching names across
     * locales are what makes the second listing the first one translated rather
     * than a different tour of the app. It also catches the likelier accident:
     * adding a screen to one locale and forgetting the other.
     */
    @Test
    fun `the locales show the same screens`() {
        val fallback = locales.first()
        val expected = screenshotsOf(fallback).map { it.name }

        for (locale in locales.drop(1)) {
            assertEquals(
                "$locale does not show the same screens as $fallback",
                expected,
                screenshotsOf(locale).map { it.name },
            )
        }
    }

    /**
     * Every screenshot is cropped to the app's own frame, with the phone's
     * status and navigation bars taken off by `tools/crop-screenshot.py`. One
     * that skipped the crop is taller than the rest, which is the shape this
     * catches: the listing would show someone's clock and battery next to
     * screens that do not, and the two locales would stop lining up.
     */
    @Test
    fun `screenshots are cropped to one portrait frame`() {
        val screenshots = locales.flatMap { screenshotsOf(it) }
        val sizes = screenshots.associate { it.name to pngSize(it) }

        val frame = sizes.values.first()
        assertTrue("$frame is not portrait", frame.second > frame.first)
        for ((name, size) in sizes) {
            assertEquals("$name was not cropped to the same frame", frame, size)
        }
    }

    /** Phone screenshots of one locale, in the order the listing shows them. */
    private fun screenshotsOf(locale: String): List<File> =
        File(metadata, "$locale/images/phoneScreenshots")
            .listFiles()
            ?.filter { it.isFile && !it.isHidden }
            ?.sortedBy { it.name }
            .orEmpty()

    /**
     * The recipe kept here is the copy submitted to fdroiddata, and it repeats
     * the version the build declares. Bumping one without the other publishes a
     * release whose recipe describes the previous one, which F-Droid finds out
     * long after the merge request was opened.
     */
    @Test
    fun `the recipe describes the version being built`() {
        val recipe = recipeText()

        assertEquals(
            "recipe versionName disagrees with the build",
            declared("versionName\\s*=\\s*\"([^\"]+)\"", buildScript()),
            declared("versionName:\\s*(\\S+)", recipe),
        )
        assertEquals(
            "recipe versionCode disagrees with the build",
            declaredVersionCode().toString(),
            declared("versionCode:\\s*(\\d+)", recipe),
        )
        assertEquals(
            "CurrentVersionCode disagrees with the build",
            declaredVersionCode().toString(),
            declared("CurrentVersionCode:\\s*(\\d+)", recipe),
        )
    }

    /**
     * Reproducible builds hang on two fields agreeing with reality: F-Droid
     * fetches the APK named by `Binaries` and refuses it unless it carries the
     * key named by `AllowedAPKSigningKeys`. A URL that does not interpolate the
     * version would keep pointing at the first release forever.
     */
    @Test
    fun `the published binary is pinned to a signing key`() {
        val recipe = recipeText()

        val binaries = declared("Binaries:\\s*(\\S+)", recipe)
        assertTrue("Binaries does not interpolate the version: $binaries", binaries.contains("%v"))

        val key = declared("AllowedAPKSigningKeys:\\s*(\\S+)", recipe)
        assertTrue(
            "AllowedAPKSigningKeys is not a lowercase SHA-256: $key",
            key.matches(Regex("[0-9a-f]{64}")),
        )
    }

    /** The build recipe, kept beside the app it describes. */
    private fun recipeText(): String = File("../fdroid/com.podxboq.yahora.yml").readText()

    private fun buildScript(): String = File("build.gradle.kts").readText()

    private fun declared(pattern: String, text: String): String =
        requireNonNull(Regex(pattern).find(text)) { "nothing matching $pattern" }.groupValues[1]

    /** Reads `versionCode` out of the build script, the one place that declares it. */
    private fun declaredVersionCode(): Int =
        declared("""versionCode\s*=\s*(\d+)""", buildScript()).toInt()

    /** The IHDR chunk of a PNG: width and height as big-endian ints at byte 16. */
    private fun pngSize(file: File): Pair<Int, Int> {
        val header = file.readBytes().copyOfRange(0, 24)
        fun intAt(offset: Int) = (0 until 4).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }

        return intAt(16) to intAt(20)
    }

    private fun <T : Any> requireNonNull(value: T?, message: () -> String): T =
        value ?: throw AssertionError(message())
}
