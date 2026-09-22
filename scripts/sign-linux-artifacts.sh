#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# WuZhuFolio Linux 发布产物签名（P7 携带项 ④，ADR-006 §2.1）
#
# 做什么：对发布目录下的 Linux 产物生成 SHA256SUMS，并用发布 GPG 密钥做**分离签名**
#        （每个产物一个 .asc + SHA256SUMS.asc）——这是 GitHub Releases 消费者实际校验的形态
#        （`gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS`）。
#
# 不做什么：**不改包内签名**（dpkg-sig / rpm --addsign 属包管理器内嵌签名，需在 CI 上
#          `apt-get install -y dpkg-sig rpm` 后执行；口径见 signing-notarization.md §4.3）。
#
# 用法：
#   scripts/sign-linux-artifacts.sh <产物目录> [--key <KEYID>] [--verify]
#   scripts/sign-linux-artifacts.sh dist --key releases@wuzhufolio            # 签名
#   scripts/sign-linux-artifacts.sh dist --verify                             # 校验
#
# 环境变量：
#   WZF_GPG_HOME   指定 GNUPGHOME（默认沿用用户 gnupg；CI 用临时目录导入私钥）
#   WZF_GPG_KEY    签名密钥 ID/指纹（等价于 --key）
#   WZF_PASSPHRASE 私钥口令（CI 由 Secrets 注入；为空 = 无口令密钥或已用 gpg-agent 缓存）
#
# 退出码：0 成功 / 2 用法错误 / 3 缺少工具 / 4 无产物 / 5 签名失败 / 6 校验失败
# ---------------------------------------------------------------------------
set -euo pipefail

DIR=""
KEY="${WZF_GPG_KEY:-}"
VERIFY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --key) KEY="${2:-}"; shift 2 ;;
        --verify) VERIFY=1; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        -*) echo "未知参数：$1" >&2; exit 2 ;;
        *) DIR="$1"; shift ;;
    esac
done

[[ -n "$DIR" ]] || { echo "用法：scripts/sign-linux-artifacts.sh <产物目录> [--key KEYID] [--verify]" >&2; exit 2; }
[[ -d "$DIR" ]] || { echo "目录不存在：$DIR" >&2; exit 2; }
command -v gpg >/dev/null || { echo "缺少 gpg（apt-get install -y gnupg）" >&2; exit 3; }
command -v sha256sum >/dev/null || { echo "缺少 sha256sum（coreutils）" >&2; exit 3; }

[[ -n "${WZF_GPG_HOME:-}" ]] && export GNUPGHOME="$WZF_GPG_HOME"

cd "$DIR"
SUMS="SHA256SUMS"

# 产物集合：jpackage 原生包 + AppImage + 便携版（Linux 侧）
mapfile -t FILES < <(find . -maxdepth 1 -type f \
    \( -name '*.deb' -o -name '*.rpm' -o -name '*.AppImage' -o -name '*-portable-*.tar.gz' \) \
    -printf '%f\n' | sort)
[[ ${#FILES[@]} -gt 0 ]] || { echo "目录内没有 Linux 发布产物：$DIR" >&2; exit 4; }

if [[ $VERIFY -eq 1 ]]; then
    echo "== 校验模式 =="
    [[ -f "$SUMS" && -f "$SUMS.asc" ]] || { echo "缺少 $SUMS / $SUMS.asc" >&2; exit 6; }
    gpg --verify "$SUMS.asc" "$SUMS" || { echo "SHA256SUMS 签名校验失败" >&2; exit 6; }
    sha256sum -c "$SUMS" || { echo "校验和不符" >&2; exit 6; }
    for f in "${FILES[@]}"; do
        if [[ -f "$f.asc" ]]; then
            gpg --verify "$f.asc" "$f" >/dev/null 2>&1 && echo "  ✓ $f 分离签名有效" \
                || { echo "  ✗ $f 分离签名无效" >&2; exit 6; }
        else
            echo "  · $f 无分离签名（仅由 SHA256SUMS 覆盖）"
        fi
    done
    echo "== 校验通过（${#FILES[@]} 个产物）=="
    exit 0
fi

[[ -n "$KEY" ]] || { echo "签名需要 --key <KEYID> 或 WZF_GPG_KEY" >&2; exit 2; }

GPG_ARGS=(--batch --yes --armor --local-user "$KEY" --detach-sign)
[[ -n "${WZF_PASSPHRASE:-}" ]] && GPG_ARGS+=(--pinentry-mode loopback --passphrase "$WZF_PASSPHRASE")

echo "== 生成 $SUMS（${#FILES[@]} 个产物）=="
rm -f "$SUMS" "$SUMS.asc"
sha256sum "${FILES[@]}" > "$SUMS"
cat "$SUMS"

echo "== 逐个分离签名 =="
for f in "${FILES[@]}"; do
    rm -f "$f.asc"
    gpg "${GPG_ARGS[@]}" --output "$f.asc" "$f" || { echo "签名失败：$f" >&2; exit 5; }
    echo "  ✓ $f.asc"
done

echo "== 签名 $SUMS 清单 =="
gpg "${GPG_ARGS[@]}" --output "$SUMS.asc" "$SUMS" || { echo "签名失败：$SUMS" >&2; exit 5; }

echo
echo "完成。发布者请同时提供：产物 + SHA256SUMS + SHA256SUMS.asc（+ 各产物 .asc）"
echo "用户校验：gpg --verify SHA256SUMS.asc SHA256SUMS && sha256sum -c SHA256SUMS"
