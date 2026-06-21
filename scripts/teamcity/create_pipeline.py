#!/usr/bin/env python3
"""Create or repair ElectraHub TeamCity pipelines from any machine.

The script uses only Python's standard library so it can run from Windows,
macOS, Linux, TeamCity agents, or a fresh developer machine without Bash,
curl, or jq.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


def eprint(message: str) -> None:
    print(message, file=sys.stderr)


def stable_id(value: str) -> str:
    parts = re.findall(r"[A-Za-z0-9]+", value)
    return "".join(part[:1].upper() + part[1:] for part in parts) or "Service"


def require(value: Any, name: str) -> str:
    if value is None or str(value).strip() == "":
        raise SystemExit(f"{name} is required")
    return str(value).strip()


@dataclass(frozen=True)
class PipelineConfig:
    build_kind: str
    service_name: str
    parent_project_id: str
    project_id: str
    project_name: str
    build_type_name: str
    build_type_id: str
    vcs_root_id: str
    git_url: str
    git_branch: str
    docker_image: str
    dockerfile_path: str
    docker_context: str
    docker_platforms: str
    docker_use_buildx: bool
    pom_path: str
    maven_goals: str
    maven_runner_args: str
    app_dir: str
    npm_install_command: str
    build_command: str
    test_command: str
    k8s_branch: str
    deploy_version_file: str
    docker_username: str
    agent_name: str
    add_vcs_trigger: bool
    jmeter_image: str
    jmeter_plan: str
    regression_base_url: str
    regression_users: str
    regression_ramp_seconds: str
    regression_hold_seconds: str
    regression_sse_seconds: str
    regression_host_header: str
    regression_dynamic_connector_selection: str

    @staticmethod
    def from_json(raw: dict[str, Any]) -> "PipelineConfig":
        service_name = require(raw.get("serviceName"), "serviceName")
        service_id = stable_id(service_name)
        build_kind = str(raw.get("buildKind") or "maven").strip().lower()
        parent_project_id = str(raw.get("parentProjectId") or "Amy").strip()
        project_id = str(raw.get("projectId") or f"{parent_project_id}_{service_id}").strip()
        build_type_id = str(raw.get("buildTypeId") or f"{project_id}_Build").strip()
        git_branch = str(raw.get("gitBranch") or "develop").strip()
        vcs_root_id = str(raw.get("vcsRootId") or f"{project_id}_VcsRoot").strip()
        docker_image = str(raw.get("dockerImage") or "").strip()
        deploy_version_file = str(raw.get("deployVersionFile") or "").strip()
        if build_kind != "jmeter":
            docker_image = require(docker_image, "dockerImage")
            deploy_version_file = require(deploy_version_file, "deployVersionFile")

        return PipelineConfig(
            build_kind=build_kind,
            service_name=service_name,
            parent_project_id=parent_project_id,
            project_id=project_id,
            project_name=str(raw.get("teamcityProjectName") or raw.get("projectName") or service_name).strip(),
            build_type_name=str(raw.get("buildTypeName") or "Build").strip(),
            build_type_id=build_type_id,
            vcs_root_id=vcs_root_id,
            git_url=require(raw.get("gitUrl"), "gitUrl"),
            git_branch=git_branch,
            docker_image=docker_image,
            dockerfile_path=str(raw.get("dockerfilePath") or "Dockerfile").strip(),
            docker_context=str(raw.get("dockerContext") or ".").strip(),
            docker_platforms=str(raw.get("dockerPlatforms") or "linux/amd64,linux/arm64").strip(),
            docker_use_buildx=bool(raw.get("dockerUseBuildx", True)),
            pom_path=str(raw.get("pomPath") or "pom.xml").strip(),
            maven_goals=str(raw.get("mavenGoals") or "clean package").strip(),
            maven_runner_args=str(raw.get("mavenRunnerArgs") or "").strip(),
            app_dir=str(raw.get("appDir") or ".").strip(),
            npm_install_command=str(raw.get("npmInstallCommand") or "npm ci").strip(),
            build_command=str(raw.get("buildCommand") or "npm run build").strip(),
            test_command=str(raw.get("testCommand") or "").strip(),
            k8s_branch=str(raw.get("k8sBranch") or "develop").strip(),
            deploy_version_file=deploy_version_file,
            docker_username=str(raw.get("dockerUsername") or "amolsurjuse").strip(),
            agent_name=str(raw["agentName"] if "agentName" in raw else "teamcity-minimal-agent").strip(),
            add_vcs_trigger=bool(raw.get("addVcsTrigger", True)),
            jmeter_image=str(raw.get("jmeterImage") or "justb4/jmeter:latest").strip(),
            jmeter_plan=str(raw.get("jmeterPlan") or "scripts/jmeter/03-full-e2e-charging-100-users.jmx").strip(),
            regression_base_url=str(raw.get("regressionBaseUrl") or "https://api.dev.electrahub.net").strip(),
            regression_users=str(raw.get("regressionUsers") or "5").strip(),
            regression_ramp_seconds=str(raw.get("regressionRampSeconds") or "30").strip(),
            regression_hold_seconds=str(raw.get("regressionHoldSeconds") or "120").strip(),
            regression_sse_seconds=str(raw.get("regressionSseSeconds") or "60").strip(),
            regression_host_header=str(raw.get("regressionHostHeader") or "").strip(),
            regression_dynamic_connector_selection=str(raw.get("regressionDynamicConnectorSelection") or "true").strip().lower(),
        )


class TeamCityClient:
    def __init__(self, base_url: str, token: str, dry_run: bool = False) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.dry_run = dry_run

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def _headers(self) -> dict[str, str]:
        if self.token.startswith("basic:"):
            token = self.token.removeprefix("basic:")
            basic = base64.b64encode(f":{token}".encode("utf-8")).decode("ascii")
            return {"Authorization": f"Basic {basic}", "Accept": "application/json"}
        return {"Authorization": f"Bearer {self.token}", "Accept": "application/json"}

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        headers = self._headers()
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"

        if self.dry_run:
            if method.upper() == "GET":
                print(f"DRY-RUN GET {path}")
                if path.endswith("/agent-requirements"):
                    return {"count": 0}
                if "/buildTypes/id:" in path:
                    return {
                        "vcs-root-entries": {"vcs-root-entry": []},
                        "steps": {"count": 0},
                        "triggers": {"count": 0},
                    }
                return {}
            print(f"DRY-RUN {method} {path}")
            if body is not None:
                print(json.dumps(body, indent=2))
            return {}

        request = urllib.request.Request(self._url(path), data=data, method=method.upper(), headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {path} failed: {exc.reason}") from exc

    def exists(self, path: str) -> bool:
        if self.dry_run:
            print(f"DRY-RUN GET {path}")
            return False
        request = urllib.request.Request(self._url(path), method="GET", headers=self._headers())
        try:
            with urllib.request.urlopen(request, timeout=30):
                return True
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return False
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"GET {path} failed with HTTP {exc.code}: {detail}") from exc


def step_property(name: str, value: str) -> dict[str, str]:
    return {"name": name, "value": value}


def update_version_script(cfg: PipelineConfig) -> str:
    return f"""set -eu

