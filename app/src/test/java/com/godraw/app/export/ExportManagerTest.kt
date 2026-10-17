package com.godraw.app.export

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.entitlement.Tier
import com.godraw.core.model.CellKey
import com.godraw.core.model.Frame
import com.godraw.core.model.ProjectDocument
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun animation(): Pair<ProjectDocument, Map<CellKey, Bitmap>> {
        val base = ProjectFactory.create("Export test", ProjectType.ANIMATION, 120, 80, Color.WHITE, fps = 12)
        val doc = base.copy(frames = base.frames + Frame("f2") + Frame("f3", hold = 2))
        val layer = doc.layers.last()
        val cells = doc.frames.mapIndexed { i, f ->
            CellKey.forLayer(layer, f.id) to Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until 20) for (y in 0 until 20) setPixel(10 + i * 30 + x, 30 + y, Color.RED)
            }
        }.toMap()
        return doc to cells
    }

    private fun manager(cells: Map<CellKey, Bitmap>) = ExportManager(context, { cells[it] }, { File("missing") })

    @Test
    fun pngExportWritesAVerifiedFile() = runBlocking {
        val (doc, cells) = animation()
        val out = tmp.newFile("frame.png")
        val result = manager(cells).export(doc, ExportOptions(ExportFormat.PNG, frameIndex = 1), FeatureGate(Tier.FREE), Uri.fromFile(out)) {}
        assertTrue(out.length() > 0)
        assertEquals(out.length(), result.sizeBytes)
        val bmp = BitmapFactory.decodeFile(out.path)
        assertEquals(120, bmp.width)
        assertEquals(Color.RED, bmp.getPixel(45, 40))
        assertEquals(Color.WHITE, bmp.getPixel(5, 5))
    }

    @Test
    fun transparentPngDropsTheBackground() = runBlocking {
        val (doc, cells) = animation()
        val out = tmp.newFile("t.png")
        manager(cells).export(doc, ExportOptions(ExportFormat.PNG_TRANSPARENT), FeatureGate(Tier.FREE), Uri.fromFile(out)) {}
        val bmp = BitmapFactory.decodeFile(out.path)
        assertEquals(0, Color.alpha(bmp.getPixel(5, 5)))
        assertEquals(255, Color.alpha(bmp.getPixel(15, 35)))
    }

    @Test
    fun jpgExportScalesTheCanvas() = runBlocking {
        val (doc, cells) = animation()
        val out = tmp.newFile("half.jpg")
        manager(cells).export(doc, ExportOptions(ExportFormat.JPG, scale = 0.5f, quality = 80), FeatureGate(Tier.FREE), Uri.fromFile(out)) {}
        val bmp = BitmapFactory.decodeFile(out.path)
        assertEquals(60, bmp.width)
        assertEquals(40, bmp.height)
    }

    @Test
    fun gifExportContainsEveryFrame() = runBlocking {
        val (doc, cells) = animation()
        val out = tmp.newFile("anim.gif")
        var lastProgress = 0f
        manager(cells).export(doc, ExportOptions(ExportFormat.GIF, fps = 12), FeatureGate(Tier.FREE), Uri.fromFile(out)) { lastProgress = it }
        assertEquals(1f, lastProgress, 0.001f)
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(out)
        assertEquals(3, reader.getNumImages(true))
        assertEquals(120, reader.read(0).width)
    }

    @Test
    fun pngSequenceRequiresPro() = runBlocking {
        val (doc, cells) = animation()
        val out = tmp.newFile("seq.zip")
        try {
            manager(cells).export(doc, ExportOptions(ExportFormat.PNG_SEQUENCE), FeatureGate(Tier.FREE), Uri.fromFile(out)) {}
            fail("PNG sequence must be locked on Free")
        } catch (e: ExportException) {
            assertEquals(ExportError.FEATURE_LOCKED, e.error)
        }
        manager(cells).export(doc, ExportOptions(ExportFormat.PNG_SEQUENCE), FeatureGate(Tier.PRO), Uri.fromFile(out)) {}
        val entries = java.util.zip.ZipFile(out).use { z -> z.entries().toList().size }
        assertEquals(3, entries)
    }

    @Test
    fun exportSizeFollowsThePlan() {
        val big = ProjectFactory.create("Big", ProjectType.DRAWING, 3840, 2160, Color.WHITE)
        val m = manager(emptyMap())
        assertEquals(1920 to 1080, m.outputSize(big, ExportOptions(ExportFormat.PNG), FeatureGate(Tier.FREE)))
        assertEquals(2560 to 1440, m.outputSize(big, ExportOptions(ExportFormat.PNG), FeatureGate(Tier.PRO)))
        assertEquals(3840 to 2160, m.outputSize(big, ExportOptions(ExportFormat.PNG), FeatureGate(Tier.MAX)))
    }
}
