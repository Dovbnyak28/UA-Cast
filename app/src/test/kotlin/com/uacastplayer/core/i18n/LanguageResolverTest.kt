package com.uacastplayer.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageResolverTest {

    @Test
    fun `fromStoredCode returns matching language`() {
        assertEquals(AppLanguage.UKRAINIAN, LanguageResolver.fromStoredCode("uk"))
        assertEquals(AppLanguage.RUSSIAN, LanguageResolver.fromStoredCode("ru"))
        assertEquals(AppLanguage.SPANISH, LanguageResolver.fromStoredCode("es"))
        assertEquals(AppLanguage.ENGLISH, LanguageResolver.fromStoredCode("en"))
    }

    @Test
    fun `fromStoredCode is case insensitive`() {
        assertEquals(AppLanguage.UKRAINIAN, LanguageResolver.fromStoredCode("UK"))
    }

    @Test
    fun `fromStoredCode falls back to default for null`() {
        assertEquals(AppLanguage.DEFAULT, LanguageResolver.fromStoredCode(null))
    }

    @Test
    fun `fromStoredCode falls back to default for blank`() {
        assertEquals(AppLanguage.DEFAULT, LanguageResolver.fromStoredCode("   "))
    }

    @Test
    fun `fromStoredCode falls back to default for unsupported code`() {
        assertEquals(AppLanguage.DEFAULT, LanguageResolver.fromStoredCode("fr"))
    }

    @Test
    fun `fromDeviceLocales matches first supported tag`() {
        assertEquals(
            AppLanguage.UKRAINIAN,
            LanguageResolver.fromDeviceLocales(listOf("fr-FR", "uk-UA", "en-US"))
        )
    }

    @Test
    fun `fromDeviceLocales matches region-less tag`() {
        assertEquals(AppLanguage.RUSSIAN, LanguageResolver.fromDeviceLocales(listOf("ru")))
    }

    @Test
    fun `fromDeviceLocales matches underscore separated tag`() {
        assertEquals(AppLanguage.SPANISH, LanguageResolver.fromDeviceLocales(listOf("es_ES")))
    }

    @Test
    fun `fromDeviceLocales falls back to default when nothing matches`() {
        assertEquals(AppLanguage.DEFAULT, LanguageResolver.fromDeviceLocales(listOf("fr-FR", "de-DE")))
    }

    @Test
    fun `fromDeviceLocales falls back to default for empty list`() {
        assertEquals(AppLanguage.DEFAULT, LanguageResolver.fromDeviceLocales(emptyList()))
    }
}
