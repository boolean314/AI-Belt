package com.example.ai_belt_mobile.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityScanBinding
import com.example.ai_belt_mobile.utils.OverlayView
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN_RESULT = "extra_scan_result"
    }
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvResult: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var isTorchOn = false
    private lateinit var binding: ActivityScanBinding

    // 防止同一个二维码被重复返回
    @Volatile
    private var hasReturnedResult = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlay)
        tvResult = findViewById(R.id.tvResult)
        cameraExecutor = Executors.newSingleThreadExecutor()

        permissionLauncher.launch(Manifest.permission.CAMERA)

        previewView.setOnClickListener {
            cameraProvider?.let {
                val camera = it.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA)
                val cameraControl = camera.cameraControl
                val cameraInfo = camera.cameraInfo

                cameraInfo.torchState.observe(this) { torchState ->
                    isTorchOn = torchState == androidx.camera.core.TorchState.ON
                }
                cameraControl.enableTorch(!isTorchOn)
            }
        }
    }

    private fun returnScanResult(rawValue: String) {
        if (hasReturnedResult) return
        hasReturnedResult = true

        val data = Intent().putExtra(EXTRA_SCAN_RESULT, rawValue)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreviewAndAnalyzer()
            } catch (e: Exception) {
                e.printStackTrace()
                setResult(RESULT_CANCELED)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreviewAndAnalyzer() {
        val cp = cameraProvider ?: return
        cp.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (hasReturnedResult) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val rawValue = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                    if (rawValue.isNotBlank()) {
                        runOnUiThread { tvResult.text = "Scanned: $rawValue" }
                        imageAnalysis.clearAnalyzer()
                        returnScanResult(rawValue)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cp.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // 保留你的图库解码方法
    private fun decodeFromGallery(uri: Uri) {
        try {
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val image = InputImage.fromBitmap(bmp, 0)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        tvResult.text = "Photo: ${barcodes[0].rawValue}"
                    }
                }
                .addOnFailureListener { e ->
                    tvResult.text = "Decode failed: ${e.message}"
                }
        } catch (e: Exception) {
            tvResult.text = "Decode failed: ${e.message}"
        }
    }
}