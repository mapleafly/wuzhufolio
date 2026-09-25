#!/usr/bin/env bash
#
# DEF-52（2026-09-24 人工拍板 C1）：Linux .deb 的桌面集成补齐（打包后处理）。
#
# 背景（人工在 Ubuntu 24.04 实测）：
#   - 顶部栏/Dock 显示**通用齿轮**图标 → 根因是 jpackage 生成的桌面条目 `Icon=` 指向
#     `/opt/.../lib/WuZhuFolio.png`（绝对路径、未进图标主题），且**没有** `StartupWMClass`，
#     窗口与桌面条目关联不上；`Categories=Unknown`（DSL 的 appCategory 未落到该字段）；
#   - `postinst` 只跑 `xdg-desktop-menu install`，**没有**刷新图标缓存/桌面数据库。
#
# 本脚本对 jpackage 产出的 .deb 做最小后处理（不动应用本体、不改依赖/控制字段语义）：
#   ① `.desktop`：`Icon=wuzhufolio`（按主题名解析）、`Categories=Office;Finance;`、
#      追加 `StartupWMClass=<实测 WM_CLASS>`（见下，取自运行中的打包版窗口）；
#   ② 把图标装进 `usr/share/icons/hicolor/<N>x<N>/apps/wuzhufolio.png`
#      （尺寸来自 `app/icons/wuzhufolio.ico` 的 PNG 载荷 + 512 主图；无 python3 时退化为只装 512）；
#   ③ `postinst` 追加 `update-desktop-database` / `gtk-update-icon-cache`（缺失时静默跳过）。
#
# 用法：
#   scripts/patch-linux-desktop-integration.sh <path/to/xxx.deb> [更多 deb...]
#   校验（不修改）：scripts/patch-linux-desktop-integration.sh --check <deb>
#
# 退出码：0 = 处理/校验通过；非 0 = 失败（CI 中应视为构建失败）。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
APP_ICON_PNG="$ROOT/app/icons/wuzhufolio.png"
APP_ICON_ICO="$ROOT/app/icons/wuzhufolio.ico"

# 实测值（Ubuntu 24.04 / JDK 21 / jpackage）：`xprop -id <client> WM_CLASS`
#   = "com-wuzhufolio-app-MainKt", "com-wuzhufolio-app-MainKt"（AWT 取主类名，`.` → `-`）。
# 取法注意：**不要用 `xprop -name WuZhuFolio`**——它会命中窗口框架（mutter-x11-frames）从而取错值；
# 正确取法是 `_NET_CLIENT_LIST` 遍历 + `_NET_WM_PID` 反查（见 docs/test/defects.md §2.3）。
WM_CLASS="com-wuzhufolio-app-MainKt"
ICON_NAME="wuzhufolio"         # 图标主题名（hicolor/apps 下的文件名去扩展名）
CATEGORIES="Office;Finance;"

CHECK_ONLY=0
if [ "${1:-}" = "--check" ]; then CHECK_ONLY=1; shift; fi
if [ "$#" -eq 0 ]; then
  echo "用法：$0 [--check] <deb> [deb...]" >&2
  exit 2
fi
command -v dpkg-deb >/dev/null || { echo "需要 dpkg-deb（Debian/Ubuntu：dpkg-dev）" >&2; exit 3; }

WORK_ROOT="$(mktemp -d)"
trap 'rm -rf "$WORK_ROOT"' EXIT

# 从 ICO 里抽出 PNG 载荷（各尺寸）到目录；无 python3/ICO 时返回空
extract_ico_pngs() {
  local outdir="$1"
  [ -f "$APP_ICON_ICO" ] || return 0
  command -v python3 >/dev/null || return 0
  python3 - "$APP_ICON_ICO" "$outdir" <<'PY'
import struct, sys
ico, outdir = sys.argv[1], sys.argv[2]
data = open(ico, 'rb').read()
if data[:4] != b'\x00\x00\x01\x00':
    sys.exit(0)
count = struct.unpack('<H', data[4:6])[0]
for i in range(count):
    off = 6 + i * 16
    width = data[off] or 256
    size = struct.unpack('<I', data[off + 8:off + 12])[0]
    start = struct.unpack('<I', data[off + 12:off + 16])[0]
    payload = data[start:start + size]
    if payload[:8] == b'\x89PNG\r\n\x1a\n':
        open(f'{outdir}/{width}.png', 'wb').write(payload)
PY
}

