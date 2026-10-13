package com.college.bustracker.pass.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.bustracker.pass.engine.CardDetector
import com.college.bustracker.pass.engine.CardEnhancer
import com.college.bustracker.pass.engine.ImageHasher
import com.college.bustracker.pass.engine.PerspectiveCorrector
import com.college.bustracker.pass.engine.QualityChecker
import com.college.bustracker.pass.model.BusPass
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.QualityCheckIssue
import com.college.bustracker.pass.storage.BusPassStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanStep {
    IDLE,
    SCANNING_FRONT,
    ADJUST_FRONT,
    FRONT_CONFIRMED,
    SCANNING_BACK,
    ADJUST_BACK,
    REVIEW,
    SAVING,
    SAVED,
    ERROR
}

data class BusPassUiState(
    val savedPass: BusPass? = null,
    val hasDraftFront: Boolean = false,
    val currentStep: ScanStep = ScanStep.IDLE,
    val draftFrontBitmap: Bitmap? = null,
    val draftBackBitmap: Bitmap? = null,
    val rawFrontBitmap: Bitmap? = null,
    val rawBackBitmap: Bitmap? = null,
    val frontCorners: CardQuad? = null,
    val backCorners: CardQuad? = null,
    val pendingRawBitmap: Bitmap? = null,
    val pendingCorners: CardQuad? = null,
    val isReplacing: Boolean = false,
    val validationIssue: QualityCheckIssue? = null,
    val validationMessage: String? = null,
    val cameraError: String? = null,
    val cameraRetryCount: Int = 0,
    val isDuplicateWarning: Boolean = false,
    val generalError: String? = null,
    val isSaving: Boolean = false,
    val lastQualityResult: com.college.bustracker.pass.model.CardQualityResult? = null,
    val isDebugOverlayEnabled: Boolean = false
)

