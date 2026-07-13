# OmniCamera — Architecture Document

## Overview
Transform NIKO-Portfolio into a high-design camera app with rich shooting algorithms and templates.

## Package Structure
```
com.example.myapplication/
├── MainActivity.kt              # Rewritten — camera entry point
├── camera/
│   ├── CameraTypes.kt           # Shared data types & enums
│   ├── CameraController.kt      # CameraX lifecycle + capture
│   ├── CameraViewModel.kt       # UI state + actions
│   └── CameraPermissionManager.kt
├── processing/
│   ├── FilterEngine.kt          # Real-time filter pipeline
│   ├── ImageProcessor.kt        # Post-capture processing
│   └── algorithms/              # Per-algorithm implementations
│       ├── ColorGrading.kt
│       ├── PortraitProcessor.kt
│       ├── HDRProcessor.kt
│       ├── NightModeProcessor.kt
│       ├── BeautyProcessor.kt
│       └── BokehProcessor.kt
├── templates/
│   ├── CameraTemplate.kt        # Template data model
│   ├── TemplateRepository.kt    # Template storage + lookup
│   ├── TemplateApplier.kt       # Apply template to camera + processing
│   └── presets/                 # All preset definitions
│       ├── PortraitPresets.kt
│       ├── LandscapePresets.kt
│       ├── NightPresets.kt
│       ├── FoodPresets.kt
│       ├── FilmPresets.kt
│       ├── VintagePresets.kt
│       ├── MinimalPresets.kt
│       └── MacroPresets.kt
├── ui/
│   ├── screens/
│   │   ├── CameraScreen.kt      # Main camera view
│   │   ├── GalleryScreen.kt     # Photo grid gallery
│   │   ├── PhotoReviewScreen.kt # Single photo view + edit
│   │   └── SettingsScreen.kt    # Camera settings
│   └── components/
│       ├── CameraViewfinder.kt  # Camera preview surface
│       ├── ShutterButton.kt     # Capture button
│       ├── ModeSelector.kt      # Template/mode selector
│       ├── ZoomSlider.kt        # Zoom control
│       ├── FocusIndicator.kt    # Focus point indicator
│       ├── ExposureSlider.kt    # EV compensation
│       ├── FilterWheel.kt       # Circular filter picker
│       ├── TemplateCard.kt      # Template preview card
│       ├── FilmStrip.kt         # Recent captures strip
│       └── CameraToolbar.kt     # Bottom toolbar
│   └── theme/
│       ├── Color.kt             # Camera-centric color palette
│       ├── Theme.kt             # Dark-first camera theme
│       └── Type.kt              # Clean, modern typography
├── data/
│   ├── GalleryRepository.kt     # Photo CRUD
│   └── PhotoEntity.kt           # Room entity
└── utils/
    └── ImageSaver.kt            # Save to MediaStore
```

## Shared API Contracts

### CameraTypes.kt
```kotlin
enum class LensType { WIDE, ULTRA_WIDE, TELEPHOTO, MACRO }
enum class FocusMode { AUTO, MANUAL, LOCKED }
enum class FlashMode { AUTO, ON, OFF, TORCH }
enum class CaptureMode { PHOTO, PORTRAIT, NIGHT, PRO }
data class CameraState(
    val isReady: Boolean = false,
    val lens: LensType = LensType.WIDE,
    val zoom: Float = 1f,
    val focusMode: FocusMode = FocusMode.AUTO,
    val focusPoint: Offset? = null,
    val exposureBias: Float = 0f,
    val flashMode: FlashMode = FlashMode.AUTO,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val activeTemplate: CameraTemplate? = null,
    val isCapturing: Boolean = false,
    val thumbnailUri: Uri? = null,
    val thumbnails: List<Uri> = emptyList(),
    val error: String? = null,
)
data class CaptureSettings(
    val iso: Int = 0, val shutterSpeed: String = "", val aperture: Float = 0f,
    val focalLength: Float = 0f, val whiteBalance: String = "auto",
)
```

### CameraViewModel (interface exposed to UI)
```kotlin
// State
val cameraState: StateFlow<CameraState>
// Actions
fun startCamera(context: Context)
fun stopCamera()
fun capturePhoto()
fun switchLens()
fun setZoom(level: Float)
fun setFocusMode(mode: FocusMode)
fun focusOnPoint(x: Float, y: Float)
fun adjustExposure(bias: Float)
fun toggleFlash()
fun applyTemplate(template: CameraTemplate)
fun switchCaptureMode(mode: CaptureMode)
fun openGallery()
```

### CameraTemplate.kt
```kotlin
data class CameraTemplate(
    val id: String, val name: String, val description: String,
    val category: TemplateCategory, val icon: String,
    val previewColor: Long,
    val cameraSettings: TemplateCameraSettings,
    val processing: TemplateProcessing,
)
data class TemplateCameraSettings(
    val preferredLens: LensType?, val isoRange: ClosedFloatingPointRange<Int>?,
    val exposureBias: Float?, val whiteBalance: String?,
    val focusMode: FocusMode?, val flashMode: FlashMode?,
)
data class TemplateProcessing(
    val colorGrading: ColorGrade?, val parameters: Map<String, Float> = emptyMap(),
)
enum class TemplateCategory { PORTRAIT, LANDSCAPE, NIGHT, FOOD, FILM, VINTAGE, MINIMAL, MACRO }
data class ColorGrade(
    val temperature: Float = 0f, val tint: Float = 0f, val contrast: Float = 0f,
    val saturation: Float = 1f, val highlights: Float = 0f, val shadows: Float = 0f,
    val vignette: Float = 0f, val grain: Float = 0f, val structure: Float = 0f,
)
```

## Design Language
- **Dark-first**: Deep blacks, subtle glows, high contrast
- **Minimal chrome**: No unnecessary UI chrome — immersive viewfinder
- **Gesture-driven**: Swipe to switch lens, tap to focus, pinch to zoom
- **Accent colors** from the template system (warm portraits, cool landscapes)
- **Subtle grain overlay** (texture, not noise)
- **Smooth animations** with spring physics

## Dependencies (new)
- CameraX: camera-camera2, camera-lifecycle, camera-view
- Coil: image loading for gallery
- ExifInterface: metadata handling
