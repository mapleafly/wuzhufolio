#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# WuZhuFolio AppImage 打包（M13 T13.2，ADR-006 §1）
#
# 为什么单独一个脚本：jpackage 不产出 AppImage（Linux 只有 deb/rpm 是 jpackage 原生格式），
# ADR-006 §1 已定「AppImage 用 appimagetool 包 jpackage app-image」——本脚本即该步骤的落点，
# 本地与 CI 共用同一条路径（CI 见 .github/workflows/ci.yml 的 package job）。
#
# 用法：
#   ./gradlew :app:createDistributable        # 先产出 app-image
#   scripts/package-appimage.sh [输出目录]     # 默认 app/build/compose/binaries/main/appimage
#
# 环境变量（均有默认值，CI 无需额外配置）：
#   APPIMAGE_TOOL      appimagetool 可执行文件路径（已下载时复用，避免重复下载）
#   APPIMAGE_TOOL_URL  下载地址（默认 AppImage/appimagetool continuous，按本机架构选择）
#   APPIMAGE_RUNTIME   type2 runtime 文件路径（默认缓存在用户缓存目录）
#   APPIMAGE_RUNTIME_URL  runtime 下载地址（默认 AppImage/type2-runtime continuous）
#   APPIMAGE_ARCH      目标架构（默认 uname -m 归一为 x86_64/aarch64）
#
# 说明：无 FUSE 的环境（容器/CI）通过 APPIMAGE_EXTRACT_AND_RUN=1 解包运行 appimagetool；
# 产物版本号取自 app/build.gradle.kts 的 packageVersion（唯一真源）。
# ---------------------------------------------------------------------------
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

APP_NAME="WuZhuFolio"                     # jpackage packageName（app-image 目录名 / 图标名）
APP_ID="wuzhufolio"                       # 小写标识（.desktop / 图标 / AppDir 名）
APP_IMAGE_DIR="app/build/compose/binaries/main/app/${APP_NAME}"
OUT_DIR="${1:-app/build/compose/binaries/main/appimage}"
STAGE_DIR="app/build/compose/appimage-stage"
# 工具缓存放用户缓存目录（默认），`./gradlew clean` 不会清掉、也不入库
TOOL_CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/wuzhufolio"
# 版本单一真源 = app/build.gradle.kts 的 `val appVersion`（M13 起 jpackage packageVersion 亦取自它）
VERSION="$(grep -oP 'val appVersion\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1 || true)"
if [[ -z "$VERSION" ]]; then
    VERSION="$(grep -oP 'packageVersion\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1 || true)"
fi
if [[ -z "$VERSION" ]]; then
    echo "无法从 app/build.gradle.kts 解析应用版本（appVersion/packageVersion）" >&2
    exit 4
fi

ARCH_RAW="$(uname -m)"
case "$ARCH_RAW" in
    x86_64|amd64)   DEFAULT_ARCH="x86_64" ;;
    aarch64|arm64)  DEFAULT_ARCH="aarch64" ;;
    *) echo "不支持的架构：$ARCH_RAW（仅 x86_64/aarch64）" >&2; exit 2 ;;
esac
TARGET_ARCH="${APPIMAGE_ARCH:-$DEFAULT_ARCH}"

echo "== WuZhuFolio AppImage 打包 =="
echo "版本：${VERSION} · 架构：${TARGET_ARCH}"

if [[ ! -x "${APP_IMAGE_DIR}/bin/${APP_NAME}" ]]; then
    echo "缺少 app-image：${APP_IMAGE_DIR}（先执行 ./gradlew :app:createDistributable）" >&2
    exit 3
fi

# ---- 1. 组装 AppDir（appimagetool 要求的目录布局：AppRun + .desktop + 图标 + 载荷）----
echo "-- 组装 AppDir：${STAGE_DIR}/${APP_ID}.AppDir"
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
APPDIR="${STAGE_DIR}/${APP_ID}.AppDir"
cp -a "$APP_IMAGE_DIR" "$APPDIR"

