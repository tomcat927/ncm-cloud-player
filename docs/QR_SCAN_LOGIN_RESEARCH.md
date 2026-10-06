# 扫码登录其他设备：协议研究与风控结论

> 2026-10-07，五轮真机迭代 + 服务端探针实测的完整记录。
> 结论先行：**协议层已全部打通且实现正确；最终被网易账号级风控（手机号复用验证）挡住，该验证的信任根是官方客户端，第三方 App 无法通过**。本文档沉淀协议细节、踩坑与方法论，供后续继续或避免重复踩坑。

## 功能定义

设置页「扫码登录其他设备」：本 App（已登录）作为**扫码方**，确认外部（网页版/脚本生成的）网易云登录二维码，让对端完成登录。

完整状态机（被扫方视角，轮询 `login/qrcode/client/login`）：

| 码 | 含义 |
|---|---|
| 800 | 二维码不存在或已过期 |
| 801 | 等待扫码 |
| 802 | 已扫码，等待确认 |
| 803 | 授权登录成功（响应 Set-Cookie 下发 MUSIC_U） |

## 协议结论（全部实测验证）

### 1. 端点

- ❌ `/eapi/login/qrcode/confirm` —— **已被网易下线**（HTTP 200 + body `{"code":404,"message":"接口未找到！"}`）。社区资料（Binaryify 等）里的这个路径全部过时。
- ✅ 现行端点：`/eapi/login/qrcode/server/login`（eapi）/ `/weapi/login/qrcode/server/login`（weapi，路由都在）。与被扫方的 `client/login` 对应，扫码方是 "server"。
- 端点出处：**官方扫码确认页 `https://music.163.com/st/platform/scanlogin` 的前端 JS bundle**。这是最权威的资料来源（详见方法论）。

### 2. 时序（重要，踩过坑）

必须严格按官方确认页的顺序：

1. `type=1` 上报"已扫描"（把二维码从 801 推到 802）
2. `type=2` 确认授权（802 → 803，对端轮询即拿到 cookie）
3. `type=3` 拒绝

**跳过 type=1 直接 type=2 会返回 `{"code":400,"message":"授权失败"}`**——因为二维码还停在 801 态，无授权可确认。官方页面对 type=1 的失败不阻断，仍链式发起 type=2。

### 3. 请求参数（对齐官方确认页）

```json
{
  "key": "<unikey>",
  "type": 2,
  "userid": 8815005788,          // 扫码方账号 uid，必须；缺失时先调 nuser/account/get 现补
  "clientTraceId": "",           // 二维码 URL 里的 login_traceId 参数；没有就传空串（官方页同款行为，不要造随机 UUID）
  "isEd": true,                  // 官方页请求包装层固定附加
  "brand": "OnePlus",            // type=2 时上报，Build.BRAND
  "device": "PJX110",            // type=2 时上报，Build.MODEL
  "envType": ""                  // 官方页传空串
}
```

- eapi 摘要用 `/api/login/qrcode/server/login`（剥掉 /eapi 前缀），走 `interface.music.163.com`
- 请求需带扫码方登录 Cookie（MUSIC_U），HeaderInterceptor 现有逻辑已覆盖
- 响应里 `redirectUrl` 字段用于风控跳转（见下）

### 4. 风控（当前不可逾越的边界）

type=1 可能直接返回：

```json
{
  "hitType": 800006,
  "code": 10004,
  "message": "当前登录存在安全风险，请稍后再试",
  "redirectUrl": "https://st.music.163.com/st/user-new/phoneReuse/index.html?scene=-1&userId=<uid>&failCode=10003&systemOs=android&ts=...&NMSCVT=..."
}
```

- `failCode=10003` = **手机号复用验证**，网易对"陌生设备确认扫码登录"的账号保护，连续多轮触发，无规律可绕
- type=2 此时回 `{"code":301}`，只是二维码未进入 802 态的连锁反应，不是独立问题
- 验证页上下文实测：
  - **系统浏览器裸开** → 页面判「风险设备」（缺登录态 + 缺风控 Cookie）
  - **App 内置 WebView 注入全部 Cookie**（登录态 + server/login 响应下发的 20 分钟临时风控 Cookie，来自 OkHttp CookieJar）→ 仍被拒，页面 console 报 `appType undefined`
- **根因**：验证页的信任根是官方客户端注入的 JS bridge / 安全 SDK 设备证明。协议可以复刻，设备证明无法伪造（也不应该伪造）。官方页面对 10004 的处理是 `window.location.replace(redirectUrl)`，第三方 App 没有对应的可信环境可跳。

### 5. 现状

- 功能代码保留（协议正确，对未触发风控的账号/设备可能直接可用）。触发风控时 UI 引导进内置验证页 `RiskVerifyScreen`。
- 依赖 cookie 的场景（如 netease-level-up 脚本 `--qr-login`）走兜底：**官方网易云 App 扫码确认**（官方 App 能过风控），或 App「导出登录凭证」手动填 cookie 文件。

## 方法论（可复用）

1. **探针法先验路由，再猜参数**：python + pycryptodome 复刻 eapi/weapi 加密直接打服务端。未知路由回「接口未找到」，路由存在但缺参/缺鉴权回「参数错误」——用这两个响应区分路由存活性，比盲猜路径快得多。
2. **官方页面前端 JS 是最权威的协议资料**。`music.163.com/st/platform/scanlogin` 的 bundle 里有 server/login 调用点、错误码表（`"/api/login/qrcode/server/login":[800,400]`）、10004→redirectUrl 跳转逻辑、完整的参数字段名。SPA 会拆 chunk，注意把 webpack 分包哈希表抠出来全部拉下来搜。
3. **eapi/weapi 加密细节坑**：eapi 摘要用剥掉 `/eapi` 前缀的 `/api/...` 路径；weapi 第二层 AES 加密的是第一层结果的 **base64 字符串**而非原始密文（复刻时踩过，症状是响应空 body）。
4. **HTTP 200 ≠ 成功**：eapi 业务码在 body 里，404「接口未找到」也是 HTTP 200。
5. 社区资料（Binaryify/NeteaseCloudMusicApi、pyncm、chaunsin Go 库）只覆盖**被扫方**（unikey + client/login 轮询），扫码方 confirm 无任何公开实现——不要指望现成答案。

## 后续方向（未验证）

- 对比官方 App 抓包：server/login 请求头是否带安全 SDK 签名（若带，即彻底封死）
- `x-login-chain-id` 头 + 二维码 URL `chainId` 参数（官方网页版登录链路参数，社区资料提及）对风控的影响
- 换设备/新账号观察风控触发条件（当前账号 100% 触发，可能与账号安全状态有关）

## 相关代码

| 文件 | 职责 |
|---|---|
| `core/api/NeteaseApiService.kt` | server/login 端点与请求/响应模型 |
| `data/AuthRepository.kt` | confirmQrLogin 时序（type=1→type=2）、风控异常 `QrRiskChallengeException`、uid 兜底 |
| `ui/settings/SettingsScreen.kt` | 扫码入口、二维码内容解析（codekey/login_traceId）、风控分支 |
| `ui/settings/RiskVerifyScreen.kt` | 内置验证 WebView（Cookie 注入） |
| `ui/scan/PortraitCaptureActivity.kt` | 竖屏扫码页（zxing 默认锁横屏） |

配套脚本仓：`netease-level-up`（`--qr-login` 生成二维码 + 轮询 client/login，与本功能配对；注意其二维码内容是 `https://music.163.com/login?codekey=<unikey>` URL 形式，裸 unikey 官方 App 不识别）。