test -n "%github.token%"

BRANCH="%k8s.branch%"
REPO="https://%github.user%:%github.token%@github.com/amolsurjuse/k8s-platform.git"
BUILD="%build.number%"

WORKDIR="$(pwd)/k8s"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"

git clone --branch "$BRANCH" --depth 1 "$REPO" "$WORKDIR"
cd "$WORKDIR"

FILE="{cfg.deploy_version_file}"

if [ ! -f "$FILE" ]; then
  echo "ERROR: $FILE not found"
  exit 1
fi

sed -i.bak "s/^\\([[:space:]]*tag:\\).*/\\1 \\"${{BUILD}}\\"/g" "$FILE"
rm -f "$FILE.bak"

git add "$FILE"
git config user.email "ci@teamcity"
git config user.name "teamcity-ci"
git commit -m "chore({cfg.service_name}): deploy dev image tag ${{BUILD}}" || {{
  echo "Nothing to commit"
  exit 0
}}

for attempt in 1 2 3; do
  if git push origin "$BRANCH"; then
    exit 0
  fi

  echo "Push failed, rebasing onto latest $BRANCH before retry $attempt..."
  git fetch origin "$BRANCH"
  git rebase "origin/$BRANCH"
done

echo "ERROR: failed to push deployment version update after retries"
exit 1
"""


def ensure_project(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if tc.exists(f"/app/rest/projects/id:{cfg.project_id}"):
        print(f"TeamCity project exists: {cfg.project_id}")
        return
    tc.request("POST", "/app/rest/projects", {
        "id": cfg.project_id,
        "name": cfg.project_name,
        "parentProject": {"id": cfg.parent_project_id},
    })
    print(f"Created project: {cfg.project_id}")


def ensure_parent_project(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if cfg.parent_project_id in ("_Root", "Root"):
        return
    if tc.exists(f"/app/rest/projects/id:{cfg.parent_project_id}"):
        print(f"TeamCity parent project exists: {cfg.parent_project_id}")
        return
    tc.request("POST", "/app/rest/projects", {
        "id": cfg.parent_project_id,
        "name": cfg.parent_project_id,
        "parentProject": {"id": "_Root"},
    })
    print(f"Created parent project: {cfg.parent_project_id}")


def ensure_vcs_root(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if tc.exists(f"/app/rest/vcs-roots/id:{cfg.vcs_root_id}"):
        print(f"VCS root exists: {cfg.vcs_root_id}")
        return
    tc.request("POST", "/app/rest/vcs-roots", {
        "id": cfg.vcs_root_id,
        "name": f"{cfg.git_url}#refs/heads/{cfg.git_branch}",
        "vcsName": "jetbrains.git",
        "project": {"id": cfg.parent_project_id},
        "properties": {"property": [
            step_property("agentCleanFilesPolicy", "ALL_UNTRACKED"),
            step_property("agentCleanPolicy", "ON_BRANCH_CHANGE"),
            step_property("authMethod", "PASSWORD"),
            step_property("branch", f"refs/heads/{cfg.git_branch}"),
            step_property("secure:password", "%github.token%"),
            step_property("submoduleCheckout", "CHECKOUT"),
            step_property("teamcity:branchSpec", "refs/heads/*"),
            step_property("url", cfg.git_url),
            step_property("useAlternates", "AUTO"),
            step_property("username", cfg.docker_username),
            step_property("usernameStyle", "USERID"),
        ]},
    })
    print(f"Created VCS root: {cfg.vcs_root_id}")


def ensure_build_type(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if tc.exists(f"/app/rest/buildTypes/id:{cfg.build_type_id}"):
        print(f"Build config exists: {cfg.build_type_id}")
        return
    tc.request("POST", f"/app/rest/projects/id:{cfg.project_id}/buildTypes", {
        "id": cfg.build_type_id,
        "name": cfg.build_type_name,
    })
    print(f"Created build config: {cfg.build_type_id}")


def attach_vcs_root(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    build_type = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}")
    entries = build_type.get("vcs-root-entries", {}).get("vcs-root-entry", [])
    if any(entry.get("id") == cfg.vcs_root_id for entry in entries):
        print("VCS root already attached")
        return
    tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/vcs-root-entries", {
        "id": cfg.vcs_root_id,
        "vcs-root": {"id": cfg.vcs_root_id},
        "checkout-rules": "",
    })
    print("Attached VCS root")


def set_parameter(tc: TeamCityClient, build_type_id: str, name: str, value: str) -> None:
    tc.request("PUT", f"/app/rest/buildTypes/id:{build_type_id}/parameters/{urllib.parse.quote(name)}", {
        "name": name,
        "value": value,
    })


def set_parameters(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    set_parameter(tc, cfg.build_type_id, "k8s.branch", cfg.k8s_branch)
    set_parameter(tc, cfg.build_type_id, "docker.username", cfg.docker_username)
    set_parameter(tc, cfg.build_type_id, "deployment.version.file", cfg.deploy_version_file)
    if cfg.build_kind == "jmeter":
        set_parameter(tc, cfg.build_type_id, "jmeter.image", cfg.jmeter_image)
        set_parameter(tc, cfg.build_type_id, "jmeter.plan", cfg.jmeter_plan)
        set_parameter(tc, cfg.build_type_id, "regression.base.url", cfg.regression_base_url)
        set_parameter(tc, cfg.build_type_id, "regression.users", cfg.regression_users)
        set_parameter(tc, cfg.build_type_id, "regression.ramp.seconds", cfg.regression_ramp_seconds)
        set_parameter(tc, cfg.build_type_id, "regression.hold.seconds", cfg.regression_hold_seconds)
        set_parameter(tc, cfg.build_type_id, "regression.sse.seconds", cfg.regression_sse_seconds)
        set_parameter(tc, cfg.build_type_id, "regression.host.header", cfg.regression_host_header)
        set_parameter(tc, cfg.build_type_id, "regression.dynamic.connector.selection", cfg.regression_dynamic_connector_selection)
    print("Set build parameters")


def ensure_agent_requirement(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if not cfg.agent_name:
        print("Skipped agent requirement")
        return
    requirements = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}/agent-requirements")
    if int(requirements.get("count", 0)) > 0:
        print("Agent requirement already configured")
        return
    tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/agent-requirements", {
        "type": "equals",
        "properties": {"property": [
            step_property("property-name", "system.agent.name"),
            step_property("property-value", cfg.agent_name),
        ]},
    })
    print("Created agent requirement")


def create_script_step(tc: TeamCityClient, cfg: PipelineConfig, name: str, script: str) -> None:
    tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/steps", {
        "name": name,
        "type": "simpleRunner",
        "properties": {"property": [
            step_property("script.content", script),
            step_property("teamcity.step.mode", "default"),
            step_property("use.custom.script", "true"),
        ]},
    })


def create_maven_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    pom_arg = "" if cfg.pom_path == "pom.xml" else f" -f {cfg.pom_path}"
    args = f" {cfg.maven_runner_args}" if cfg.maven_runner_args else ""
    script = f"mvn{pom_arg} {cfg.maven_goals}{args}"
    create_script_step(tc, cfg, "Maven Build", script)

def create_source_build_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if cfg.build_kind == "maven":
        create_maven_step(tc, cfg)
    elif cfg.build_kind == "go":
        create_script_step(tc, cfg, "Go Test", cfg.test_command or "go test ./...")
    elif cfg.build_kind == "node":
        script = f"""set -eu
