package com.ncmcloud.player.core.network

import java.io.IOException

// 自定义 API 异常
class ApiException(message: String) : IOException(message)

