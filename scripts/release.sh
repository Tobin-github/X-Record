#!/usr/bin/env bash
#
# 发布一个版本。
#
#   scripts/release.sh 1.0.1              # 正常发布
#   scripts/release.sh 1.0.1 --dry-run    # 只构建与归档，不改仓库、不推送
#   scripts/release.sh 1.0.1 --yes        # 跳过交互确认
#
# 脚本会依次完成：
#   1. 检查工作区是否干净、签名配置是否存在
#   2. 更新 versionName 并递增 versionCode
#   3. 跑单元测试并构建 release 包
#   4. 把 APK 与 mapping.txt 归档到仓库之外
#   5. 提交版本号、打标签、推送
#   6. 创建或替换 GitHub Release 的附件
#
# 之所以把 mapping.txt 归档到仓库之外：它 40+ MB，且记录了完整的类名映射，
# 传到公开仓库等于公开代码结构；但不存的话，混淆后的崩溃堆栈就无法还原。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERSION="${1:-}"
shift || true

DRY_RUN=false
ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --yes) ASSUME_YES=true ;;
    *) echo "未知参数：$arg" >&2; exit 2 ;;
  esac
done

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法：scripts/release.sh <版本号，形如 1.0.1> [--dry-run] [--yes]" >&2
  exit 2
fi

GRADLE_FILE="app/build.gradle.kts"
TAG="v$VERSION"
APK_NAME="X-Record-$VERSION.apk"
ARCHIVE_DIR="${XRELEASE_ARCHIVE_DIR:-$HOME/xrecord-releases}/$VERSION"

echo "==> 发布 $TAG"

# ---------- 1. 前置检查 ----------

if [[ -n "$(git status --porcelain)" ]]; then
  echo "工作区不干净，请先提交或暂存改动：" >&2
  git status --short >&2
  exit 1
fi

# 没有签名配置时 release 会是未签名的，装不上，等于白忙一场
if [[ ! -f keystore.properties ]]; then
  echo "缺少 keystore.properties，release 包将是未签名的，无法安装。" >&2
  echo "请在项目根目录创建该文件，格式见 README 的「签名配置」一节。" >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "标签 $TAG 已存在。若要替换该版本的安装包，请先删除标签：" >&2
  echo "  git tag -d $TAG && git push origin :refs/tags/$TAG" >&2
  exit 1
fi

# ---------- 2. 更新版本号 ----------

CURRENT_CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')"
NEXT_CODE=$((CURRENT_CODE + 1))

# versionCode 必须递增，否则已安装旧版的用户无法覆盖升级，
# 只能卸载重装——而卸载会清空全部记账数据。
echo "==> versionCode $CURRENT_CODE -> ${NEXT_CODE}，versionName -> $VERSION"

cp "$GRADLE_FILE" "$GRADLE_FILE.release-backup"
sed -i.bak -E "s/versionCode = [0-9]+/versionCode = $NEXT_CODE/" "$GRADLE_FILE"
sed -i.bak -E "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

restore_on_failure() {
  if [[ -f "$GRADLE_FILE.release-backup" ]]; then
    mv "$GRADLE_FILE.release-backup" "$GRADLE_FILE"
  fi
}
trap restore_on_failure ERR

# ---------- 3. 测试与构建 ----------

echo "==> 运行单元测试"
./gradlew :app:testDebugUnitTest --console=plain

echo "==> 构建 release"
./gradlew :app:assembleRelease --console=plain

APK_PATH="app/build/outputs/apk/release/app-release.apk"
MAPPING_PATH="app/build/outputs/mapping/release/mapping.txt"

if [[ ! -f "$APK_PATH" ]]; then
  echo "未找到构建产物 $APK_PATH" >&2
  exit 1
fi

# ---------- 4. 归档 ----------

mkdir -p "$ARCHIVE_DIR"
cp "$APK_PATH" "$ARCHIVE_DIR/$APK_NAME"
[[ -f "$MAPPING_PATH" ]] && cp "$MAPPING_PATH" "$ARCHIVE_DIR/mapping.txt"

echo "==> 已归档到 $ARCHIVE_DIR"
echo "    ${APK_NAME}（$(du -h "$ARCHIVE_DIR/$APK_NAME" | cut -f1)）"
echo "    mapping.txt（用于还原混淆后的崩溃堆栈，请勿公开）"

if $DRY_RUN; then
  echo
  echo "==> --dry-run：跳过提交、打标签与发布"
  restore_on_failure
  trap - ERR
  echo "    版本号改动已还原"
  exit 0
