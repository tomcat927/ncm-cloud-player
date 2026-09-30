package com.ncmcloud.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.ui.theme.NcmTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NcmTheme {
                RequestNotificationPermission()
                AppRoot()
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            AppLogger.i(TAG, "通知权限申请结果: granted=$granted")
        }

        val currentContext = LocalContext.current
        LaunchedEffect(Unit) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(
                currentContext,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                AppLogger.i(TAG, "准备申请通知权限")
                launcher.launch(permission)
            }
        }
    }
}
