#!/usr/bin/env bash
# 把 media_snapshot.json push 到目标仓库 —— app 下次同步就能拿到。
#
# 需要的环境变量：
#   MEDIA_REPO      owner/repo（快照仓库，建议 private）
#   MEDIA_BRANCH    分支（默认 main）
#   MEDIA_CLONE_DIR 本地克隆目录（默认 ~/.opc-media）
#   GITHUB_TOKEN    push 用（别写进仓库）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${MEDIA_REPO:?要设 MEDIA_REPO（owner/repo）}"
BRANCH="${MEDIA_BRANCH:-main}"
CLONE_DIR="${MEDIA_CLONE_DIR:-$HOME/.opc-media}"
SNAPSHOT_PATH="${MEDIA_PATH:-media_snapshot.json}"

cd "$ROOT"
python3 collect.py --config accounts.json --out media_snapshot.json

if [ ! -d "$CLONE_DIR/.git" ]; then
  git clone --depth 1 --branch "$BRANCH" "https://github.com/$REPO.git" "$CLONE_DIR"
fi

cp media_snapshot.json "$CLONE_DIR/$SNAPSHOT_PATH"
cd "$CLONE_DIR"
git checkout "$BRANCH"
git add "$SNAPSHOT_PATH"
if git diff --cached --quiet; then
  echo "快照没变，不用 push"
  exit 0
fi
git commit -m "chore: 更新自媒体快照 $(date +%Y-%m-%d)"
git push origin "$BRANCH"
echo "✓ 已推送到 $REPO ($BRANCH)"
