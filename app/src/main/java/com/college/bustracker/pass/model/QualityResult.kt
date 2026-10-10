package com.college.bustracker.pass.model

/**
 * Diagnostic reasons for card scan validation.
 */
enum class QualityCheckIssue(val userMessage: String) {
    CARD_NOT_DETECTED("Card not detected — Move the card inside the frame"),
    CORNER_MISSING("Corner missing — Make sure all four corners are visible"),
    TOO_DARK("Too dark — Move to a brighter area"),
    TOO_BRIGHT("Too bright — Avoid excessive bright light"),
    TOO_MUCH_GLARE("Too much glare — Avoid direct light reflection"),
    IMAGE_BLURRY("Image blurry — Hold the phone steady"),
    CARD_COVERED("Card partially covered — Keep your fingers away from the card"),
    CARD_TOO_FAR("Card too far away — Move closer"),
    CARD_TOO_CLOSE("Card too close — Move the phone slightly farther away"),
    PERSPECTIVE_DISTORTION("Tilted angle — Hold phone parallel to the card"),
    RESOLUTION_LOW("Resolution too low — Hold camera closer to card")
}

data class CardQualityResult(
    val isAccepted: Boolean,
    val primaryIssue: QualityCheckIssue? = null,
    val customMessage: String? = primaryIssue?.userMessage,
    val detectedCorners: CardQuad? = null,
    val meanBrightness: Float = 0f,
    val sharpnessScore: Float = 0f,
    val glarePixelFraction: Float = 0f,
    val isConvex: Boolean = true,
    val coverageFraction: Float = 0f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val confidencePercent: Int = 0,
    val isCardDetected: Boolean = false
)
