# 云盘歌词纠正：可行性调研与结论

> 2026-10-07 调研，**已拍板：不做该功能**。
> 结论先行：**网易云不存在"给已上传云盘歌曲事后上传/修改歌词"的任何接口**——歌词数据只能在上传歌曲那一刻随文件产生（同名 .lrc 或内嵌 LYRICS 标签）。本文档沉淀歌词两套体系的机制、接口全景查证结果与将来重启时的候选路径，避免重复调研与踩坑。

## 问题定义

云盘歌曲歌词不匹配：歌词完全跟随自动匹配结果——匹配到错的曲库歌曲 → 歌词是错的；未匹配（`matchType=unmatched`）→ 没有歌词。期望形态是"用户自己上传歌词文件纠正"，本文回答它在网易侧是否可行（答案：不可行）。

## 网易云歌词是两套互不可见的体系

| 体系 | 读取接口 | 数据来源 | App 现状 |
|---|---|---|---|
| 曲库匹配歌词 | `/eapi/song/lyric/v1` | 曲库歌曲自带的官方歌词（翻译/罗马音/yrc 逐字） | ✅ 当前唯一使用的体系 |
| 云盘自有歌词 | `/eapi/cloud/lyric/get` | **上传那一刻**随文件产生：官方 PC 客户端上传时自动附带网盘里的同名 .lrc（需 UTF-8），或音频文件内嵌 LYRICS 标签 | ❌ 未使用 |

关键机制：**两套体系互不可见**。普通歌词接口查不到云盘自有歌词，云盘歌词接口也查不到普通歌曲（出处见 [issue #1369](https://github.com/Binaryify/NeteaseCloudMusicApi/issues/1369)）。歌词上传的唯一时机就是上传歌曲那一刻，之后没有任何补传/修改通道。

云盘歌词接口细节（eapi）：

- 完整 URL：`https://interface.music.163.com/eapi/cloud/lyric/get`，内部路径 `/api/cloud/lyric/get`（weapi 路由 `/weapi/cloud/lyric/get` 同样存在）
- 参数：`songId`（**云盘私有 id**，即 `/v1/cloud/get` 列表项的顶层 songId）、`userId`（当前登录账号 uid）、`lv`/`kv`/`tv` 传 0
- 只有"获取"一个方向，没有对应的 save/upload

## API 全景查证：歌词接口全部只读

以社区维护的完整接口索引（[AboutUip/ZMusic netease-new 索引](https://github.com/AboutUip/ZMusic/blob/master/docs/netease-new/INDEX.md)，398 个接口）为准核对：

- 歌词相关：获取歌词（89）、逐字歌词（90）、**云盘歌词（176，仅获取）**、声音歌词（298）、歌词摘录增删改查（342-345，那是用户的歌词笔记，不是歌曲歌词数据本身）
- 云盘相关：列表（171/172）、删除（173）、上传（174）、**匹配纠正（175）**、云盘歌词（176）、导入（318）、下载链接（379）

**不存在**"上传歌词 / 修改歌词 / 保存歌词"类接口。Binaryify 老版本文档同样没有，不是资料过时问题。

## App 现状（与本文相关的代码）

- `PlaybackApi.kt`：`/eapi/song/lyric/v1`，`yv=99` 换逐字歌词；请求 id 用的是云盘私有 songId（`CloudRepository.kt` 映射时 `simpleSongId = item.songId`，两者同值），所以歌词完全跟着匹配结果走
- `LyricsCache.kt`：按 songId 的 JSON 磁盘缓存（`filesDir/lyrics/<songId>.json`），设置页可开关
- `CloudApi.kt`：`/weapi/cloud/user/song/match`（`CloudMatchRequest(userId, songId, adjustSongId)`）**已定义、未接任何 UI**，响应体与删除共用 `CloudActionResponse`——将来做"匹配纠正"时接口层是现成的

## 将来重启时的候选路径（按推荐顺序）

- **A. 本地歌词覆盖层（推荐，成本最低）**：基于 `LyricsCache` 加一层"手动导入 .lrc → 本地优先于云端"。效果等同"自己纠正歌词"，但只在 App 内生效、不上传网易云；不依赖任何网易接口，零风险。
- **B. 接上云盘匹配（官方语义的纠正）**：把已有的 `/weapi/cloud/user/song/match` 接上 UI，重新匹配到正确曲库歌曲后歌词/封面/元信息一并纠正。限制：**曲库里必须存在这首歌**，曲库外的歌无解。
- **C. 删歌重传**：把歌词（UTF-8 同名 .lrc 或内嵌 LYRICS 标签）跟音频一起重新上传。唯一能让曲库外歌曲在网易体系内有歌词的途径，但需要新建完整上传链路（`/api/cloud/upload/token` → NOS 直传 → `/api/upload/cloud/complete/v1`，实现参考 [ncm-api-rs](https://github.com/SPlayer-Dev/ncm-api-rs) 的 cloud_upload_token / cloud_upload_complete），工作量大且有流量成本。

另一个低成本改进（可与 A/B 同做）：对已匹配歌曲两个歌词接口都拉、云盘自有歌词优先——这是官方客户端的行为，[go-musicfox](https://github.com/go-musicfox/go-musicfox) 即此套路；当前 App 只拉曲库歌词，上传时带过同名 .lrc 的歌会"有歌词却显示不出"。

## 参考资料

- [Binaryify/NeteaseCloudMusicApi issue #1369「新增云盘歌词接口」](https://github.com/Binaryify/NeteaseCloudMusicApi/issues/1369) —— 云盘歌词接口的出处与机制说明
- [AboutUip/ZMusic netease-new 接口索引](https://github.com/AboutUip/ZMusic/blob/master/docs/netease-new/INDEX.md) —— 398 接口全景，本调研"歌词接口全只读"结论的核对基准
- [SPlayer-Dev/ncm-api-rs](https://github.com/SPlayer-Dev/ncm-api-rs) —— cloud_lyric_get / cloud_upload_token / cloud_upload_complete 的实现参考
- [go-musicfox](https://github.com/go-musicfox/go-musicfox) —— 云盘歌曲"云盘歌词优先、曲库歌词兜底"的实践
