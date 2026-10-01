# 云盘歌曲音质档位研究

播放音质切换功能（`SongInfoSheet` 内的音质块）在云盘场景下的真实作用尚未定稿。
本文档记录 2026-10-01 真机日志的验证结论与后续待办；**当前代码保持五档现状，未做调整**。

## 验证结论（2026-10-01，真机日志 diagnostic-20261001-151251）

请求 `eapi/song/enhance/player/url/v1` 的 `level` 参数，实测返回：

| 请求档位 | 实际下发 | 响应 level |
|---|---|---|
| exhigh（320k） | 928 kbps FLAC / 798 kbps FLAC | lossless |
| lossless | 888 kbps FLAC | lossless |
| hires | 971 kbps FLAC | lossless |
| standard（128k） | 322 kbps MP3 · 12.0 MB | exhigh |

结论：

1. **exhigh / lossless / hires 对无损上传的云盘歌曲返回的都是 FLAC 原文件**——exhigh 不会降级成 320k 转码（与网易曲库歌曲的行为不同）。云盘默认档位即拿原件。
2. hires 请求对 44.1k 源被封顶回 lossless（源数据 `hr` 为 null），不报错，App 无需特殊处理。
3. 只有 standard 档有实际效果：服务端转码为 320k MP3（响应 level 被钳到 exhigh，不给 128k），体积约为原件的 40%，用途是省流量。

验证方法与数据链路：`PlayerController` 取址日志（`取址完成 songId=xx name=xx level=xx br=xx type=xx size=xx`）+
「实际下发」展示行（`SongInfoSheet`），拉取用 `python tools/fetch_remote_log.py`（见 [REMOTE_LOG.md](REMOTE_LOG.md)）。

## 待办（TODO）

- [ ] 决定音质切换的最终形态，候选方案：
  - A. 彻底移除音质 UI，`level` 硬编码 `exhigh`（云盘场景下即"原文件"）；
  - B. 精简为两档："原文件"（exhigh）/ "省流量"（standard），诚实反映云盘语义（倾向推荐）；
  - C. 保持现状（五档）。
- [ ] 若后续上传 24bit/96k 等高规格文件，重新验证 hires 档位是否有真实增益（本次样本 `hr` 均为 null，无法证明）。
- [ ] "以新音质重播当前曲"（原位置续播，`setMediaItem(item, startPositionMs)`）——仅在选定方案且确认有需要时再做。

重启此项研究的触发时机：上传了高规格音频文件，或明确需要省流量开关时。
