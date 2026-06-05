#!/usr/bin/env python3
"""Run multiple kubectl port-forwards with fixed local ports."""

from __future__ import annotations

import argparse
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class ForwardSpec:
    key: str
    namespace: str
    service: str
    ports: tuple[str, ...]
    description: str


SPECS: dict[str, ForwardSpec] = {
    "postgres": ForwardSpec(
        key="postgres",
        namespace="dev",
        service="postgresql",
        ports=("58318:5432",),
        description="PostgreSQL tunnel",
    ),
    "argocd": ForwardSpec(
        key="argocd",
        namespace="argocd",
        service="argocd-server",
        ports=("8090:80", "9443:443"),
        description="Argo CD UI/API",
    ),
    "ingress": ForwardSpec(
        key="ingress",
        namespace="ingress-nginx",
        service="ingress-nginx-controller",
        ports=("8080:80", "8443:443"),
        description="Ingress NGINX",
    ),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Start fixed-port kubectl port-forwards for local development."
    )
    parser.add_argument(
        "targets",
        nargs="*",
        choices=sorted(SPECS.keys()),
        help="Which forwards to run (default: all).",
    )
    parser.add_argument("--context", help="Optional kubectl context.")
    parser.add_argument("--kubeconfig", help="Optional kubeconfig path.")
    parser.add_argument("--list", action="store_true", help="List available targets.")
    return parser.parse_args()


def stream_output(prefix: str, proc: subprocess.Popen[str]) -> None:
    assert proc.stdout is not None
    for line in proc.stdout:
        sys.stdout.write(f"[{prefix}] {line}")
    proc.stdout.close()


def build_cmd(spec: ForwardSpec, args: argparse.Namespace) -> list[str]:
    cmd = ["kubectl"]
    if args.context:
        cmd += ["--context", args.context]
    if args.kubeconfig:
        cmd += ["--kubeconfig", args.kubeconfig]
    cmd += [
        "-n",
        spec.namespace,
        "port-forward",
        f"svc/{spec.service}",
        *spec.ports,
        "--address",
        "127.0.0.1",
    ]
    return cmd


def terminate_all(processes: Iterable[subprocess.Popen[str]]) -> None:
    for proc in processes:
        if proc.poll() is None:
            proc.terminate()
    for proc in processes:
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


def main() -> int:
    args = parse_args()
    if args.list:
        for key in sorted(SPECS):
            spec = SPECS[key]
            ports = ", ".join(spec.ports)
            print(f"{key:8} {spec.namespace}/svc/{spec.service:<30} {ports}  {spec.description}")
        return 0

    selected = args.targets or sorted(SPECS.keys())
    specs = [SPECS[key] for key in selected]
    processes: list[subprocess.Popen[str]] = []
    threads: list[threading.Thread] = []

    try:
        for spec in specs:
            cmd = build_cmd(spec, args)
            print(f"Starting {spec.key}: {' '.join(cmd)}")
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            processes.append(proc)
            thread = threading.Thread(target=stream_output, args=(spec.key, proc), daemon=True)
            thread.start()
            threads.append(thread)

        print("\nPort-forwards are running. Press Ctrl+C to stop all.")
        while True:
            for proc, spec in zip(processes, specs):
                code = proc.poll()
                if code is not None:
                    print(f"\n{spec.key} exited with code {code}. Stopping all forwards.")
                    terminate_all(processes)
                    return code
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\nStopping port-forwards...")
        terminate_all(processes)
        return 0


if __name__ == "__main__":
    signal.signal(signal.SIGINT, signal.default_int_handler)
    raise SystemExit(main())