cd "{cfg.app_dir}"
{cfg.npm_install_command}
{cfg.build_command}
"""
        create_script_step(tc, cfg, "Node Build", script)
    elif cfg.build_kind == "docker":
        print("Skipping source build step for docker-only pipeline")
    elif cfg.build_kind == "jmeter":
        print("Skipping source build step for JMeter regression pipeline")
    else:
        raise ValueError(f"Unsupported buildKind {cfg.build_kind!r}. Use maven, go, node, docker, or jmeter.")


def create_jmeter_regression_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    script = """set -eu

RESULT_DIR="outputs/jmeter/teamcity/results"
REPORT_DIR="outputs/jmeter/teamcity/report"
JTL="$RESULT_DIR/electrahub-regression.jtl"
LOG="$RESULT_DIR/jmeter.log"
DOCKER_CONFIG_DIR="$RESULT_DIR/docker-config"

rm -rf "$RESULT_DIR" "$REPORT_DIR"
mkdir -p "$RESULT_DIR" "$REPORT_DIR" "$DOCKER_CONFIG_DIR"
printf "{}\n" > "$DOCKER_CONFIG_DIR/config.json"

echo "Running ElectraHub regression against %regression.base.url%"
echo "Users=%regression.users% Ramp=%regression.ramp.seconds%s Hold=%regression.hold.seconds%s SSE=%regression.sse.seconds%s"

