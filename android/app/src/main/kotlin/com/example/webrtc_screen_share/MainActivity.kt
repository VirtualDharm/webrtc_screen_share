// android/app/src/main/kotlin/com/example/webrtc_screen_share/MainActivity.kt
package com.example.webrtc_screen_share

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log

class MainActivity: FlutterActivity() {
    private val CHANNEL = "media_projection"
    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    companion object {
        var projectionResultCode: Int? = null
        var projectionResultData: Intent? = null
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestProjection" -> {
                    requestProjection()
                    result.success(true)
                }
                "startForegroundService" -> {
                    val intent = Intent(this, MediaProjectionService::class.java)
                    // Pass MediaProjection data to the service
                    projectionResultCode?.let { intent.putExtra("resultCode", it) }
                    projectionResultData?.let { intent.putExtra("data", it) }
                    // startForegroundService required on API 26+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this.startForegroundService(intent)
                    } else {
                        this.startService(intent)
                    }
                    result.success(true)
                }
                "stopForegroundService" -> {
                    val intent = Intent(this, MediaProjectionService::class.java)
                    stopService(intent)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun requestProjection() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                projectionResultCode = resultCode
                projectionResultData = data
                Log.d("MainActivity", "MediaProjection permission granted")
            } else {
                Log.d("MainActivity", "MediaProjection permission denied")
            }
        }
    }
}