patch_deb() {
  local deb="$1"
  local name; name="$(basename "$deb")"
  [ -f "$deb" ] || { echo "找不到 $deb" >&2; return 1; }

  local work="$WORK_ROOT/${name%.deb}"
  rm -rf "$work"; mkdir -p "$work"
  dpkg-deb -R "$deb" "$work"

  # 注意：jlink 运行时的 `legal/java.desktop` 是**目录**，必须限定 -type f（实测踩坑）
  local desktop; desktop="$(find "$work" -type f -name '*.desktop' -print -quit)"
  [ -n "$desktop" ] || { echo "[$name] 包内没有 .desktop，跳过" >&2; return 0; }

  local app_lib; app_lib="$(dirname "$desktop")"      # /opt/<pkg>/lib
  local app_root; app_root="$(dirname "$app_lib")"    # /opt/<pkg>

  echo "[$name] 桌面条目：${desktop#"$work"/}"

  # ① .desktop 字段
  #   - 去掉原有 Icon/Categories/StartupWMClass 行后统一重写（幂等，可重复执行）
  local tmp; tmp="$(mktemp)"
  grep -vE '^(Icon|Categories|StartupWMClass)=' "$desktop" > "$tmp" || true
  {
    cat "$tmp"
    echo "Icon=$ICON_NAME"
    echo "Categories=$CATEGORIES"
    echo "StartupWMClass=$WM_CLASS"
  } > "$desktop"
  rm -f "$tmp"

  # ② 图标进主题（hicolor）
  local icons_dir="$work/usr/share/icons/hicolor"
  mkdir -p "$icons_dir"
  local pngs="$WORK_ROOT/pngs"; rm -rf "$pngs"; mkdir -p "$pngs"
  extract_ico_pngs "$pngs"
  local installed=0
  for f in "$pngs"/*.png; do
    [ -e "$f" ] || continue
    local size; size="$(basename "$f" .png)"
    mkdir -p "$icons_dir/${size}x${size}/apps"
    cp "$f" "$icons_dir/${size}x${size}/apps/$ICON_NAME.png"
    installed=$((installed + 1))
  done
  if [ -f "$APP_ICON_PNG" ]; then
    mkdir -p "$icons_dir/512x512/apps"
    cp "$APP_ICON_PNG" "$icons_dir/512x512/apps/$ICON_NAME.png"
    installed=$((installed + 1))
  fi
  echo "[$name] 装入 hicolor 图标 $installed 个尺寸"

  # ③ postinst：刷新桌面数据库与图标缓存（工具缺失时静默跳过——不因缺 xdg-utils 而安装失败）
  local postinst="$work/DEBIAN/postinst"
  if [ -f "$postinst" ]; then
    if ! grep -q 'update-desktop-database' "$postinst"; then
      python3 - "$postinst" <<'PY' 2>/dev/null || true
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
needle = "xdg-desktop-menu install"
idx = s.find(needle)
if idx < 0:
    sys.exit(0)
end = s.find("\n", idx)
block = ("\n# DEF-52：刷新桌面条目数据库与图标缓存（GNOME 需要，缺失时静默跳过）\n"
         "command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q || true\n"
         "command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true\n")
open(p, 'w', encoding='utf-8').write(s[:end + 1] + block + s[end + 1:])
PY
      # 无 python3 时用 sed 兜底
      if ! grep -q 'update-desktop-database' "$postinst"; then
        sed -i 's|^\(\s*\)xdg-desktop-menu install \(.*\)$|\1xdg-desktop-menu install \2\n\1command -v update-desktop-database >/dev/null 2>\&1 \&\& update-desktop-database -q \|\| true\n\1command -v gtk-update-icon-cache >/dev/null 2>\&1 \&\& gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor \|\| true|' "$postinst"
      fi
      echo "[$name] postinst 已追加缓存刷新"
    fi
  fi

  if [ "$CHECK_ONLY" -eq 1 ]; then
    echo "[$name] --check：仅校验（未写回）"
    return 0
  fi

  # 重新打包（保持原控制字段；root 属主与安装包一致）
  dpkg-deb --build --root-owner-group "$work" "$deb" >/dev/null
  echo "[$name] 已重打包 ✅"
}

for deb in "$@"; do
  patch_deb "$deb"
done

echo "完成：$# 个包"