class BusPassViewModel(
    application: Application
) : AndroidViewModel(application) {

    private var storage: BusPassStorage = BusPassStorage(application)

    constructor() : this(com.college.bustracker.CollegeBusApp.instance)

    constructor(application: Application, customStorage: BusPassStorage) : this(application) {
        this.storage = customStorage
    }

    constructor(customStorage: BusPassStorage) : this(com.college.bustracker.CollegeBusApp.instance) {
        this.storage = customStorage
    }

    private val _uiState = MutableStateFlow(BusPassUiState())
    val uiState: StateFlow<BusPassUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val pass = storage.loadBusPass()
            val hasDraft = storage.hasDraftFront()
            _uiState.value = _uiState.value.copy(
                savedPass = pass,
                hasDraftFront = hasDraft
            )
        }
    }

    fun startScanning(isReplacement: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            currentStep = ScanStep.SCANNING_FRONT,
            isReplacing = isReplacement,
            draftFrontBitmap = null,
            draftBackBitmap = null,
            pendingRawBitmap = null,
            pendingCorners = null,
            validationIssue = null,
            validationMessage = null,
            cameraError = null,
            isDuplicateWarning = false,
            generalError = null
        )
    }

    fun onCameraError(error: String) {
        val currentRetries = _uiState.value.cameraRetryCount
        _uiState.value = _uiState.value.copy(
            cameraError = error,
            cameraRetryCount = currentRetries + 1
        )
    }

    fun clearCameraError() {
        _uiState.value = _uiState.value.copy(cameraError = null)
    }

    fun toggleDebugOverlay() {
        _uiState.value = _uiState.value.copy(
            isDebugOverlayEnabled = !_uiState.value.isDebugOverlayEnabled
        )
    }

    /**
     * Called when a camera or manual shutter capture completes with a raw bitmap.
     */
    fun processCapturedImage(rawBitmap: Bitmap, guideFrame: CardQuad?, rotationDegrees: Int = 0) {
        viewModelScope.launch(Dispatchers.Default) {
            val w = rawBitmap.width
            val h = rawBitmap.height

            val refFrame = guideFrame ?: CardQuad.createCentered(w.toFloat(), h.toFloat(), 0.75f)

            // 1. Multi-candidate card detection with outer boundary prioritization
            val detection = CardDetector.detectWithConfidence(rawBitmap, refFrame)
            val corners = if (detection.isConfident) {
                detection.quad
            } else {
                refFrame
            }

            // 2. Perform quality audit
            val quality = QualityChecker.evaluateQuality(
                bitmap = rawBitmap,
                corners = corners,
                guideFrame = refFrame,
                rotationDegrees = rotationDegrees,
                isCardDetected = detection.isConfident,
                confidencePercent = detection.confidencePercent
            )

            if (!quality.isAccepted) {
                // Keep raw bitmap and corners so student can easily adjust manually
                _uiState.value = _uiState.value.copy(
                    pendingRawBitmap = rawBitmap,
                    pendingCorners = corners,
                    validationIssue = quality.primaryIssue,
                    validationMessage = quality.primaryIssue?.userMessage ?: "Please adjust card within the frame",
                    lastQualityResult = quality
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(lastQualityResult = quality)

            // Quality is acceptable -> perform perspective crop & enhancement
            applyCropAndProceed(rawBitmap, corners)
        }
    }

    fun openManualCornerAdjustment() {
        val raw = _uiState.value.pendingRawBitmap ?: return
        val currentCorners = _uiState.value.pendingCorners ?: CardQuad.createCentered(raw.width.toFloat(), raw.height.toFloat(), 0.85f)
        val nextStep = if (_uiState.value.currentStep == ScanStep.SCANNING_FRONT) {
            ScanStep.ADJUST_FRONT
        } else {
            ScanStep.ADJUST_BACK
        }
        _uiState.value = _uiState.value.copy(
            currentStep = nextStep,
            pendingCorners = currentCorners,
            validationIssue = null,
            validationMessage = null
        )
    }

    fun confirmManualCorners(adjustedCorners: CardQuad) {
        val raw = _uiState.value.pendingRawBitmap ?: return
        if (!adjustedCorners.isValidConvex()) {
            _uiState.value = _uiState.value.copy(
                validationIssue = QualityCheckIssue.PERSPECTIVE_DISTORTION,
                validationMessage = "Invalid corner shape. Ensure corners do not cross."
            )
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            applyCropAndProceed(raw, adjustedCorners)
        }
    }

    private fun applyCropAndProceed(rawBitmap: Bitmap, corners: CardQuad) {
        val cropResult = PerspectiveCorrector.correctPerspective(rawBitmap, corners)
        if (cropResult.isFailure) {
            _uiState.value = _uiState.value.copy(
                validationIssue = QualityCheckIssue.PERSPECTIVE_DISTORTION,
                validationMessage = "Perspective correction failed. Please adjust corners manually."
            )
            return
        }

        val unwarped = cropResult.getOrThrow()
        val enhanced = CardEnhancer.enhance(unwarped)

        val isFront = (_uiState.value.currentStep == ScanStep.SCANNING_FRONT ||
                _uiState.value.currentStep == ScanStep.ADJUST_FRONT ||
                _uiState.value.draftFrontBitmap == null)

        if (isFront) {
            storage.saveDraftFront(enhanced)
            _uiState.value = _uiState.value.copy(
                draftFrontBitmap = enhanced,
                rawFrontBitmap = rawBitmap,
                frontCorners = corners,
                pendingRawBitmap = rawBitmap,
                pendingCorners = corners,
                validationIssue = null,
                validationMessage = null,
                currentStep = ScanStep.FRONT_CONFIRMED
            )
        } else {
            // Check for duplicate back side
            val front = _uiState.value.draftFrontBitmap
            val isDuplicate = front != null && ImageHasher.areSimilar(front, enhanced)

            _uiState.value = _uiState.value.copy(
                draftBackBitmap = enhanced,
                rawBackBitmap = rawBitmap,
                backCorners = corners,
                pendingRawBitmap = rawBitmap,
                pendingCorners = corners,
                validationIssue = null,
                validationMessage = null,
                isDuplicateWarning = isDuplicate,
                currentStep = ScanStep.REVIEW
            )
        }
    }

    fun openAdjustFrontCrop() {
        val raw = _uiState.value.rawFrontBitmap ?: _uiState.value.pendingRawBitmap ?: return
        val corners = _uiState.value.frontCorners ?: _uiState.value.pendingCorners ?: CardQuad.createCentered(raw.width.toFloat(), raw.height.toFloat(), 0.75f)
        _uiState.value = _uiState.value.copy(
            currentStep = ScanStep.ADJUST_FRONT,
            pendingRawBitmap = raw,
            pendingCorners = corners,
            validationIssue = null,
            validationMessage = null
        )
    }

    fun openAdjustBackCrop() {
        val raw = _uiState.value.rawBackBitmap ?: _uiState.value.pendingRawBitmap ?: return
        val corners = _uiState.value.backCorners ?: _uiState.value.pendingCorners ?: CardQuad.createCentered(raw.width.toFloat(), raw.height.toFloat(), 0.75f)
        _uiState.value = _uiState.value.copy(
            currentStep = ScanStep.ADJUST_BACK,
            pendingRawBitmap = raw,
            pendingCorners = corners,
            validationIssue = null,
            validationMessage = null
        )
    }

    fun cancelCornerAdjustmentToStep(targetStep: ScanStep) {
        _uiState.value = _uiState.value.copy(
            currentStep = targetStep,
            validationIssue = null,
            validationMessage = null
        )
    }

    fun proceedToBackScan() {
        _uiState.value = _uiState.value.copy(
            currentStep = ScanStep.SCANNING_BACK,
            pendingRawBitmap = null,
            pendingCorners = null,
            validationIssue = null,
            validationMessage = null,
            isDuplicateWarning = false
        )
    }

    fun retakeFront() {
        _uiState.value = _uiState.value.copy(
            draftFrontBitmap = null,
            pendingRawBitmap = null,
            pendingCorners = null,
            validationIssue = null,
            validationMessage = null,
            currentStep = ScanStep.SCANNING_FRONT
        )
    }

    fun retakeBack() {
        _uiState.value = _uiState.value.copy(
            draftBackBitmap = null,
            pendingRawBitmap = null,
            pendingCorners = null,
            validationIssue = null,
            validationMessage = null,
            isDuplicateWarning = false,
            currentStep = ScanStep.SCANNING_BACK
        )
    }

    fun cancelScanning() {
        // If replacing, keep existing pass untouched
        _uiState.value = _uiState.value.copy(
            currentStep = ScanStep.IDLE,
            draftFrontBitmap = null,
            draftBackBitmap = null,
            pendingRawBitmap = null,
            pendingCorners = null,
            validationIssue = null,
            validationMessage = null,
            isReplacing = false,
            isDuplicateWarning = false
        )
    }

    fun resumeDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = storage.loadDraftFront()
            if (draft != null) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        draftFrontBitmap = draft,
                        currentStep = ScanStep.FRONT_CONFIRMED,
                        hasDraftFront = false
                    )
                }
            } else {
                storage.clearDraft()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(hasDraftFront = false)
                }
            }
        }
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearDraft()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(hasDraftFront = false)
            }
        }
    }

    fun onGalleryImagePicked(context: Context, uri: Uri, guideFrame: CardQuad? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open gallery image stream.")

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                var sampleSize = 1
                while (options.outWidth / sampleSize > 1920 || options.outHeight / sampleSize > 1920) {
                    sampleSize *= 2
                }

                val decodeStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not re-open gallery stream.")
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                decodeStream.close()

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            generalError = "Could not decode gallery image. Format may be unsupported."
                        )
                    }
                    return@launch
                }

                withContext(Dispatchers.Default) {
                    processCapturedImage(bitmap, guideFrame)
                }
            } catch (e: Exception) {
                Log.e("BusPassVM", "Gallery image error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        generalError = "Failed to load selected gallery image: ${e.message}"
                    )
                }
            }
        }
    }

    fun savePass() {
        val front = _uiState.value.draftFrontBitmap ?: return
        val back = _uiState.value.draftBackBitmap ?: return

        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch(Dispatchers.IO) {
            val result = storage.saveBusPass(front, back)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val saved = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        savedPass = saved,
                        currentStep = ScanStep.SAVED,
                        draftFrontBitmap = null,
                        draftBackBitmap = null,
                        isReplacing = false,
                        isSaving = false,
                        hasDraftFront = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        generalError = "Failed to save bus pass locally: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun deletePass() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = storage.deleteBusPass()
            withContext(Dispatchers.Main) {
                if (res.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        savedPass = null,
                        currentStep = ScanStep.IDLE,
                        hasDraftFront = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        generalError = "Could not delete bus pass: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun attachFeeReceipt(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Decode with inSampleSize to safeguard memory
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                val maxDimension = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            generalError = "Could not decode fee receipt image. Format may be unsupported."
                        )
                    }
                    return@launch
                }

                val saveResult = storage.saveFeeReceipt(bitmap)
                withContext(Dispatchers.Main) {
                    if (saveResult.isSuccess) {
                        refresh()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            generalError = "Failed to save fee receipt: ${saveResult.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("BusPassVM", "Fee receipt load error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        generalError = "Failed to load fee receipt: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteFeeReceipt() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = storage.deleteFeeReceipt()
            withContext(Dispatchers.Main) {
                if (res.isSuccess) {
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        generalError = "Could not delete fee receipt: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearGeneralError() {
        _uiState.value = _uiState.value.copy(generalError = null)
    }

    fun dismissSavedDialog() {
        _uiState.value = _uiState.value.copy(currentStep = ScanStep.IDLE)
    }
}
