package com.ncmcloud.player.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val qrState by viewModel.qrState.collectAsState()
    val smsState by viewModel.smsState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(2) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) viewModel.resetQrState()
        if (selectedTab != 2) viewModel.resetSmsState()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("登录网易云音乐") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Cookie") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("二维码") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("短信") })
            }
            when (selectedTab) {
                0 -> CookieLoginPane(viewModel, state)
                1 -> QrLoginPane(qrState) { viewModel.startQrLogin() }
                2 -> SmsLoginPane(viewModel, smsState)
            }
        }
    }
}

@Composable
private fun CookieLoginPane(viewModel: LoginViewModel, state: LoginState) {
    var cookie by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Spacer(Modifier.height(8.dp))
    Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(56.dp))
    Text("在浏览器登录 music.163.com，复制 Cookie 中的 MUSIC_U 值并粘贴。")
    OutlinedTextField(
        value = cookie,
        onValueChange = { cookie = it },
        label = { Text("MUSIC_U Cookie") },
        singleLine = false,
        maxLines = 4,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Button(
        onClick = {
            keyboard?.hide()
            viewModel.login(cookie)
        },
        enabled = state !is LoginState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state is LoginState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("登录")
        }
    }
    (state as? LoginState.Error)?.let {
        Text(it.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun QrLoginPane(qrState: QrLoginState, onRefresh: () -> Unit) {
    LaunchedEffect(qrState) {
        if (qrState is QrLoginState.Idle) onRefresh()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        when (qrState) {
            is QrLoginState.Idle, is QrLoginState.Loading -> {
                CircularProgressIndicator()
                Text("正在生成二维码…")
            }
            is QrLoginState.WaitingScan -> {
                QrCodeImage(qrState.qrMatrix)
                Text("使用网易云音乐 App 扫码登录")
            }
            is QrLoginState.WaitingConfirm -> {
                QrCodeImage(qrState.qrMatrix)
                Text("已扫码，请在手机上确认")
            }
            is QrLoginState.Expired -> {
                Text("二维码已过期", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRefresh) { Text("刷新二维码") }
            }
            is QrLoginState.Error -> {
                Text(qrState.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRefresh) { Text("重试") }
            }
        }
    }
}

@Composable
private fun SmsLoginPane(viewModel: LoginViewModel, smsState: SmsLoginState) {
    var phone by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(smsState) {
        if (smsState is SmsLoginState.CodeSent) {
            countdown = 60
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
        label = { Text("手机号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = captcha,
            onValueChange = { captcha = it.filter(Char::isDigit).take(6) },
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { viewModel.sendCaptcha(phone) },
            enabled = countdown == 0 && smsState !is SmsLoginState.Sending,
        ) {
            if (smsState is SmsLoginState.Sending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (countdown > 0) {
                Text("${countdown}s")
            } else {
                Text("发送")
            }
        }
    }
    Button(
        onClick = {
            keyboard?.hide()
            viewModel.loginWithCaptcha(phone, captcha)
        },
        enabled = smsState !is SmsLoginState.LoggingIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (smsState is SmsLoginState.LoggingIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("登录")
        }
    }
    (smsState as? SmsLoginState.Error)?.let {
        Text(it.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun QrCodeImage(matrix: BitMatrix) {
    Canvas(modifier = Modifier.size(240.dp)) {
        val cell = size.minDimension / matrix.width
        val black = Color.Black
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
