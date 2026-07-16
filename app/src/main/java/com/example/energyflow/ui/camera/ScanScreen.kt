package com.example.energyflow.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.energyflow.data.ImagePreprocessor
import com.example.energyflow.data.OcrSmartProcessor
import com.example.energyflow.data.ParseResult
import com.example.energyflow.data.SmartInputParser
import com.example.energyflow.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ScanScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ML Kit Client: 使用 DisposableEffect 确保退出时释放资源
    val textRecognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    DisposableEffect(Unit) {
        onDispose { textRecognizer.close() }
    }

    var recognizedText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        if (!hasCameraPermission) {
            PermissionDeniedView { permissionLauncher.launch(Manifest.permission.CAMERA) }
        } else if (recognizedText != null) {
            ScanResultView(
                recognizedText = recognizedText!!,
                onConfirm = { edited -> onResult(edited) },
                onRetry = { recognizedText = null },
                onDismiss = onDismiss
            )
        } else {
            CameraPreviewSection(
                textRecognizer = textRecognizer,
                onSuccess = { recognizedText = it },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun CameraPreviewSection(
    textRecognizer: TextRecognizer,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    
    // 保存 CameraControl 引用，用于丝滑切换手电筒
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 底层相机预览
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        cameraControl = camera.cameraControl // 获取控制权
                    } catch (e: Exception) {
                        Log.e("ScanScreen", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            onRelease = {
                // 组件销毁时彻底解绑，避免 CameraX 后台持有资源报错
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 中层镂空遮罩 (取景框)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.99f } // 开启离屏缓冲以支持 BlendMode.Clear
        ) {
            val scanBoxWidth = size.width * 0.75f
            val scanBoxHeight = 120.dp.toPx()
            val left = (size.width - scanBoxWidth) / 2f
            val top = size.height * 0.25f

            // 画一层半透明黑底
            drawRect(color = Color.Black.copy(alpha = 0.6f))
            // 抠出中间的高亮框
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(scanBoxWidth, scanBoxHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode = BlendMode.Clear 
            )
            // 画高亮框的边框
            drawRoundRect(
                color = ElectricColor.copy(alpha = 0.8f),
                topLeft = Offset(left, top),
                size = Size(scanBoxWidth, scanBoxHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. 顶部栏 (返回与手电筒)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp), // 适配状态栏
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text("对准表盘数字", color = Color.White, fontFamily = MonoFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                isTorchOn = !isTorchOn
                cameraControl?.enableTorch(isTorchOn) // 极其轻量级的调用，不卡顿
            }) {
                Text(if (isTorchOn) "🔦" else "💡", fontSize = 22.sp)
            }
        }

        // 4. 底部操作区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = errorMessage ?: "",
                    color = ErrorNeon,
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorNeon.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 拍照按钮
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(4.dp, if (isProcessing) ElectricColor else Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = ElectricColor, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                } else {
                    IconButton(
                        onClick = {
                            isProcessing = true
                            errorMessage = null
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val mediaImage = image.image
                                        if (mediaImage == null) {
                                            errorMessage = "获取图像失败"
                                            isProcessing = false
                                            image.close()
                                            return
                                        }
                                        val inputImage = ImagePreprocessor.preprocess(
                                            imageProxy = image,
                                            roiRegion = null
                                        )

                                        textRecognizer.process(inputImage)
                                            .addOnSuccessListener { visionText ->
                                                coroutineScope.launch(Dispatchers.Default) {
                                                    val rawText = visionText.text.trim()
                                                    if (rawText.isBlank()) {
                                                        withContext(Dispatchers.Main) {
                                                            errorMessage = "未识别到文字，请拉近距离或开启手电筒"
                                                            isProcessing = false
                                                        }
                                                    } else {
                                                        val smartText = OcrSmartProcessor.process(rawText)
                                                        val confidence = OcrSmartProcessor.calculateConfidence(rawText, smartText)
                                                        withContext(Dispatchers.Main) {
                                                            if (confidence < 0.3) {
                                                                errorMessage = "识别置信度较低（${"%.0f".format(confidence * 100)}%），请重新拍摄"
                                                                isProcessing = false
                                                            } else {
                                                                onSuccess(smartText)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                errorMessage = "识别失败: ${e.localizedMessage}"
                                                isProcessing = false
                                            }
                                            .addOnCompleteListener { image.close() }
                                    }
                                    override fun onError(exc: ImageCaptureException) {
                                        errorMessage = "拍照失败"
                                        isProcessing = false
                                    }
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "拍照", tint = DarkBackground, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanResultView(
    recognizedText: String,
    onConfirm: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val parser = remember { SmartInputParser() }
    var editableText by remember(recognizedText) { mutableStateOf(recognizedText) }
    val parseResults = remember(editableText) { parser.parseWithContext(editableText) }
    val successes = parseResults.filterIsInstance<ParseResult.Success>()
    val errors = parseResults.filterIsInstance<ParseResult.Error>()

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("识别结果", color = TextPrimary, fontFamily = MonoFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = editableText, onValueChange = { editableText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("OCR 识别结果（可编辑）", fontFamily = MonoFontFamily, fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricColor, unfocusedBorderColor = DarkSurface,
                cursorColor = ElectricColor, focusedTextColor = ElectricColor, unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            minLines = 2, maxLines = 5
        )
        Spacer(Modifier.height(16.dp))
        if (successes.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(SuccessGreen.copy(alpha = 0.06f)).border(1.dp, SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(16.dp)
            ) {
                Column {
                    Text("✅ 解析成功", color = SuccessGreen, fontFamily = MonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    successes.forEach { result ->
                        val label = when {
                            result.isElectric -> "⚡ 电表: ${result.electricTotal}"
                            result.isWater -> "💧 水表: ${result.waterTotal}"
                            else -> "📝 ${result.note ?: "记录"}"
                        }
                        Text(label, color = TextPrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("⚠️ ${errors.first().message}", color = ErrorNeon, fontFamily = MonoFontFamily, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重拍", fontFamily = MonoFontFamily, color = TextSecondary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onConfirm(editableText) }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (successes.isNotEmpty()) ElectricColor else DarkSurface,
                    contentColor = if (successes.isNotEmpty()) DarkBackground else TextSecondary
                ),
                shape = RoundedCornerShape(12.dp), enabled = successes.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("使用", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// PermissionDeniedView
@Composable
private fun PermissionDeniedView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📷", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("需要相机权限", color = TextPrimary, fontFamily = MonoFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("请在系统设置中授予相机权限以使用扫表功能", color = TextTertiary, fontFamily = MonoFontFamily, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("授予权限", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, color = DarkBackground)
        }
    }
}