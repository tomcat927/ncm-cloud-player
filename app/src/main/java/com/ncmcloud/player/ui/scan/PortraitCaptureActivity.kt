package com.ncmcloud.player.ui.scan

import com.journeyapps.barcodescanner.CaptureActivity

// 竖屏扫码页：zxing-android-embedded 默认把扫码页锁成横屏（orientationLocked 默认 true）。
// 库官方的竖屏方案就是空子类 + manifest 锁 portrait + ScanOptions.setOrientationLocked(false)
class PortraitCaptureActivity : CaptureActivity()
