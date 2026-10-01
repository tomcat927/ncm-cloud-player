# ncm-cloud-player

一个专注于网易云音乐个人云盘的轻量 Android 播放器：登录、列出云盘歌曲、播放。

本仓库不依赖任何自建 NodeJS API 服务，也不打包第三方公共 API。App 内部直接构造网易 `weapi/eapi/xeapi` 请求并播放返回的音频地址。网络加密层参考自 [Melodia](https://github.com/rinchao0721/Melodia)（MIT）。

## 功能范围

- 登录：短信验证码（手机自用首选）、二维码扫码、粘贴 `MUSIC_U` Cookie
- 云盘列表：分页获取 `/weapi/v1/cloud/get`
- 播放：通过 `/eapi/song/enhance/player/url/v1` 获取临时播放地址，使用 Media3 播放
- 播放模式：顺序播放 / 列表循环 / 单曲循环 / 随机播放，模式持久化；循环与随机下切歌自动绕回
- 歌词：`/eapi/song/lyric/v1` 拉取逐行（LRC）与逐字（YRC）歌词；播放详情页点击封面/歌词页互相切换，当前行自动居中、点击行跳转进度、YRC 歌曲逐字卡拉OK填色；支持翻译行开关、字号调节与按曲目磁盘缓存（均在设置页配置）
- 歌曲信息：播放页"更多"弹层展示曲目元信息（格式/时长/大小/码率/加入云盘时间/匹配状态）与服务端实际下发的码率，并可在弹层内切换播放音质
- 封面横滑切歌：播放详情页封面上左右滑动切换上一首/下一首
- 播放队列：云盘列表点歌即播放整列，支持上一首/下一首与队列内跳转
- 后台播放与系统通知栏控制
- 远程诊断日志：App 内上传至 OpenList/Alist，`tools/fetch_remote_log.py` 拉取分析
- GitHub Actions 构建 Debug / Release APK，无需本地安装 Android Studio

## 如何获取 MUSIC_U

1. 在浏览器登录 music.163.com
2. 打开开发者工具 -> Application -> Cookies
3. 复制 `MUSIC_U` 的值，粘贴到 App 登录页

Cookie 只保存在本机，不经过任何中间服务器。

## 构建

**请勿在本地安装 JDK / Android SDK 等构建环境，也不要在本地执行编译构建。** 编译与发布一律由 GitHub Actions 完成（`.github/workflows/android.yml`）：提交并推送到 `main`，CI 自动构建 Debug / Release APK 并发布 Release，结果在仓库 Actions 页查看（`gh run watch`）。

仅在 CI 不可用等特殊情况下才考虑本地构建兜底（环境要求：JDK 21、Android SDK 36、Gradle 9.3.1，项目内置 Wrapper）：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## 免责声明

1. 本项目仅供个人学习和技术研究使用。
2. 本项目不包含任何商业目的，不提供任何增值服务。
3. 软件调用的所有音频及元数据均来自公开网络接口，版权归原权利人所有。请在下载安装后 24 小时内删除，严禁用于商业用途。
4. 使用本项目产生的任何问题由使用者自行承担。