# AppRun：包装脚本而非符号链接——jpackage 启动器按自身真实路径定位 lib/ 与私有 JRE，
# 经 exec 传入真实可执行文件可保证解析正确（符号链接会让 argv[0] 指向 AppDir 根）。
cat > "${APPDIR}/AppRun" <<'APPRUN'
#!/bin/sh
# AppImage 入口：转发到 jpackage 启动器（相对 AppDir 根定位）
HERE="$(dirname "$(readlink -f "$0")")"
exec "${HERE}/bin/WuZhuFolio" "$@"
APPRUN
chmod +x "${APPDIR}/AppRun"

# .desktop：Exec 用桌面 ID（AppImage 运行时会解析到 AppRun），图标名与复制的 png 同名
cat > "${APPDIR}/${APP_ID}.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=本地优先的加密资产组合追踪工具
Exec=${APP_NAME}
Icon=${APP_ID}
Categories=Office;Finance;
Terminal=false
X-AppImage-Version=${VERSION}
DESKTOP

# 图标：jpackage 会把图标放在 lib/ 下（默认资产，正式图标资产归 P7）
ICON_SRC="${APP_IMAGE_DIR}/lib/${APP_NAME}.png"
if [[ -f "$ICON_SRC" ]]; then
    cp "$ICON_SRC" "${APPDIR}/${APP_ID}.png"
else
    echo "警告：未找到图标 ${ICON_SRC}（AppImage 将缺少图标）" >&2
fi

# ---- 2. 准备 appimagetool（缺失则下载到用户缓存，不入库；`./gradlew clean` 不会清掉）----
TOOL="${APPIMAGE_TOOL:-${TOOL_CACHE_DIR}/appimagetool-${TARGET_ARCH}.AppImage}"
if [[ ! -x "$TOOL" ]]; then
    TOOL_URL="${APPIMAGE_TOOL_URL:-https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${TARGET_ARCH}.AppImage}"
    echo "-- 下载 appimagetool：${TOOL_URL}"
    mkdir -p "$(dirname "$TOOL")"
    # 大文件下载偶发抖动（CI 亦可能）：重试 3 次，避免因网络抖动判定打包失败
    for attempt in 1 2 3; do
        if curl -fsSL --retry 2 --retry-delay 2 "$TOOL_URL" -o "$TOOL"; then
            break
        fi
        echo "   下载失败（第 ${attempt} 次），重试…" >&2
        sleep 3
    done
    [[ -s "$TOOL" ]] || { echo "appimagetool 下载失败：${TOOL_URL}" >&2; exit 5; }
    chmod +x "$TOOL"
fi

# ---- 3. 产出 .AppImage ----
# type2 runtime：appimagetool 默认自行下载，网络抖动时失败（实测 "Failed to download runtime"）——
# 预下载并缓存，显式 --runtime-file 传入，保证 CI/本地可重复。
RUNTIME="${APPIMAGE_RUNTIME:-${TOOL_CACHE_DIR}/runtime-${TARGET_ARCH}}"
if [[ ! -s "$RUNTIME" ]]; then
    RUNTIME_URL="${APPIMAGE_RUNTIME_URL:-https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-${TARGET_ARCH}}"
    echo "-- 下载 AppImage runtime：${RUNTIME_URL}"
    mkdir -p "$(dirname "$RUNTIME")"
    for attempt in 1 2 3; do
        if curl -fsSL --retry 2 --retry-delay 2 "$RUNTIME_URL" -o "$RUNTIME"; then
            break
        fi
        echo "   下载失败（第 ${attempt} 次），重试…" >&2
        sleep 3
    done
    [[ -s "$RUNTIME" ]] || { echo "AppImage runtime 下载失败：${RUNTIME_URL}" >&2; exit 6; }
    chmod +x "$RUNTIME"
fi

mkdir -p "$OUT_DIR"
OUT_FILE="${OUT_DIR}/${APP_ID}-${VERSION}-${TARGET_ARCH}.AppImage"
rm -f "$OUT_FILE"
echo "-- 运行 appimagetool（无 FUSE 环境自动解包执行）"
ARCH="$TARGET_ARCH" APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream \
    --runtime-file "$RUNTIME" "$APPDIR" "$OUT_FILE"

echo "-- 完成：${OUT_FILE}"
ls -l "$OUT_FILE"
sha256sum "$OUT_FILE" | tee "${OUT_FILE}.sha256"
