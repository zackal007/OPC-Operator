# 自媒体采集脚本

把各平台的数字抓成 `media_snapshot.json`，push 到一个仓库；
app（`:opc-core`）拉这个文件写进本地存储。

**协议是唯一契约** —— app 侧实现见
`opc-core/src/commonMain/kotlin/com/androidkit/app/opc/data/media/MediaSnapshot.kt`，
设计说明见 `docs/opc-console-design.md` §4.2。

## 用法

```bash
cd scripts/media-sync

cp accounts.example.json accounts.json      # 改成自己的账号
mkdir -p data
cp data/wechat_mp.example.json data/wechat_mp.json   # 填上今天的数字

python collect.py --config accounts.json --out media_snapshot.json
python collect.py --config accounts.json --out media_snapshot.json --check   # 只校验不写文件
```

产物不对（字段多了、指标指向不存在的账号）会打印原因并退出码 1。

## 推给 app

```bash
export MEDIA_REPO=zackal/opc-media     # 快照仓库（建议 private）
export GITHUB_TOKEN=ghp_xxx            # 只在 shell 里给，别写进仓库
bash push.sh
```

app 设置里填同一个 `owner/repo`，点「立即同步」就能拉到。

## 数据从哪来

这个脚本**不抓取**，只拼装。各平台的数字从哪来由你定，只要每个来源文件是下面这个格式：

```json
{
  "date": "2026-11-12",
  "followers": 12800,
  "views": 3200,
  "interactions": 96,
  "newFollowers": 128,
  "revenue": 0
}
```

也可以给一个数组（多条指标），脚本取日期最后一条。

想自动抓，就在 `collect.py` 前面加一步：写一个自己的抓取器，把结果落成上面这个
JSON 文件，再跑 `collect.py`。app 那边不用动 —— 它只认 `media_snapshot.json`。

## 降级

脚本挂了、平台接口变了、token 过期 —— 都**不影响 app 里手动录的数据**。
同步只是「多一份数据源」，不是唯一入口。
