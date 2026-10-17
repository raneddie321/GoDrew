package com.godraw.app.localization

import android.app.Application
import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "en")
    fun englishIsLeftToRight() {
        assertEquals(View.LAYOUT_DIRECTION_LTR, context.resources.configuration.layoutDirection)
        assertEquals("New Drawing", context.getString(R.string.new_drawing))
        assertEquals("Layer 3", context.getString(R.string.layer_default_name, 3))
    }

    @Test
    @Config(qualifiers = "iw")
    fun hebrewIsRightToLeft() {
        assertEquals(View.LAYOUT_DIRECTION_RTL, context.resources.configuration.layoutDirection)
        assertEquals("ציור חדש", context.getString(R.string.new_drawing))
        assertEquals("שכבה 3", context.getString(R.string.layer_default_name, 3))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicIsRightToLeftWithPlurals() {
        assertEquals(View.LAYOUT_DIRECTION_RTL, context.resources.configuration.layoutDirection)
        assertEquals("رسم جديد", context.getString(R.string.new_drawing))
        assertEquals("إطاران", context.resources.getQuantityString(R.plurals.frame_count, 2, 2))
    }

    @Test
    @Config(qualifiers = "es")
    fun spanishStrings() {
        assertEquals("Nuevo dibujo", context.getString(R.string.new_drawing))
        assertEquals(5, context.resources.getStringArray(R.array.tut_first_drawing_steps).size)
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchStrings() {
        assertEquals("Nouveau dessin", context.getString(R.string.new_drawing))
        assertNotEquals(context.getString(R.string.export_done, "a"), "Exported a")
    }
}
