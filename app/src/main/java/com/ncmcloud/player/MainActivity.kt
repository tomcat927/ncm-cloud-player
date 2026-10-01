package com.ncmcloud.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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

    // 临时埋点：记录系统返回键到达 App 的时刻，与 Compose 处理时刻对比可区分
    // "按键派发延迟"和"App 内处理延迟"
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            AppLogger.i(TAG, "返回键到达 Activity")
        }
        return super.dispatchKeyEvent(event)
    }

    // 临时埋点：窗口失焦会导致按键/点击表现为"没反应"
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        AppLogger.i(TAG, "窗口焦点: hasFocus=$hasFocus")
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
