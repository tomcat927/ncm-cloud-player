# 远程诊断日志

App 内置远程日志上传到 OpenList/Alist。配置文件 `openlist.properties` 位于仓库根目录，**已 gitignore**，不会提交。

## 拉取最新日志

```bash
python tools/fetch_remote_log.py            # 打印最新一条日志
python tools/fetch_remote_log.py --list     # 列出最近日志文件
python tools/fetch_remote_log.py --all      # 打印所有日志
```

脚本会自动读取 `openlist.properties`，登录 OpenList，找到 `target_path` 下最新诊断日志并下载。

## 配置文件格式

```ini
base_url=https://host:port
username=logger
password=xxxx
target_path=/ncm-cloud-player/logs
```

如果换机器或新 clone，需手动创建 `openlist.properties` 并填入 OpenList 凭据。
