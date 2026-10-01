#!/usr/bin/env python3
"""从 OpenList/Alist 拉取 ncm-cloud-player 最新远程诊断日志。

配置文件：仓库根目录 openlist.properties（已 gitignore），内容：
    base_url=https://host:port
    username=xxx
    password=xxx
    target_path=/ncm-cloud-player/logs

用法：
    python tools/fetch_remote_log.py            # 打印最新一条日志
    python tools/fetch_remote_log.py --list     # 列出最近 20 个日志文件
"""
import argparse
import configparser
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

SALT = "https://github.com/alist-org/alist"
ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "openlist.properties"


def load_config():
    if not CONFIG.exists():
        sys.exit(f"缺少配置文件：{CONFIG}（已 gitignore，需手动填写 OpenList 地址/账号/密码）")
    cp = configparser.ConfigParser()
    # configparser 需要节，这里用宽松解析
    text = "[DEFAULT]\n" + CONFIG.read_text(encoding="utf-8")
    cp.read_string(text)
    d = cp["DEFAULT"]
    return {k: d[k] for k in ["base_url", "username", "password", "target_path"]}


def post_json(base, path, body, token=None, timeout=30):
    data = json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    req = urllib.request.Request(base + path, data=data, headers=headers, method="POST")
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        sys.exit(f"HTTP {e.code} {base}{path}\n{body}")
    except Exception as e:
        sys.exit(f"请求失败 {base}{path}: {e}")


def login(base, username, password):
    digest = hashlib.sha256((password + "-" + SALT).encode()).hexdigest()
    res = post_json(base, "/api/auth/login/hash", {"username": username, "password": digest, "otp_code": ""})
    token = (res.get("data") or {}).get("token", "")
    if not token:
        sys.exit(f"OpenList 登录失败：{res}")
    return token


def list_dir(base, token, path):
    res = post_json(base, "/api/fs/list", {"path": path, "page": 1, "per_page": 100, "refresh": False}, token)
    return (res.get("data") or {}).get("content") or []


def fix_raw_url(base, raw_url):
    """Alist 返回的 raw_url 有时会丢端口，补回 base 的端口。"""
    if not raw_url:
        return raw_url
    from urllib.parse import urlparse, urlunparse, ParseResult
    b = urlparse(base)
    r = urlparse(raw_url)
    if b.port and not r.port:
        host = r.hostname or b.hostname
        netloc = f"{host}:{b.port}"
        r = ParseResult(r.scheme or b.scheme, netloc, r.path, r.params, r.query, r.fragment)
    return urlunparse(r)


def download_text(raw_url):
    # Alist 的 /p 链接可能有自签证书，用 curl 容忍
    try:
        result = subprocess.run(
            ["curl", "--ssl-no-revoke", "-k", "-sL", raw_url],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode != 0:
            sys.exit(f"curl 下载失败：{result.stderr}")
        return result.stdout
    except FileNotFoundError:
        # 回退到 urllib，忽略证书
        import ssl
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        req = urllib.request.Request(raw_url)
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            return resp.read().decode("utf-8", errors="replace")


def collect_files(base, token, target_path):
    """返回 [(相对 target_path 的路径, 条目)]；子目录里的文件路径必须带目录前缀，
    否则 /api/fs/get 会报 object not found。"""
    entries = list_dir(base, token, target_path)
    files = []
    for entry in entries:
        if entry.get("is_dir"):
            sub = list_dir(base, token, f"{target_path}/{entry['name']}")
            for f in sub:
                if not f.get("is_dir"):
                    files.append((f"{entry['name']}/{f['name']}", f))
        else:
            files.append((entry["name"], entry))
    files.sort(key=lambda item: item[1].get("modified", ""), reverse=True)
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="只列出日志文件，不下载")
    ap.add_argument("--all", action="store_true", help="打印所有日志，不只最新一条")
    args = ap.parse_args()

    cfg = load_config()
    base = cfg["base_url"].rstrip("/")
    token = login(base, cfg["username"], cfg["password"])
    files = collect_files(base, token, cfg["target_path"])
    if not files:
        sys.exit("远程日志目录为空")

    if args.list:
        for rel, f in files[:20]:
            print(f"{f['modified']}  {f.get('size',0):>8}  {rel}")
        return

    selected = files if args.all else files[:1]
    for rel, f in selected:
        file_path = f"{cfg['target_path']}/{rel}"
        info = post_json(base, "/api/fs/get", {"path": file_path}, token)
        raw_url = fix_raw_url(base, (info.get("data") or {}).get("raw_url", ""))
        print(f"===== {rel} ({f.get('size')} bytes) =====")
        if raw_url:
            print(download_text(raw_url))
        else:
            print(json.dumps(info, ensure_ascii=False, indent=2)[:2000])
        print()


if __name__ == "__main__":
    main()
