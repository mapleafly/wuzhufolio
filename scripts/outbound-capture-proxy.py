#!/usr/bin/env python3
"""P6 出站抓包实证代理（docs/test/security-checklist.md §8 复核命令 5）。

用途：证明「运行期只与白名单三主机通信」——把应用/测试进程的 `https_proxy`/`http_proxy`
指向本代理，代理记录每一次 CONNECT / 绝对 URI 请求的目标主机与路径（**不解密 TLS**，
因此既不接触业务数据，也不需要证书）。

用法：
    python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
    WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-capture https_proxy=http://127.0.0.1:8899 \
      http_proxy=http://127.0.0.1:8899 ./gradlew :app:run
    # 结束后：cut -d' ' -f3 /tmp/wzf-outbound.log | sort -u   → 期望仅白名单主机

白名单（源码常量，见 SecurityGuardTest）：api.coingecko.com / pro-api.coinmarketcap.com / api.binance.com
"""

from __future__ import annotations

import argparse
import select
import socket
import socketserver
import sys
import threading
import time

WHITELIST = {"api.coingecko.com", "pro-api.coinmarketcap.com", "api.binance.com"}
_log_lock = threading.Lock()
_log_path = "/tmp/wzf-outbound.log"


def record(kind: str, host: str, target: str) -> None:
    line = f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {kind} {host} {target}"
    with _log_lock:
        with open(_log_path, "a", encoding="utf-8") as fh:
            fh.write(line + "\n")
    print(line, flush=True)


def split_host(netloc: str, default_port: int) -> tuple[str, int]:
    if "@" in netloc:  # 去掉 user:pass@（本应用不使用代理认证）
        netloc = netloc.rsplit("@", 1)[1]
    if netloc.startswith("["):  # IPv6
        host, _, port = netloc.partition("]")
        return host.lstrip("["), int(port.lstrip(":") or default_port)
    host, _, port = netloc.partition(":")
    return host, int(port or default_port)


class ProxyHandler(socketserver.StreamRequestHandler):
    """CONNECT 隧道 + 绝对 URI 的普通 HTTP 请求转发（只记录，不改内容）。"""

    def handle(self) -> None:  # noqa: C901 - 协议分支显式展开更易读
        try:
            request_line = self.rfile.readline(65536).decode("latin-1").strip()
            if not request_line:
                return
            headers: list[str] = []
            while True:
                line = self.rfile.readline(65536).decode("latin-1")
                if line in ("\r\n", "\n", ""):
                    break
                headers.append(line.strip())
            method, target, _version = request_line.split(" ", 2)

            if method.upper() == "CONNECT":
                host, port = split_host(target, 443)
                record("CONNECT", host, f"{host}:{port}")
                upstream = socket.create_connection((host, port), timeout=15)
                self.connection.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
                self._pump(self.connection, upstream)
                return

            if target.startswith("http://"):
                netloc, _, path = target[len("http://"):].partition("/")
                host, port = split_host(netloc, 80)
                record("HTTP", host, "/" + path)
                upstream = socket.create_connection((host, port), timeout=15)
                rebuilt = f"{method} /{path} HTTP/1.1\r\n" + "\r\n".join(headers) + "\r\n\r\n"
                upstream.sendall(rebuilt.encode("latin-1"))
                self._pump(self.connection, upstream)
                return

            record("UNKNOWN", "-", request_line)
            self.connection.sendall(b"HTTP/1.1 400 Bad Request\r\n\r\n")
        except Exception as exc:  # 代理只做观测，任何异常都不得影响被测进程
            record("ERROR", "-", f"{type(exc).__name__}: {exc}")

    def _pump(self, client: socket.socket, upstream: socket.socket) -> None:
        try:
            sockets = [client, upstream]
            while True:
                readable, _, _ = select.select(sockets, [], [], 60)
                if not readable:
                    return
                for src in readable:
                    dst = upstream if src is client else client
                    data = src.recv(65536)
                    if not data:
                        return
                    dst.sendall(data)
        finally:
            upstream.close()


class ProxyServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> int:
    global _log_path
    parser = argparse.ArgumentParser(description="P6 outbound capture proxy")
    parser.add_argument("--port", type=int, default=8899)
    parser.add_argument("--log", default="/tmp/wzf-outbound.log")
    args = parser.parse_args()
    _log_path = args.log
    with open(_log_path, "w", encoding="utf-8") as fh:
        fh.write("")
    print(f"capture proxy listening on 127.0.0.1:{args.port} → {_log_path}", flush=True)
    with ProxyServer(("127.0.0.1", args.port), ProxyHandler) as server:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
    hosts = set()
    with open(_log_path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) >= 3:
                hosts.add(parts[2])
    unexpected = hosts - WHITELIST - {"-"}
    print("captured hosts: " + ", ".join(sorted(hosts)), flush=True)
    if unexpected:
        print("UNEXPECTED HOSTS: " + ", ".join(sorted(unexpected)), flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
