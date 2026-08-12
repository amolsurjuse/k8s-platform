#!/usr/bin/env python3
"""Provision and control disposable OCPP capacity-test sockets.

Secrets are read only from the environment and are never printed. The tool is
intentionally limited to the LOAD-STAGE2- namespace so it cannot mutate real
charger credentials or connector state.
"""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request

PREFIX = "LOAD-STAGE2-"


def charger_id(index: int) -> str:
    if index < 1:
        raise ValueError("index must be positive")
    return f"{PREFIX}{index:05d}"


def password_for(identifier: str, key: str) -> str:
    digest = hmac.new(key.encode(), identifier.encode(), hashlib.sha256).digest()
    return base64.urlsafe_b64encode(digest).decode().rstrip("=")


def request_json(url: str, method: str, payload: dict, headers: dict) -> int:
    body = json.dumps(payload, separators=(",", ":")).encode()
    request = urllib.request.Request(url, data=body, method=method)
    request.add_header("Content-Type", "application/json")
    for name, value in headers.items():
        request.add_header(name, value)
    with urllib.request.urlopen(request, timeout=45) as response:
        response.read()
        return response.status


def execute(args: argparse.Namespace, index: int, key: str, token: str) -> int:
    identifier = charger_id(index)
    if args.action == "provision":
        return request_json(
            f"{args.ocpp_url}/api/v1/ocpp/internal/charger-credentials/{identifier}",
            "PUT",
            {
                "password": password_for(identifier, key),
                "validUntil": None,
                "rotationOverlapSeconds": 0,
            },
            {
                "X-ElectraHub-Internal-Token": token,
                "X-ElectraHub-Actor": "stage2-capacity-gate",
            },
        )
    if args.action == "disable":
        return request_json(
            f"{args.ocpp_url}/api/v1/ocpp/internal/charger-credentials/{identifier}/disable",
            "POST",
            {},
            {
                "X-ElectraHub-Internal-Token": token,
                "X-ElectraHub-Actor": "stage2-capacity-gate",
            },
        )
    if args.action == "connect":
        return request_json(
            f"{args.connector_url}/connect",
            "POST",
            {
                "chargerId": identifier,
                "url": f"{args.ws_url}/{identifier}",
                "ocppVersion": "OCPP16J",
                "skipVerify": False,
                "boot": {"vendor": "ElectraHubCapacityGate", "model": "Stage2"},
            },
            {},
        )
    if args.action == "disconnect":
        return request_json(
            f"{args.connector_url}/disconnect",
            "POST",
            {"chargerId": identifier, "reason": "capacity_gate_cleanup"},
            {},
        )
    raise ValueError(f"unsupported action: {args.action}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("provision", "connect", "disconnect", "disable"))
    parser.add_argument("--start", type=int, default=1)
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--workers", type=int, default=16)
    parser.add_argument("--ocpp-url", default="http://127.0.0.1:18082")
    parser.add_argument("--connector-url", default="http://127.0.0.1:18091")
    parser.add_argument("--ws-url", default="wss://api.electrahub.net/ws/ocpp")
    args = parser.parse_args()
    if args.start < 1 or args.count < 1 or not 1 <= args.workers <= 64:
        parser.error("start/count must be positive and workers must be 1..64")

    key = os.environ.get("OCPP_CAPACITY_DERIVATION_KEY", "")
    token = os.environ.get("OCPP_CAPACITY_INTERNAL_TOKEN", "")
    if args.action in ("provision", "connect") and not key:
        parser.error("OCPP_CAPACITY_DERIVATION_KEY is required")
    if args.action in ("provision", "disable") and not token:
        parser.error("OCPP_CAPACITY_INTERNAL_TOKEN is required")

    started = time.monotonic()
    completed = 0
    failures: list[str] = []
    indices = range(args.start, args.start + args.count)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(execute, args, i, key, token): i for i in indices}
        for future in concurrent.futures.as_completed(futures):
            index = futures[future]
            try:
                status = future.result()
                if status < 200 or status >= 300:
                    failures.append(f"{charger_id(index)} status={status}")
            except (OSError, urllib.error.HTTPError, ValueError) as error:
                failures.append(f"{charger_id(index)} {type(error).__name__}: {error}")
            completed += 1
            if completed % 500 == 0 or completed == args.count:
                print(f"{args.action}: {completed}/{args.count}", flush=True)

    elapsed = time.monotonic() - started
    print(f"{args.action}: completed={completed} failures={len(failures)} elapsed={elapsed:.1f}s")
    for failure in failures[:20]:
        print(f"failure: {failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