fi

# ---------- 5. 确认 ----------

if ! $ASSUME_YES; then
  echo
  read -r -p "确认提交、打标签 $TAG 并推送到远程？[y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]] || { echo "已取消"; restore_on_failure; trap - ERR; exit 1; }
fi

# ---------- 6. 提交与推送 ----------

git add "$GRADLE_FILE"
git commit -q -m "release: $VERSION"
git tag -a "$TAG" -m "X-Record $VERSION"
git push origin HEAD
git push origin "$TAG"

rm -f "$GRADLE_FILE.release-backup"
trap - ERR

# ---------- 7. 发布到 GitHub ----------

REMOTE_URL="$(git remote get-url origin)"
# 兼容 git@github.com:owner/repo.git 与 https://github.com/owner/repo.git
SLUG="$(echo "$REMOTE_URL" | sed -E 's#.*github\.com[:/]##; s#\.git$##')"

TOKEN="${GH_TOKEN:-}"
if [[ -z "$TOKEN" ]] && command -v gh >/dev/null 2>&1; then
  TOKEN="$(gh auth token 2>/dev/null || true)"
fi
# 没有 gh 时回退到 Android Studio 存在钥匙串里的令牌；
# 账号名可用 GH_KEYCHAIN_ACCOUNT 覆盖
if [[ -z "$TOKEN" ]] && command -v security >/dev/null 2>&1; then
  KEYCHAIN_ACCOUNT="${GH_KEYCHAIN_ACCOUNT:-https://github.com:Tobin-github}"
  TOKEN="$(security find-generic-password -a "$KEYCHAIN_ACCOUNT" -w 2>/dev/null || true)"
fi

if [[ -z "$TOKEN" ]]; then
  echo
  echo "==> 代码与标签已推送，但没有可用的 GitHub 凭据，未创建 Release。"
  echo "    请先执行 gh auth login，或设置 GH_TOKEN 后手动上传："
  echo "    $ARCHIVE_DIR/$APK_NAME"
  exit 0
fi

AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")
API="https://api.github.com/repos/$SLUG"

# 若该标签已有 Release 则替换其附件，否则新建
RELEASE_ID="$(curl -s "${AUTH[@]}" "$API/releases/tags/$TAG" \
  | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)"

if [[ -z "$RELEASE_ID" ]]; then
  echo "==> 创建 Release $TAG"
  RELEASE_ID="$(python3 -c "
import json
print(json.dumps({'tag_name': '$TAG', 'name': 'X-Record $VERSION', 'draft': False, 'prerelease': False}))
" | curl -s -X POST "${AUTH[@]}" "$API/releases" --data-binary @- \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('id') or d.get('message',''))")"
  [[ "$RELEASE_ID" =~ ^[0-9]+$ ]] || { echo "创建 Release 失败：$RELEASE_ID" >&2; exit 1; }
else
  echo "==> Release $TAG 已存在，替换其附件"
  curl -s "${AUTH[@]}" "$API/releases/$RELEASE_ID/assets" \
    | python3 -c "import json,sys; [print(a['id']) for a in json.load(sys.stdin)]" \
    | while read -r asset_id; do
        curl -s -o /dev/null -X DELETE "${AUTH[@]}" "$API/releases/assets/$asset_id"
      done
fi

echo "==> 上传 $APK_NAME"
UPLOAD_URL="https://uploads.github.com/repos/$SLUG/releases/$RELEASE_ID/assets?name=$APK_NAME"
UPLOAD_RESULT="$(curl -s -X POST "${AUTH[@]}" \
  -H "Content-Type: application/vnd.android.package-archive" \
  "$UPLOAD_URL" --data-binary @"$ARCHIVE_DIR/$APK_NAME")"

python3 -c "
import json,sys
d = json.loads(sys.argv[1])
if 'browser_download_url' in d:
    print('    上传成功：', d['name'], round(d['size'] / 1048576, 1), 'MB')
    print('    下载地址：', d['browser_download_url'])
    print('    摘要：', d.get('digest'))
else:
    print('    上传失败：', d.get('message'), d.get('errors'))
    sys.exit(1)
" "$UPLOAD_RESULT"

echo
echo "==> 发布完成：https://github.com/$SLUG/releases/tag/$TAG"
echo "    请务必保留 $ARCHIVE_DIR/mapping.txt，未被版本库收录。"
