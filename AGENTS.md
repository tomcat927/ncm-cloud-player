# AGENTS.md

给 AI 编码助手（ZCode / Claude Code 等）在本仓库工作的约定。改动代码前请先读完本文件。

## 构建与验证（最重要的规约）

- **禁止在本地安装 JDK / Android SDK 等构建环境，禁止本地运行 gradlew 编译。**
- 编译验证一律走 GitHub Actions（`.github/workflows/android.yml`）：提交 → 推送到 `main` → CI 自动 `assembleDebug` + `assembleRelease` 并发布 Release。
- push 到 `main` 会直接创建新的版本 tag 并发布 GitHub Release，**推送前请静态自查改动**（仓库里没有可用的本地编译环境，CI 就是编译器）。
- 用 `gh run list` / `gh run watch` 跟踪构建结果；失败则修复后重新提交推送。
- 签名材料一律在仓库外：本地读 `signing.properties`，CI 读同名环境变量（见 `build.gradle.kts`）。

## 技术栈与提交

- Kotlin + Jetpack Compose（Material3），DI 用 Koin，播放用 Media3，持久化用 DataStore Preferences。
- 提交信息格式：`<type>: <中文描述>`，如 `feat: 新增xxx`、`fix: 修复xxx`、`ui: 调整xxx`。

## 参考资料

- **参考项目 [Melodia](https://github.com/rinchao0721/Melodia)**：UI 观感与功能设计均参考它，已克隆在本机 `C:/data/vscode/android/Melodia`，直接查阅，不要重复克隆。
- 远程日志机制见 `docs/REMOTE_LOG.md`。
- 扫码登录其他设备的协议研究与风控结论（含 eapi/weapi 探针方法论）见 `docs/QR_SCAN_LOGIN_RESEARCH.md`。
