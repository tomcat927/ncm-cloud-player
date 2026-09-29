# ncm-cloud-player

一个专注于网易云音乐个人云盘的轻量 Android 播放器：登录、列出云盘歌曲、播放。

本仓库不依赖任何自建 NodeJS API 服务，也不打包第三方公共 API。App 内部直接构造网易 `weapi/eapi/xeapi` 请求并播放返回的音频地址。网络加密层参考自 [Melodia](https://github.com/rinchao0721/Melodia)（MIT）。

## 功能范围

- 登录：短信验证码（手机自用首选）、二维码扫码、粘贴 `MUSIC_U` Cookie
- 云盘列表：分页获取 `/weapi/v1/cloud/get`
- 播放：通过 `/eapi/song/enhance/player/url/v1` 获取临时播放地址，使用 Media3 播放
- 播放队列：云盘列表点歌即播放整列，支持上一首/下一首
- 后台播放与系统通知栏控制
- GitHub Actions 构建 Debug / Release APK，无需本地安装 Android Studio

## 如何获取 MUSIC_U

1. 在浏览器登录 music.163.com
2. 打开开发者工具 -> Application -> Cookies
3. 复制 `MUSIC_U` 的值，粘贴到 App 登录页

Cookie 只保存在本机，不经过任何中间服务器。

## 构建

本项目不要求本地构建，GitHub Actions 会自动构建。如需本地构建：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

环境要求：JDK 21、Android SDK 36、Gradle 9.3.1（项目内置 Wrapper）。

## 免责声明

1. 本项目仅供个人学习和技术研究使用。
2. 本项目不包含任何商业目的，不提供任何增值服务。
3. 软件调用的所有音频及元数据均来自公开网络接口，版权归原权利人所有。请在下载安装后 24 小时内删除，严禁用于商业用途。
4. 使用本项目产生的任何问题由使用者自行承担。


