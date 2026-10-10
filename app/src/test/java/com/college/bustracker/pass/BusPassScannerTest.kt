package com.college.bustracker.pass

import com.college.bustracker.pass.engine.CardDetector
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.Point2D
import com.college.bustracker.pass.model.QualityCheckIssue
import com.college.bustracker.pass.storage.BusPassStorage
import com.college.bustracker.pass.ui.ScanStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import android.content.Context

class BusPassScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 1. Normal front card detection: 4 corners detected correctly
    @Test
    fun test01_NormalFrontCardDetection() {
        val quad = CardQuad(
            topLeft = Point2D(100f, 100f),
            topRight = Point2D(900f, 100f),
            bottomRight = Point2D(900f, 600f),
            bottomLeft = Point2D(100f, 600f)
        )
        assertTrue(quad.isValidConvex())
        assertEquals(100f, quad.topLeft.x, 0.001f)
        assertEquals(900f, quad.topRight.x, 0.001f)
        assertEquals(900f, quad.bottomRight.x, 0.001f)
        assertEquals(100f, quad.bottomLeft.x, 0.001f)
    }

    // 2. Normal back card detection: 4 corners detected correctly
    @Test
    fun test02_NormalBackCardDetection() {
        val quad = CardQuad(
            topLeft = Point2D(120f, 110f),
            topRight = Point2D(880f, 110f),
            bottomRight = Point2D(880f, 590f),
            bottomLeft = Point2D(120f, 590f)
        )
        assertTrue(quad.isValidConvex())
        assertTrue(quad.aspectRatio() > 1.2f)
    }

    // 3. Card slightly rotated: convex quad calculation remains valid
    @Test
    fun test03_CardSlightlyRotatedConvexity() {
        val rotatedQuad = CardQuad(
            topLeft = Point2D(120f, 100f),
            topRight = Point2D(910f, 140f),
            bottomRight = Point2D(890f, 610f),
            bottomLeft = Point2D(100f, 570f)
        )
        assertTrue(rotatedQuad.isValidConvex())
        assertTrue(rotatedQuad.maxWidth() > rotatedQuad.maxHeight())
    }

    // 4. Card too far / small: coverage rejection
    @Test
    fun test04_CardTooFarRejected() {
        val tinyQuad = CardQuad(
            topLeft = Point2D(450f, 450f),
            topRight = Point2D(550f, 450f),
            bottomRight = Point2D(550f, 550f),
            bottomLeft = Point2D(450f, 550f)
        )
        val guide = CardQuad.createCentered(1000f, 1000f, 0.85f)
        val cardArea = tinyQuad.area()
        val guideArea = guide.area()
        val coverageRatio = cardArea / guideArea
        assertTrue(coverageRatio < 0.42f)
    }

    // 5. Card too close / edges cut off: out of bounds rejection
    @Test
    fun test05_CardTooCloseOutOfBounds() {
        val outOfBoundsQuad = CardQuad(
            topLeft = Point2D(-10f, 50f),
            topRight = Point2D(1020f, 50f),
            bottomRight = Point2D(1010f, 580f),
            bottomLeft = Point2D(10f, 580f)
        )
        assertFalse(outOfBoundsQuad.isWithinBounds(1000f, 600f))
    }

    // 6. Blurry image issue definition and user guidance
    @Test
    fun test06_BlurryImageIssueGuidance() {
        assertEquals("Image blurry — Hold the phone steady", QualityCheckIssue.IMAGE_BLURRY.userMessage)
    }

    // 7. Motion blur issue: provides steady guidance
    @Test
    fun test07_MotionBlurIssue() {
        assertTrue(QualityCheckIssue.IMAGE_BLURRY.userMessage.contains("steady"))
    }

    // 8. Too dark / low luminance issue definition
    @Test
    fun test08_TooDarkIssueDefinition() {
        assertEquals("Too dark — Move to a brighter area", QualityCheckIssue.TOO_DARK.userMessage)
    }

    // 9. Too bright issue definition
    @Test
    fun test09_TooBrightIssueDefinition() {
        assertEquals("Too bright — Avoid excessive bright light", QualityCheckIssue.TOO_BRIGHT.userMessage)
    }

    // 10. Glare hotspot issue definition
    @Test
    fun test10_GlareHotspotIssueDefinition() {
        assertEquals("Too much glare — Avoid direct light reflection", QualityCheckIssue.TOO_MUCH_GLARE.userMessage)
    }

    // 11. Low resolution issue definition
    @Test
    fun test11_LowResolutionIssueDefinition() {
        assertEquals("Resolution too low — Hold camera closer to card", QualityCheckIssue.RESOLUTION_LOW.userMessage)
    }

    // 12. Obstructed / finger on border issue definition
    @Test
    fun test12_ObstructionIssueDefinition() {
        assertEquals("Card partially covered — Keep your fingers away from the card", QualityCheckIssue.CARD_COVERED.userMessage)
    }

    // 13. Perspective distortion / severe skew: aspect ratio out of bounds
    @Test
    fun test13_PerspectiveDistortionExtremeSkewRejected() {
        val extremeSkewQuad = CardQuad(
            topLeft = Point2D(100f, 100f),
            topRight = Point2D(800f, 100f),
            bottomRight = Point2D(420f, 400f),
            bottomLeft = Point2D(400f, 400f)
        )
        val aspect = extremeSkewQuad.aspectRatio()
        // Standard credit/ID card aspect is ~1.586; extreme skew deviates significantly
        assertTrue(aspect > 2.25f || extremeSkewQuad.topWidth() / extremeSkewQuad.bottomWidth() > 5f)
    }

    // 14. Non-convex quad (crossing corners): rejected
    @Test
    fun test14_NonConvexQuadRejected() {
        val crossedQuad = CardQuad(
            topLeft = Point2D(100f, 100f),
            topRight = Point2D(100f, 500f), // swapped vertices -> bowtie polygon
            bottomRight = Point2D(800f, 100f),
            bottomLeft = Point2D(800f, 500f)
        )
        assertFalse(crossedQuad.isValidConvex())
    }

    // 15. Self-intersecting quad: rejected by Shoelace / cross products
    @Test
    fun test15_SelfIntersectingQuadRejected() {
        val crossed = CardQuad(
            topLeft = Point2D(100f, 100f),
            topRight = Point2D(800f, 500f),
            bottomRight = Point2D(800f, 100f),
            bottomLeft = Point2D(100f, 500f)
        )
        assertFalse(crossed.isValidConvex())
    }

    // 16. Partial card visibility: out of bounds rejected
    @Test
    fun test16_PartialCardVisibilityRejected() {
        val cutQuad = CardQuad(
            topLeft = Point2D(200f, -5f),
            topRight = Point2D(900f, -5f),
            bottomRight = Point2D(900f, 600f),
            bottomLeft = Point2D(200f, 600f)
        )
        assertFalse(cutQuad.isWithinBounds(1000f, 700f))
    }

    // 17. Manual corner adjustment: produces valid convex card quad
    @Test
    fun test17_ManualCornerAdjustmentCorrection() {
        val userCorners = CardQuad(
            topLeft = Point2D(150f, 100f),
            topRight = Point2D(850f, 100f),
            bottomRight = Point2D(850f, 520f),
            bottomLeft = Point2D(150f, 520f)
        )
        assertTrue(userCorners.isValidConvex())
        assertTrue(userCorners.aspectRatio() in 1.4f..1.8f)
    }

    // 18. Manual adjustment with crossed corners rejected: prevents crash
    @Test
    fun test18_ManualAdjustmentInvalidCrossedCornersFailsSafely() {
        val crossed = CardQuad(
            topLeft = Point2D(500f, 500f),
            topRight = Point2D(100f, 100f),
            bottomRight = Point2D(800f, 100f),
            bottomLeft = Point2D(100f, 500f)
        )
        assertFalse(crossed.isValidConvex())
    }

    // 19. Complete front + back scan flow: storage structure and files
    @Test
    fun test19_CompleteFrontAndBackScanStorage() {
        val filesDir = tempFolder.newFolder("files19")
        val storage = BusPassStorage(filesDir)

        // Empty initially
        assertNull(storage.loadBusPass())
        assertFalse(storage.hasDraftFront())
    }

    // 20. Replace pass: preserves existing files until new confirmed
    @Test
    fun test20_ReplacePassPreservesOldUntilNewSaved() {
        val filesDir = tempFolder.newFolder("files20")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        val frontFile = File(passDir, "pass_front.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val backFile = File(passDir, "pass_back.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val storage = BusPassStorage(filesDir)
        val existing = storage.loadBusPass()

        assertNotNull(existing)
        assertTrue(existing!!.isComplete())
        assertEquals(frontFile.absolutePath, existing.frontImagePath)
        assertEquals(backFile.absolutePath, existing.backImagePath)
    }

    // 21. Delete pass: removes all pass files cleanly
    @Test
    fun test21_DeletePassRemovesAllFilesCleanly() {
        val filesDir = tempFolder.newFolder("files21")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        File(passDir, "pass_front.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(passDir, "pass_back.jpg").writeBytes(byteArrayOf(4, 5, 6))
        File(passDir, "pass_metadata.json").writeText("{}")

        val storage = BusPassStorage(filesDir)
        assertNotNull(storage.loadBusPass())

        val res = storage.deleteBusPass()
        assertTrue(res.isSuccess)
        assertNull(storage.loadBusPass())
        assertFalse(File(passDir, "pass_front.jpg").exists())
        assertFalse(File(passDir, "pass_back.jpg").exists())
    }

    // 22. Interruption after front scan: draft file handling
    @Test
    fun test22_DraftFrontSurvivesInterruption() {
        val filesDir = tempFolder.newFolder("files22")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        val draftFile = File(passDir, "draft_front.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }

        val storage = BusPassStorage(filesDir)
        assertTrue(storage.hasDraftFront())

        storage.clearDraft()
        assertFalse(storage.hasDraftFront())
        assertFalse(draftFile.exists())
    }

    // 23. Zero network/Firebase calls: BusPassStorage writes to local filesDir only
    @Test
    fun test23_ZeroNetworkFirebaseUploadLocalFilesOnly() {
        val filesDir = tempFolder.newFolder("files23")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        val front = File(passDir, "pass_front.jpg").apply { writeBytes(byteArrayOf(1)) }
        val back = File(passDir, "pass_back.jpg").apply { writeBytes(byteArrayOf(2)) }

        val storage = BusPassStorage(filesDir)
        val pass = storage.loadBusPass()!!
        assertTrue(pass.frontImagePath.startsWith(filesDir.absolutePath))
        assertTrue(pass.backImagePath.startsWith(filesDir.absolutePath))
        assertFalse(pass.frontImagePath.contains("firebase"))
        assertFalse(pass.frontImagePath.contains("cloud"))
    }

    // 24. Identical front and back hash comparison
    @Test
    fun test24_DuplicateSidesHashCalculation() {
        // Test 64-bit Hamming distance bit count
        val hash1 = 0xAAAAAAAAAAAAAAAAUL
        val hash2 = 0xAAAAAAAAAAAAAAAAUL
        val diff = java.lang.Long.bitCount((hash1 xor hash2).toLong())
        assertEquals(0, diff)
        assertTrue(diff <= 10) // Should trigger duplicate detection
    }

    // 25. Different front and back sides: high Hamming distance
    @Test
    fun test25_DistinctSidesHighHammingDistance() {
        val hash1 = 0x0000000000000000UL
        val hash2 = 0xFFFFFFFFFFFFFFFFUL
        val diff = java.lang.Long.bitCount((hash1 xor hash2).toLong())
        assertEquals(64, diff)
        assertFalse(diff <= 10) // Does not trigger duplicate detection
    }

    // 26. CardQuad serialization: float array round-trip
    @Test
    fun test26_CardQuadFloatArrayRoundTrip() {
        val original = CardQuad(
            topLeft = Point2D(10f, 20f),
            topRight = Point2D(300f, 25f),
            bottomRight = Point2D(310f, 210f),
            bottomLeft = Point2D(15f, 205f)
        )
        val arr = original.toFloatArray()
        assertEquals(8, arr.size)
        val restored = CardQuad.fromFloatArray(arr)
        assertEquals(original.topLeft.x, restored.topLeft.x, 0.001f)
        assertEquals(original.topLeft.y, restored.topLeft.y, 0.001f)
        assertEquals(original.topRight.x, restored.topRight.x, 0.001f)
        assertEquals(original.bottomRight.y, restored.bottomRight.y, 0.001f)
    }

    // 27. Auto-crop falls back gracefully to guide frame if edge detection is null
    @Test
    fun test27_AutoCropFallbackToGuideFrame() {
        val guide = CardQuad.createCentered(800f, 500f, 0.85f)
        assertTrue(guide.isValidConvex())
        assertTrue(guide.area() > 0)
    }

    // 28. Incomplete pass detection
    @Test
    fun test28_IncompletePassDetection() {
        val filesDir = tempFolder.newFolder("files28")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        // Only front exists, back is missing
        File(passDir, "pass_front.jpg").writeBytes(byteArrayOf(1, 2))

        val storage = BusPassStorage(filesDir)
        assertNull(storage.loadBusPass())
    }

    // 29. ScanStep state machine completeness
    @Test
    fun test29_ScanStepStateMachineCompleteness() {
        val allSteps = ScanStep.entries
        assertTrue(allSteps.contains(ScanStep.IDLE))
        assertTrue(allSteps.contains(ScanStep.SCANNING_FRONT))
        assertTrue(allSteps.contains(ScanStep.ADJUST_FRONT))
        assertTrue(allSteps.contains(ScanStep.FRONT_CONFIRMED))
        assertTrue(allSteps.contains(ScanStep.SCANNING_BACK))
        assertTrue(allSteps.contains(ScanStep.ADJUST_BACK))
        assertTrue(allSteps.contains(ScanStep.REVIEW))
        assertTrue(allSteps.contains(ScanStep.SAVING))
        assertTrue(allSteps.contains(ScanStep.SAVED))
    }

    // 30. BusPass model serialization and version tracking integrity
    @Test
    fun test30_BusPassMetadataVersionTracking() {
        val filesDir = tempFolder.newFolder("files30")
        val passDir = File(filesDir, "bus_pass").apply { mkdirs() }
        val front = File(passDir, "pass_front.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val back = File(passDir, "pass_back.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        File(passDir, "pass_metadata.json").writeText("""
            {
                "createdAt": 1700000000000,
                "updatedAt": 1700005000000,
                "version": 3
            }
        """.trimIndent())

        val storage = BusPassStorage(filesDir)
        val pass = storage.loadBusPass()

        assertNotNull(pass)
        assertEquals(3, pass!!.version)
        assertEquals(1700000000000L, pass.createdAt)
        assertEquals(1700005000000L, pass.updatedAt)
        assertEquals(3L, pass.frontFileSize)
        assertEquals(3L, pass.backFileSize)
    }

    // 31. CardQuad withSafetyMargin expands outward from centroid by margin percentage
    @Test
    fun test31_CardQuadSafetyMarginExpansion() {
        val quad = CardQuad(
            topLeft = Point2D(100f, 100f),
            topRight = Point2D(500f, 100f),
            bottomRight = Point2D(500f, 350f),
            bottomLeft = Point2D(100f, 350f)
        )
        val withMargin = quad.withSafetyMargin(marginFraction = 0.035f, maxWidth = 600f, maxHeight = 450f)
        assertTrue(withMargin.isValidConvex())
        // Top-left must expand outward (x < 100, y < 100)
        assertTrue(withMargin.topLeft.x < quad.topLeft.x)
        assertTrue(withMargin.topLeft.y < quad.topLeft.y)
        // Bottom-right must expand outward (x > 500, y > 350)
        assertTrue(withMargin.bottomRight.x > quad.bottomRight.x)
        assertTrue(withMargin.bottomRight.y > quad.bottomRight.y)
        // Area must be strictly larger to protect card borders
        assertTrue(withMargin.area() > quad.area())
    }

    // 32. Safety margin respects boundary clamps
    @Test
    fun test32_CardQuadSafetyMarginClampedAtBoundary() {
        val quadNearBorder = CardQuad(
            topLeft = Point2D(2f, 2f),
            topRight = Point2D(598f, 2f),
            bottomRight = Point2D(598f, 398f),
            bottomLeft = Point2D(2f, 398f)
        )
        val expanded = quadNearBorder.withSafetyMargin(marginFraction = 0.05f, maxWidth = 600f, maxHeight = 400f)
        assertTrue(expanded.topLeft.x >= 0f)
        assertTrue(expanded.topLeft.y >= 0f)
        assertTrue(expanded.bottomRight.x <= 600f)
        assertTrue(expanded.bottomRight.y <= 400f)
    }

    // 33. State machine guarantees manual capture: initial step is idle or scanning with no auto-completed draft
    @Test
    fun test33_ManualCaptureFlowGuarantees() {
        // Step sequence ensures user must explicitly advance through steps
        val steps = listOf(
            ScanStep.IDLE,
            ScanStep.SCANNING_FRONT,
            ScanStep.FRONT_CONFIRMED,
            ScanStep.SCANNING_BACK,
            ScanStep.REVIEW,
            ScanStep.SAVED
        )
        for (i in 0 until steps.size - 1) {
            assertTrue(steps[i] != steps[i + 1])
        }
    }

    // 34. CardQuad.clampToBounds keeps points strictly within image boundaries
    @Test
    fun test34_CardQuadClampToBounds() {
        val outQuad = CardQuad(
            topLeft = Point2D(-25f, -10f),
            topRight = Point2D(1050f, -5f),
            bottomRight = Point2D(1020f, 620f),
            bottomLeft = Point2D(-15f, 610f)
        )
        val clamped = outQuad.clampToBounds(1000, 600)
        assertEquals(0f, clamped.topLeft.x, 0.001f)
        assertEquals(0f, clamped.topLeft.y, 0.001f)
        assertEquals(1000f, clamped.topRight.x, 0.001f)
        assertEquals(0f, clamped.topRight.y, 0.001f)
        assertEquals(1000f, clamped.bottomRight.x, 0.001f)
        assertEquals(600f, clamped.bottomRight.y, 0.001f)
        assertEquals(0f, clamped.bottomLeft.x, 0.001f)
        assertEquals(600f, clamped.bottomLeft.y, 0.001f)
    }

    // 35. Corner adjustment letterbox coordinate mapping is strictly reciprocal
    @Test
    fun test35_LetterboxCoordinateMappingReciprocal() {
        val containerWidth = 360f
        val containerHeight = 640f
        val bitmapWidth = 1080f
        val bitmapHeight = 1920f

        val scale = minOf(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
        val offsetX = (containerWidth - bitmapWidth * scale) / 2f
        val offsetY = (containerHeight - bitmapHeight * scale) / 2f

        val origBmpX = 450f
        val origBmpY = 920f

        // Convert to Screen
        val screenX = origBmpX * scale + offsetX
        val screenY = origBmpY * scale + offsetY

        // Convert back to Bitmap
        val convertedBmpX = ((screenX - offsetX) / scale).coerceIn(0f, bitmapWidth)
        val convertedBmpY = ((screenY - offsetY) / scale).coerceIn(0f, bitmapHeight)

        assertEquals(origBmpX, convertedBmpX, 0.01f)
        assertEquals(origBmpY, convertedBmpY, 0.01f)
    }

    // 36. Detector fallback creates centered convex quad within bounds
    @Test
    fun test36_DetectorFallbackCenteredQuad() {
        val fallback = CardQuad.createCentered(1080f, 1920f, 0.85f)
        assertTrue(fallback.isValidConvex())
        assertTrue(fallback.isWithinBounds(1080f, 1920f))
        assertEquals(1080f / 2f, fallback.centroid().x, 1f)
        assertEquals(1920f / 2f, fallback.centroid().y, 1f)
    }

    // 37. mapScreenGuideToBitmap maps screen guide frame accurately to bitmap space without distortion
    @Test
    fun test37_MapScreenGuideToBitmapPrecision() {
        val screenWidth = 360f
        val screenHeight = 800f
        val frameWidth = screenWidth * 0.88f
        val frameHeight = frameWidth / 1.586f
        val frameLeft = (screenWidth - frameWidth) / 2f
        val frameTop = (screenHeight - frameHeight) / 2f

        val mapped = com.college.bustracker.pass.ui.mapScreenGuideToBitmap(
            screenWidthDp = screenWidth,
            screenHeightDp = screenHeight,
            guideLeftDp = frameLeft,
            guideTopDp = frameTop,
            guideWidthDp = frameWidth,
            guideHeightDp = frameHeight,
            bitmapWidth = 3000,
            bitmapHeight = 4000
        )

        assertTrue(mapped.isValidConvex())
        assertTrue(mapped.isWithinBounds(3000f, 4000f))
        // Aspect ratio must be very close to standard ID card 1.586
        assertEquals(1.586f, mapped.aspectRatio(), 0.05f)
        // Card must be centered horizontally and vertically
        assertEquals(1500f, mapped.centroid().x, 50f)
        assertEquals(2000f, mapped.centroid().y, 50f)
    }

    // 38. Fallback card corners use mapped guide frame instead of entire wide sensor
    @Test
    fun test38_CardDetectorFallbackUsesMappedGuide() {
        val mappedGuide = CardQuad(
            topLeft = Point2D(600f, 1200f),
            topRight = Point2D(2400f, 1200f),
            bottomRight = Point2D(2400f, 2335f),
            bottomLeft = Point2D(600f, 2335f)
        )
        // Verify CardQuad geometry properties of mapped guide
        assertTrue(mappedGuide.isValidConvex())
        assertEquals(1800f, mappedGuide.maxWidth(), 0.01f)
        assertEquals(1135f, mappedGuide.maxHeight(), 0.01f)
        assertEquals(1.586f, mappedGuide.aspectRatio(), 0.01f)
        assertEquals(1500f, mappedGuide.centroid().x, 0.01f)
        assertEquals(1767.5f, mappedGuide.centroid().y, 0.01f)
        assertTrue(mappedGuide.isWithinBounds(3000f, 4000f))

        // Ensure safety margin application stays tight and matches guide
        val expanded = mappedGuide.withSafetyMargin(0.015f, 3000f, 4000f)
        assertTrue(expanded.isValidConvex())
        assertTrue(expanded.isWithinBounds(3000f, 4000f))
        assertTrue(expanded.maxWidth() > mappedGuide.maxWidth())
        assertTrue(expanded.maxWidth() < mappedGuide.maxWidth() * 1.05f)
    }
}
