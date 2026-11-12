#!/usr/bin/env python3
"""自媒体采集脚本 —— 把各平台的数字抓成 app 认识的 media_snapshot.json。

协议（app 与脚本之间唯一的契约）见 docs/opc-console-design.md §4.2，
app 侧的实现是 opc-core 的 MediaSnapshot.kt。

这个脚本只做三件事：
1. 读配置 accounts.json（账号列表 + 每个账号的数据来源）
2. 读每个来源文件（JSON：一条或一组指标）→ 拼成 media_snapshot.json
3. 校验产物（--check）

**它不负责 push** —— push 用同目录的 push.sh（要 git 和一个 PAT）。
**它不负责抓取** —— 平台数据从哪来由你决定：手动导出的 JSON、
第三方 API 的返回、或者自己写的抓取器，只要写成下面这个格式就行。

用法：
    python collect.py --config accounts.json --out media_snapshot.json
    python collect.py --config accounts.json --out media_snapshot.json --check
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone

# media_snapshot.json 里每个账号/指标允许的字段（多一个少一个都算错）
ACCOUNT_FIELDS = {"platform", "handle", "name"}
METRIC_FIELDS = {
    "accountId", "date", "followers", "views",
    "interactions", "newFollowers", "revenue",
}


def read_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def account_id(platform, handle):
    """app 侧的账号 id —— app 会把它转成安全文件名，脚本这边保持一样的口径。"""
    return "%s:%s" % (platform, handle or platform)


def pick_metric(raw):
    """来源文件可以是一条指标，也可以是一组指标 —— 取最后一条（按日期）。"""
    if isinstance(raw, dict):
        raw = [raw]
    if not isinstance(raw, list) or not raw:
        return None
    items = [m for m in raw if isinstance(m, dict) and m.get("date")]
    if not items:
        return None
    items.sort(key=lambda m: m["date"])
    return items[-1]


def build_snapshot(config, base_dir):
    accounts, metrics = [], []
    for account in config.get("accounts", []):
        platform = account.get("platform", "")
        handle = account.get("handle", "")
        name = account.get("name", "") or handle or platform
        if not platform:
            continue
        accounts.append({"platform": platform, "handle": handle, "name": name})

        source = account.get("source", "")
        if not source:
            continue
        path = os.path.join(base_dir, source)
        if not os.path.exists(path):
            print("跳过 %s：找不到数据文件 %s" % (name, path), file=sys.stderr)
            continue
        metric = pick_metric(read_json(path))
        if metric is None:
            print("跳过 %s：数据文件里没有可用指标" % name, file=sys.stderr)
            continue
        metrics.append({
            "accountId": account_id(platform, handle),
            "date": metric["date"],
            "followers": metric.get("followers", 0),
            "views": metric.get("views", 0),
            "interactions": metric.get("interactions", 0),
            "newFollowers": metric.get("newFollowers", 0),
            "revenue": metric.get("revenue", 0),
        })
    return {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "accounts": accounts,
        "metrics": metrics,
        "contents": config.get("contents", []),
    }


def check_snapshot(snapshot):
    """产物自检 —— app 那边解析失败就整次同步白跑，这里先挡住。"""
    problems = []
    if not snapshot.get("generatedAt"):
        problems.append("缺 generatedAt")
    known = {a["platform"] + ":" + a.get("handle", "") for a in snapshot.get("accounts", [])}
    for account in snapshot.get("accounts", []):
        if not account.get("platform"):
            problems.append("有账号缺 platform")
        if set(account) - ACCOUNT_FIELDS:
            problems.append("账号字段多了：%s" % (set(account) - ACCOUNT_FIELDS))
    for metric in snapshot.get("metrics", []):
        if set(metric) - METRIC_FIELDS:
            problems.append("指标字段多了：%s" % (set(metric) - METRIC_FIELDS))
        if not metric.get("date"):
            problems.append("有指标缺 date")
        if metric.get("accountId") not in known:
            problems.append("指标指向了不存在的账号：%s" % metric.get("accountId"))
    return problems


def main():
    parser = argparse.ArgumentParser(description="生成 media_snapshot.json")
    parser.add_argument("--config", default="accounts.json", help="账号配置文件")
    parser.add_argument("--out", default="media_snapshot.json", help="产物路径")
    parser.add_argument("--check", action="store_true", help="只校验，不写文件")
    args = parser.parse_args()

    base_dir = os.path.dirname(os.path.abspath(args.config)) or "."
    config = read_json(args.config)
    snapshot = build_snapshot(config, base_dir)

    problems = check_snapshot(snapshot)
    if problems:
        for p in problems:
            print("✗ %s" % p, file=sys.stderr)
        return 1

    if not args.check:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(snapshot, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("✓ %d 个账号，%d 条指标" % (len(snapshot["accounts"]), len(snapshot["metrics"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
