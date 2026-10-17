package com.godraw.core.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates the app's string resources: every supported language must define every key, with
 * the same format placeholders, complete plurals and string arrays, and valid escaping.
 */
class LocalizationTest {

    private val resDir = File(System.getProperty("godraw.resDir") ?: "../app/src/main/res")
    private val locales = mapOf("he" to "values-iw", "ar" to "values-ar", "es" to "values-es", "fr" to "values-fr")
    private val placeholder = Regex("%(\\d+\\$)?[sdf]|%%")

    private class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
        val raw: String,
    )

    private fun load(folder: String): Resources {
        val file = File(resDir, "$folder/strings.xml")
        assumeTrue("resources not found at $file", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        fun elements(tag: String) = root.getElementsByTagName(tag).let { nl -> (0 until nl.length).map { nl.item(it) as Element } }
        val strings = elements("string")
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
        val plurals = elements("plurals").associate { p ->
            val items = p.getElementsByTagName("item")
            p.getAttribute("name") to (0 until items.length).associate {
                val e = items.item(it) as Element
                e.getAttribute("quantity") to e.textContent
            }
        }
        val arrays = elements("string-array").associate { a ->
            val items = a.getElementsByTagName("item")
            a.getAttribute("name") to (0 until items.length).map { items.item(it).textContent }
        }
        return Resources(strings, plurals, arrays, file.readText())
    }

    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageDefinesEveryString() {
        val en = load("values")
        assertTrue("expected a full English string table", en.strings.size > 500)
        for ((lang, folder) in locales) {
            val tr = load(folder)
            val missing = en.strings.keys - tr.strings.keys
            val extra = tr.strings.keys - en.strings.keys
            assertTrue("$lang is missing $missing", missing.isEmpty())
            assertTrue("$lang has unknown keys $extra", extra.isEmpty())
            assertEquals("$lang plurals", en.plurals.keys, tr.plurals.keys)
            assertEquals("$lang arrays", en.arrays.keys, tr.arrays.keys)
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        val en = load("values")
        for ((lang, folder) in locales) {
            val tr = load(folder)
            for ((key, value) in en.strings) {
                assertEquals("$lang/$key placeholders", placeholders(value), placeholders(tr.strings.getValue(key)))
            }
        }
    }

    @Test
    fun pluralsAndArraysAreComplete() {
        val en = load("values")
        for ((lang, folder) in locales) {
            val tr = load(folder)
            tr.plurals.forEach { (name, forms) -> assertTrue("$lang/$name needs 'other'", "other" in forms) }
            en.arrays.forEach { (name, items) ->
                assertEquals("$lang/$name length", items.size, tr.arrays.getValue(name).size)
                tr.arrays.getValue(name).forEach { assertTrue("$lang/$name has an empty step", it.isNotBlank()) }
            }
        }
    }

    @Test
    fun apostrophesAndQuotesAreEscaped() {
        for (folder in listOf("values") + locales.values) {
            val raw = load(folder).raw
            // Inside element text, Android requires \' and \" (unless the whole value is quoted).
            Regex(">([^<]*)<").findAll(raw).forEach { m ->
                val text = m.groupValues[1]
                val inner = if (text.startsWith("\"") && text.endsWith("\"") && text.length > 1) text.substring(1, text.length - 1) else text
                Regex("(?<!\\\\)['\"]").find(inner)?.let { error("$folder: unescaped quote in: $text") }
            }
        }
    }

    @Test
    fun rtlLanguagesAreActuallyTranslated() {
        val en = load("values")
        for (folder in listOf("values-iw", "values-ar")) {
            val tr = load(folder)
            val translated = tr.strings.count { (k, v) -> v != en.strings[k] }
            assertTrue("$folder looks untranslated ($translated)", translated > en.strings.size * 0.8)
            val rtlChars = tr.strings.values.count { s -> s.any { Character.getDirectionality(it).let { d -> d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC } } }
            assertTrue("$folder should contain right-to-left text", rtlChars > en.strings.size / 2)
        }
    }
}