status=0
CID=""
cleanup() {
  if [ -n "$CID" ]; then
    DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker rm -f "$CID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker pull "%jmeter.image%"
CID="$(DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker create \
  -w /work \
  "%jmeter.image%" \
  -n \
  -t "%jmeter.plan%" \
  -l "$JTL" \
  -j "$LOG" \
  -e \
  -o "$REPORT_DIR" \
  -Jbase_url="%regression.base.url%" \
  -Jusers="%regression.users%" \
  -Jramp_seconds="%regression.ramp.seconds%" \
  -Jhold_seconds="%regression.hold.seconds%" \
  -Jsse_seconds="%regression.sse.seconds%" \
  -Jrequest_host_header="%regression.host.header%" \
  -Jdynamic_connector_selection="%regression.dynamic.connector.selection%" \
  -Jrun_id="tc-%build.number%" \
  -Jjmeter.save.saveservice.output_format=csv \
  -Jjmeter.save.saveservice.print_field_names=true \
  -Jjmeter.save.saveservice.response_data.on_error=true)"

tar --exclude=.git --exclude=outputs -cf - . | DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp - "$CID":/work
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker start -a "$CID" || status=$?
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp "$CID":/work/outputs/jmeter/teamcity outputs/jmeter/ || true

echo "##teamcity[publishArtifacts '$RESULT_DIR/** => jmeter-results']"
echo "##teamcity[publishArtifacts '$REPORT_DIR/** => jmeter-report']"

if [ "$status" -ne 0 ]; then
  echo "JMeter exited with status $status"
  exit "$status"
fi

if awk -F, 'NR==1 { for (i=1; i<=NF; i++) if ($i == "success") success=i; next } success && $success == "false" { found=1 } END { exit found ? 0 : 1 }' "$JTL"; then
  echo "JMeter assertions failed. See jmeter-results/electrahub-regression.jtl and jmeter-report/index.html."
  awk -F, 'NR==1 { for (i=1; i<=NF; i++) { if ($i == "label") label=i; if ($i == "responseMessage") msg=i; if ($i == "success") success=i } next } success && $success == "false" { print "FAILED: " $label " - " $msg }' "$JTL" | head -20 || true
  exit 1
fi

echo "ElectraHub regression completed successfully."
"""
    create_script_step(tc, cfg, "JMeter Charging Regression", script)


def clear_steps(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    response = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}/steps")
    steps = response.get("step", [])
    if not steps and int(response.get("count", 0)) > 0:
        raise RuntimeError(f"TeamCity reported {response.get('count')} step(s), but returned no step ids to delete")
    for step in steps:
        step_id = step.get("id")
        if step_id:
            tc.request("DELETE", f"/app/rest/buildTypes/id:{cfg.build_type_id}/steps/{urllib.parse.quote(step_id)}")
            print(f"Deleted build step: {step_id}")


def create_steps_if_empty(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    build_type = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}")
    if int(build_type.get("steps", {}).get("count", 0)) > 0:
        if cfg.build_kind == "jmeter":
            clear_steps(tc, cfg)
            create_jmeter_regression_step(tc, cfg)
            print("Replaced JMeter regression step")
            return
        print("Build steps already configured")
        return

    if cfg.build_kind == "jmeter":
        create_jmeter_regression_step(tc, cfg)
        print("Created JMeter regression step")
        return

    create_source_build_step(tc, cfg)

    dockerfile_path = cfg.dockerfile_path
    docker_context = cfg.docker_context
    if cfg.build_kind == "node" and cfg.app_dir != ".":
        app_dir = cfg.app_dir.rstrip("/")
        dockerfile_path = f"{app_dir}/{cfg.dockerfile_path}"
        docker_context = app_dir

    if cfg.docker_use_buildx:
        create_script_step(tc, cfg, "Docker Buildx Push", f"""set -eu

test -n "%docker.username%"
test -n "%docker.password%"

echo "%docker.password%" | docker login -u "%docker.username%" --password-stdin
docker buildx create --use --name "tc-%build.number%" || docker buildx use "tc-%build.number%"
docker buildx build \\
  --platform "{cfg.docker_platforms}" \\
  --file "{dockerfile_path}" \\
  --tag "{cfg.docker_image}:%build.number%" \\
  --push \\
  "{docker_context}"
docker buildx imagetools inspect "{cfg.docker_image}:%build.number%"
""")
    else:
        tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/steps", {
            "name": "Docker Build",
            "type": "DockerCommand",
            "properties": {"property": [
                step_property("docker.command.type", "build"),
                step_property("docker.image.namesAndTags", f"{cfg.docker_image}:%build.number%"),
                step_property("docker.push.remove.image", "true"),
                step_property("dockerfile.path", dockerfile_path),
                step_property("dockerfile.source", "PATH"),
                step_property("docker.context.folder", docker_context),
                step_property("teamcity.step.mode", "default"),
            ]},
        })
        tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/steps", {
            "name": "Docker Push",
            "type": "DockerCommand",
            "properties": {"property": [
                step_property("docker.command.type", "push"),
                step_property("docker.image.namesAndTags", f"{cfg.docker_image}:%build.number%"),
                step_property("docker.push.remove.image", "true"),
                step_property("dockerfile.source", "PATH"),
                step_property("teamcity.step.mode", "default"),
            ]},
        })
    create_script_step(tc, cfg, "Update build version", update_version_script(cfg))
    print("Created build steps")


def ensure_trigger(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if not cfg.add_vcs_trigger:
        print("Skipped VCS trigger")
        return
    build_type = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}")
    if int(build_type.get("triggers", {}).get("count", 0)) > 0:
        print("VCS trigger already configured")
        return
    tc.request("POST", f"/app/rest/buildTypes/id:{cfg.build_type_id}/triggers", {
        "type": "vcsTrigger",
        "properties": {"property": [
            step_property("branchFilter", "+:<default>"),
            step_property("enableQueueOptimization", "true"),
            step_property("quietPeriodMode", "DO_NOT_USE"),
        ]},
    })
    print("Created VCS trigger")


def create_pipeline(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    ensure_parent_project(tc, cfg)
    ensure_project(tc, cfg)
    ensure_vcs_root(tc, cfg)
    ensure_build_type(tc, cfg)
    attach_vcs_root(tc, cfg)
    set_parameters(tc, cfg)
    ensure_agent_requirement(tc, cfg)
    create_steps_if_empty(tc, cfg)
    ensure_trigger(tc, cfg)
    print(f"Pipeline is ready: {tc.base_url}/buildConfiguration/{cfg.build_type_id}?mode=builds")


def load_configs(path: str) -> list[PipelineConfig]:
    with open(path, "r", encoding="utf-8") as handle:
        raw = json.load(handle)
    if isinstance(raw, list):
        items = raw
    elif isinstance(raw, dict) and isinstance(raw.get("pipelines"), list):
        items = raw["pipelines"]
    elif isinstance(raw, dict):
        items = [raw]
    else:
        raise SystemExit("Config must be an object, an array, or an object with a pipelines array")
    return [PipelineConfig.from_json(item) for item in items]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create ElectraHub TeamCity service pipelines.")
    parser.add_argument("--config", required=True, help="Path to pipeline JSON config.")
    parser.add_argument("--service", action="append", default=[], help="Only create matching serviceName. Can be repeated.")
    parser.add_argument("--teamcity-url", default=os.getenv("TEAMCITY_URL", "http://localhost:8111"))
    parser.add_argument("--token", default=os.getenv("TEAMCITY_TOKEN", ""))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--continue-on-error", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    token = str(args.token or "").strip()
    if not token and sys.stdin.isatty():
        token = getpass.getpass("TeamCity access token: ").strip()
    token = require(token, "TEAMCITY_TOKEN or --token")
    configs = load_configs(args.config)
    wanted = {service.lower() for service in args.service}
    if wanted:
        configs = [cfg for cfg in configs if cfg.service_name.lower() in wanted]
    if not configs:
        raise SystemExit("No pipelines matched the requested service filter")

    tc = TeamCityClient(args.teamcity_url, token, args.dry_run)
    failures = 0
    for index, cfg in enumerate(configs, start=1):
        print(f"\n[{index}/{len(configs)}] {cfg.service_name}")
        try:
            create_pipeline(tc, cfg)
        except Exception as exc:
            failures += 1
            eprint(f"ERROR [{cfg.service_name}]: {exc}")
            if not args.continue_on_error:
                return 1
    if failures:
        eprint(f"Completed with {failures} failed pipeline(s)")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
