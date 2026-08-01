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


def string_tuple(value: Any, name: str) -> tuple[str, ...]:
    if value is None:
        return ()
    if not isinstance(value, list):
        raise SystemExit(f"{name} must be a JSON array of strings")
    normalized = tuple(str(item).strip() for item in value)
    if any(not item for item in normalized):
        raise SystemExit(f"{name} must not contain blank entries")
    if len(set(normalized)) != len(normalized):
        raise SystemExit(f"{name} must not contain duplicate entries")
    return normalized


def string_map(value: Any, name: str) -> dict[str, str]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise SystemExit(f"{name} must be a JSON object with string values")
    normalized = {str(key).strip(): str(item).strip() for key, item in value.items()}
    if any(not key for key in normalized):
        raise SystemExit(f"{name} must not contain blank parameter names")
    return normalized


def trusted_shell_value(value: str, name: str) -> str:
    if re.search(r'["`$\\\r\n]', value):
        raise SystemExit(f"{name} contains characters that are unsafe for credential-bearing shell steps")
    return value


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
    git_branch_spec: str
    docker_image: str
    dockerfile_path: str
    docker_context: str
    docker_platforms: str
    docker_use_buildx: bool
    pom_path: str
    maven_goals: str
    maven_runner_args: str
    copy_build_artifacts: bool
    app_dir: str
    npm_install_command: str
    build_command: str
    node_image: str
    flutter_image: str
    test_command: str
    k8s_branch: str
    deploy_version_file: str
    docker_username: str
    agent_name: str
    add_vcs_trigger: bool
    finish_build_trigger_source_id: str
    jmeter_image: str
    jmeter_plan: str
    regression_base_url: str
    regression_users: str
    regression_ramp_seconds: str
    regression_hold_seconds: str
    regression_sse_seconds: str
    regression_host_header: str
    regression_dynamic_connector_selection: str
    regression_connector_start_attempts: str
    regression_request_timeout_ms: str
    regression_session_command_timeout_ms: str
    jmeter_load_stages: str
    jmeter_load_max_error_percent: str
    jmeter_step_name: str
    jmeter_response_data_on_error: bool
    jmeter_environment_variables: tuple[str, ...]
    teamcity_parameters: dict[str, str]
    teamcity_secure_parameters: tuple[str, ...]
    jmeter_credential_safe_mode: bool
    jmeter_required_summary_providers: tuple[str, ...]

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
        if build_kind not in ("jmeter", "flutter"):
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
            git_branch_spec=str(raw.get("gitBranchSpec", "refs/heads/*")).strip(),
            docker_image=docker_image,
            dockerfile_path=str(raw.get("dockerfilePath") or "Dockerfile").strip(),
            docker_context=str(raw.get("dockerContext") or ".").strip(),
            docker_platforms=str(raw.get("dockerPlatforms") or "linux/amd64,linux/arm64").strip(),
            docker_use_buildx=bool(raw.get("dockerUseBuildx", True)),
            pom_path=str(raw.get("pomPath") or "pom.xml").strip(),
            maven_goals=str(raw.get("mavenGoals") or "clean package").strip(),
            maven_runner_args=str(raw.get("mavenRunnerArgs") or "").strip(),
            copy_build_artifacts=bool(raw.get("copyBuildArtifacts", False)),
            app_dir=str(raw.get("appDir") or ".").strip(),
            npm_install_command=str(raw.get("npmInstallCommand") or "npm ci").strip(),
            build_command=str(raw.get("buildCommand") or "npm run build").strip(),
            node_image=str(raw.get("nodeImage") or "node:22-bookworm").strip(),
            flutter_image=str(raw.get("flutterImage") or "ghcr.io/cirruslabs/flutter:3.44.0").strip(),
            test_command=str(raw.get("testCommand") or "").strip(),
            k8s_branch=str(raw.get("k8sBranch") or "develop").strip(),
            deploy_version_file=deploy_version_file,
            docker_username=str(raw.get("dockerUsername") or "amolsurjuse").strip(),
            agent_name=str(raw["agentName"] if "agentName" in raw else "teamcity-minimal-agent").strip(),
            add_vcs_trigger=bool(raw.get("addVcsTrigger", True)),
            finish_build_trigger_source_id=str(raw.get("finishBuildTriggerSourceId") or "").strip(),
            jmeter_image=str(raw.get("jmeterImage") or "amolsurjuse/electrahub-jmeter:5.6.3-java17").strip(),
            jmeter_plan=str(raw.get("jmeterPlan") or "scripts/jmeter/03-full-e2e-charging-100-users.jmx").strip(),
            regression_base_url=str(raw.get("regressionBaseUrl") or "https://api.dev.electrahub.net").strip(),
            regression_users=str(raw.get("regressionUsers") or "5").strip(),
            regression_ramp_seconds=str(raw.get("regressionRampSeconds") or "30").strip(),
            regression_hold_seconds=str(raw.get("regressionHoldSeconds") or "120").strip(),
            regression_sse_seconds=str(raw.get("regressionSseSeconds") or "60").strip(),
            regression_host_header=str(raw.get("regressionHostHeader") or "").strip(),
            regression_dynamic_connector_selection=str(raw.get("regressionDynamicConnectorSelection") or "true").strip().lower(),
            regression_connector_start_attempts=str(raw.get("regressionConnectorStartAttempts") or "12").strip(),
            regression_request_timeout_ms=str(raw.get("regressionRequestTimeoutMs") or "120000").strip(),
            regression_session_command_timeout_ms=str(raw.get("regressionSessionCommandTimeoutMs") or "180000").strip(),
            jmeter_load_stages=str(raw.get("jmeterLoadStages") or "").strip(),
            jmeter_load_max_error_percent=str(raw.get("jmeterLoadMaxErrorPercent") or "5").strip(),
            jmeter_step_name=str(raw.get("jmeterStepName") or "JMeter Charging Regression").strip(),
            jmeter_response_data_on_error=bool(raw.get("jmeterResponseDataOnError", True)),
            jmeter_environment_variables=string_tuple(
                raw.get("jmeterEnvironmentVariables"), "jmeterEnvironmentVariables"
            ),
            teamcity_parameters=string_map(raw.get("teamcityParameters"), "teamcityParameters"),
            teamcity_secure_parameters=string_tuple(
                raw.get("teamcitySecureParameters"), "teamcitySecureParameters"
            ),
            jmeter_credential_safe_mode=bool(raw.get("jmeterCredentialSafeMode", False)),
            jmeter_required_summary_providers=string_tuple(
                raw.get("jmeterRequiredSummaryProviders"), "jmeterRequiredSummaryProviders"
            ),
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

    def request(self, method: str, path: str, body: dict[str, Any] | str | None = None) -> Any:
        headers = self._headers()
        data = None
        if body is not None:
            if isinstance(body, str):
                data = body.encode("utf-8")
                headers["Content-Type"] = "text/plain"
            else:
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
                print(body if isinstance(body, str) else json.dumps(body, indent=2))
            return {}

        request = urllib.request.Request(self._url(path), data=data, method=method.upper(), headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = response.read().decode("utf-8")
                if not payload:
                    return {}
                try:
                    return json.loads(payload)
                except json.JSONDecodeError:
                    return payload
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {path} failed: {exc.reason}") from exc

    def request_text(self, method: str, path: str, body: str) -> str:
        """Update TeamCity primitive REST properties that require text/plain.

        Most TeamCity endpoints accept JSON, but VCS root names and individual
        property values are primitive resources.  TeamCity rejects those calls
        when the request advertises application/json in Accept.
        """
        if self.dry_run:
            print(f"DRY-RUN {method} {path}")
            print(body)
            return ""

        headers = self._headers()
        headers["Accept"] = "text/plain"
        headers["Content-Type"] = "text/plain"
        request = urllib.request.Request(
            self._url(path),
            data=body.encode("utf-8"),
            method=method.upper(),
            headers=headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
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
        locator = f"id:{cfg.vcs_root_id}"
        encoded_locator = urllib.parse.quote(locator, safe=":")
        tc.request_text(
            "PUT",
            f"/app/rest/vcs-roots/{encoded_locator}/name",
            f"{cfg.git_url}#refs/heads/{cfg.git_branch}",
        )
        for name, value in (
            ("branch", f"refs/heads/{cfg.git_branch}"),
            ("url", cfg.git_url),
        ):
            encoded_name = urllib.parse.quote(name, safe="")
            tc.request_text(
                "PUT",
                f"/app/rest/vcs-roots/{encoded_locator}/properties/{encoded_name}",
                value,
            )
        branch_spec_name = urllib.parse.quote("teamcity:branchSpec", safe="")
        branch_spec_path = f"/app/rest/vcs-roots/{encoded_locator}/properties/{branch_spec_name}"
        if cfg.git_branch_spec:
            tc.request_text("PUT", branch_spec_path, cfg.git_branch_spec)
        elif tc.exists(branch_spec_path):
            tc.request("DELETE", branch_spec_path)
        print(f"Updated VCS root: {cfg.vcs_root_id}")
        return
    vcs_properties = [
        step_property("agentCleanFilesPolicy", "ALL_UNTRACKED"),
        step_property("agentCleanPolicy", "ON_BRANCH_CHANGE"),
        step_property("authMethod", "PASSWORD"),
        step_property("branch", f"refs/heads/{cfg.git_branch}"),
        step_property("secure:password", "%github.token%"),
        step_property("submoduleCheckout", "CHECKOUT"),
        step_property("url", cfg.git_url),
        step_property("useAlternates", "AUTO"),
        step_property("username", cfg.docker_username),
        step_property("usernameStyle", "USERID"),
    ]
    if cfg.git_branch_spec:
        vcs_properties.append(step_property("teamcity:branchSpec", cfg.git_branch_spec))
    tc.request("POST", "/app/rest/vcs-roots", {
        "id": cfg.vcs_root_id,
        "name": f"{cfg.git_url}#refs/heads/{cfg.git_branch}",
        "vcsName": "jetbrains.git",
        "project": {"id": cfg.parent_project_id},
        "properties": {"property": vcs_properties},
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
    tc.request("PUT", f"/app/rest/buildTypes/id:{build_type_id}/parameters/{urllib.parse.quote(name, safe='')}", {
        "name": name,
        "value": value,
    })


def ensure_secure_parameter(tc: TeamCityClient, build_type_id: str, name: str) -> None:
    path = f"/app/rest/buildTypes/id:{build_type_id}/parameters/{urllib.parse.quote(name, safe='')}"
    if tc.exists(path):
        parameter = tc.request("GET", path)
        raw_type = str(parameter.get("type", {}).get("rawValue", "")).strip().lower()
        if not raw_type.startswith("password"):
            raise RuntimeError(
                f"TeamCity parameter {name!r} already exists but is not Password typed; "
                "convert it in TeamCity before reconciling this pipeline"
            )
        print(f"Secure parameter already configured: {name}")
        return
    tc.request("PUT", path, {
        "name": name,
        "value": "SET_IN_TEAMCITY",
        "type": {"rawValue": "password"},
    })
    print(f"Created Password-typed parameter placeholder: {name}")


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
        set_parameter(tc, cfg.build_type_id, "regression.connector.start.attempts", cfg.regression_connector_start_attempts)
        set_parameter(tc, cfg.build_type_id, "regression.request.timeout.ms", cfg.regression_request_timeout_ms)
        set_parameter(tc, cfg.build_type_id, "regression.session.command.timeout.ms", cfg.regression_session_command_timeout_ms)
        set_parameter(tc, cfg.build_type_id, "jmeter.load.stages", cfg.jmeter_load_stages)
        set_parameter(tc, cfg.build_type_id, "jmeter.load.max.error.percent", cfg.jmeter_load_max_error_percent)
    overlap = set(cfg.teamcity_parameters).intersection(cfg.teamcity_secure_parameters)
    if overlap:
        raise RuntimeError(f"TeamCity parameters cannot be both plain and secure: {', '.join(sorted(overlap))}")
    for name, value in sorted(cfg.teamcity_parameters.items()):
        set_parameter(tc, cfg.build_type_id, name, value)
    for name in sorted(cfg.teamcity_secure_parameters):
        ensure_secure_parameter(tc, cfg.build_type_id, name)
    print("Set build parameters")


def agent_requirement_properties(requirement: dict[str, Any]) -> dict[str, str]:
    properties = requirement.get("properties", {}).get("property", [])
    return {str(prop.get("name")): str(prop.get("value", "")) for prop in properties}


def agent_requirement_matches_agent_name(requirement: dict[str, Any], agent_name: str) -> bool:
    props = agent_requirement_properties(requirement)
    return (
        requirement.get("type") == "equals"
        and props.get("property-name") == "system.agent.name"
        and props.get("property-value") == agent_name
    )


def clear_agent_requirements(tc: TeamCityClient, cfg: PipelineConfig, requirements: dict[str, Any]) -> None:
    agent_requirements = requirements.get("agent-requirement", [])
    if not agent_requirements and int(requirements.get("count", 0)) > 0:
        raise RuntimeError(
            f"TeamCity reported {requirements.get('count')} agent requirement(s), but returned no ids to delete"
        )
    for requirement in agent_requirements:
        requirement_id = requirement.get("id")
        if requirement_id:
            tc.request(
                "DELETE",
                f"/app/rest/buildTypes/id:{cfg.build_type_id}/agent-requirements/{urllib.parse.quote(requirement_id)}",
            )
            print(f"Deleted agent requirement: {requirement_id}")


def ensure_agent_requirement(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    requirements = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}/agent-requirements")
    if not cfg.agent_name:
        if int(requirements.get("count", 0)) > 0:
            clear_agent_requirements(tc, cfg, requirements)
            print("Removed agent requirement")
        else:
            print("Skipped agent requirement")
        return
    agent_requirements = requirements.get("agent-requirement", [])
    if len(agent_requirements) == 1 and agent_requirement_matches_agent_name(agent_requirements[0], cfg.agent_name):
        print("Agent requirement already configured")
        return
    if int(requirements.get("count", 0)) > 0:
        clear_agent_requirements(tc, cfg, requirements)
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
    command = sh_single_quote(f"""mkdir -p /workspace
tar -xf - -C /workspace
cd /workspace
mvn --version
mvn{pom_arg} {cfg.maven_goals}{args}""")
    script = f"""set -eu

# TeamCity agents only need Docker. Keep the Maven repository cache per agent so
# builds are deterministic without requiring Maven to be installed on the agent.
MAVEN_CACHE_VOLUME="electrahub-maven-cache"
docker volume create "$MAVEN_CACHE_VOLUME" >/dev/null
tar --exclude=.git --exclude=target -cf - . | docker run --rm -i \\
  --entrypoint sh \\
  -v "$MAVEN_CACHE_VOLUME:/root/.m2" \\
  "maven:3.9.9-eclipse-temurin-21" \\
  -lc {command}
"""
    if cfg.copy_build_artifacts:
        script = f"""set -eu

MAVEN_CACHE_VOLUME="electrahub-maven-cache"
docker volume create "$MAVEN_CACHE_VOLUME" >/dev/null
CID="$(docker create -i --entrypoint sh \\
  -v "$MAVEN_CACHE_VOLUME:/root/.m2" \\
  "maven:3.9.9-eclipse-temurin-21" \\
  -lc {command})"
cleanup() {{ docker rm -f "$CID" >/dev/null 2>&1 || true; }}
trap cleanup EXIT

tar --exclude=.git --exclude=target -cf - . | docker start -a -i "$CID"
rm -rf target
docker cp "$CID":/workspace/target ./target
test -d target
"""
    create_script_step(tc, cfg, "Maven Build", script)

def sh_single_quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def create_source_build_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if cfg.build_kind == "maven":
        create_maven_step(tc, cfg)
    elif cfg.build_kind == "go":
        create_script_step(tc, cfg, "Go Test", cfg.test_command or "go test ./...")
    elif cfg.build_kind == "node":
        node_commands = sh_single_quote(f"""mkdir -p /workspace
tar -xf - -C /workspace
cd /workspace
node --version
npm --version
{cfg.npm_install_command}
{cfg.build_command}""")
        script = f"""set -eu
cd "{cfg.app_dir}"
tar --exclude=.git --exclude=node_modules --exclude=dist -cf - . | docker run --rm -i "{cfg.node_image}" sh -lc {node_commands}
"""
        create_script_step(tc, cfg, "Node Build", script)
    elif cfg.build_kind == "flutter":
        flutter_commands = sh_single_quote("""set -eu
mkdir -p /workspace
tar -xf - -C /workspace
cd /workspace
flutter --version
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze --no-fatal-infos
flutter test --coverage --dart-define=USE_REAL_API=false
flutter build apk --debug --dart-define=USE_REAL_API=true --dart-define=GATEWAY_BASE_URL=https://api.pulsevote-electrahub.net
""")
        script = f"""set -eu
CID="$(docker create -i "{cfg.flutter_image}" sh -lc {flutter_commands})"
cleanup() {{ docker rm -f "$CID" >/dev/null 2>&1 || true; }}
trap cleanup EXIT

tar --exclude=.git --exclude=build --exclude=.dart_tool -cf - . | docker start -a -i "$CID"
mkdir -p build/app/outputs/flutter-apk
docker cp "$CID":/workspace/build/app/outputs/flutter-apk/app-debug.apk build/app/outputs/flutter-apk/app-debug.apk
test -s build/app/outputs/flutter-apk/app-debug.apk
echo "##teamcity[publishArtifacts 'build/app/outputs/flutter-apk/app-debug.apk => pulsevote-android']"
"""
        create_script_step(tc, cfg, "Flutter Quality and APK", script)
    elif cfg.build_kind == "docker":
        print("Skipping source build step for docker-only pipeline")
    elif cfg.build_kind == "jmeter":
        print("Skipping source build step for JMeter regression pipeline")
    else:
        raise ValueError(f"Unsupported buildKind {cfg.build_kind!r}. Use maven, go, node, flutter, docker, or jmeter.")


def create_jmeter_regression_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    if cfg.jmeter_load_stages:
        if (
            cfg.jmeter_environment_variables
            or cfg.jmeter_credential_safe_mode
            or cfg.jmeter_required_summary_providers
            or not cfg.jmeter_response_data_on_error
        ):
            raise RuntimeError(
                "Credential-safe JMeter settings are not supported by the load-ladder runner; "
                "use a separate non-load build configuration"
            )
        create_jmeter_load_ladder_step(tc, cfg)
        return

    invalid_environment_names = [
        name for name in cfg.jmeter_environment_variables
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name)
    ]
    if invalid_environment_names:
        raise RuntimeError(
            "jmeterEnvironmentVariables contains invalid environment names: "
            + ", ".join(invalid_environment_names)
        )
    docker_environment = "".join(
        f"  -e {name} \\\n" for name in cfg.jmeter_environment_variables
    )
    response_data_on_error = "true" if cfg.jmeter_response_data_on_error else "false"
    required_summary_gate = ""
    if cfg.jmeter_required_summary_providers:
        invalid_provider_names = [
            provider for provider in cfg.jmeter_required_summary_providers
            if not re.fullmatch(r"[A-Z][A-Z0-9_]*", provider)
        ]
        if invalid_provider_names:
            raise RuntimeError(
                "jmeterRequiredSummaryProviders contains invalid provider identifiers: "
                + ", ".join(invalid_provider_names)
            )
        required_providers = ",".join(cfg.jmeter_required_summary_providers)
        required_summary_gate = f"""
if ! python3 -c '
import json
import sys

path = sys.argv[1]
expected = set(sys.argv[2].split(","))
with open(path, encoding="utf-8") as handle:
    summary = json.load(handle)
providers = summary.get("providers", [])
actual = {{item.get("provider") for item in providers}}
passed = {{item.get("provider") for item in providers if item.get("outcome") == "PASSED"}}
if (
    len(providers) != len(expected)
    or actual != expected
    or passed != expected
    or summary.get("configurationStable") is not True
):
    print("Payment gateway provider summary is incomplete or failed", file=sys.stderr)
    raise SystemExit(1)
' "$RESULT_DIR/payment-gateway-provider-summary.json" "{required_providers}"; then
  echo "The payment gateway summary did not contain five successful, configuration-stable provider results."
  exit 1
fi
"""

    if cfg.jmeter_credential_safe_mode:
        if cfg.git_branch_spec:
            raise RuntimeError("Credential-bearing JMeter builds must disable feature branches")
        if not cfg.agent_name:
            raise RuntimeError("Credential-bearing JMeter builds must pin a trusted TeamCity agent")
        if "@sha256:" not in cfg.jmeter_image:
            raise RuntimeError("Credential-bearing JMeter builds must pin jmeterImage by sha256 digest")
        if not cfg.jmeter_environment_variables:
            raise RuntimeError("Credential-bearing JMeter builds must declare their Docker environment variables")
        if cfg.jmeter_response_data_on_error:
            raise RuntimeError("Credential-bearing JMeter builds must disable response data on error")
        expected_secure_parameters = {
            f"env.{name}" for name in cfg.jmeter_environment_variables
        }
        missing_secure_parameters = expected_secure_parameters.difference(cfg.teamcity_secure_parameters)
        if missing_secure_parameters:
            raise RuntimeError(
                "Credential-bearing JMeter environment variables must be backed by Password-typed "
                "TeamCity parameters: " + ", ".join(sorted(missing_secure_parameters))
            )

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
echo "DynamicConnectorSelection=%regression.dynamic.connector.selection% ConnectorStartAttempts=%regression.connector.start.attempts%"
echo "JMeter image=%jmeter.image%"

status=0
CID=""
RUN_ID="tc-$(date +%%s)-$$"
cleanup() {
  if [ -n "$CID" ]; then
    DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker rm -f "$CID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker pull "%jmeter.image%"
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker run --rm --entrypoint java "%jmeter.image%" -version
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker run --rm "%jmeter.image%" --version | head -25
CID="$(DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker create \
  -w /work \
  -e JVM_ARGS="-Dhttps.protocols=TLSv1.3,TLSv1.2 -Djdk.tls.client.protocols=TLSv1.3,TLSv1.2 -Dsun.net.http.allowRestrictedHeaders=true" \
__JMETER_DOCKER_ENVIRONMENT__  "%jmeter.image%" \
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
  -Jconnector_start_attempts="%regression.connector.start.attempts%" \
  -Jrequest_timeout_ms="%regression.request.timeout.ms%" \
  -Jsession_command_timeout_ms="%regression.session.command.timeout.ms%" \
  -Jrun_id="$RUN_ID" \
  -Jjmeter.save.saveservice.output_format=csv \
  -Jjmeter.save.saveservice.print_field_names=true \
  -Jjmeter.save.saveservice.requestHeaders=false \
  -Jjmeter.save.saveservice.responseHeaders=false \
  -Jjmeter.save.saveservice.samplerData=false \
  -Jjmeter.save.saveservice.response_data=false \
  -Jjmeter.save.saveservice.response_data.on_error=__RESPONSE_DATA_ON_ERROR__)"

tar --exclude=.git --exclude=outputs -cf - . | DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp - "$CID":/work
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker start -a "$CID" || status=$?
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp "$CID":/work/outputs/jmeter/teamcity outputs/jmeter/ || true

echo "##teamcity[publishArtifacts '$RESULT_DIR/** => jmeter-results']"
echo "##teamcity[publishArtifacts '$REPORT_DIR/** => jmeter-report']"

if [ "$status" -ne 0 ]; then
  echo "JMeter exited with status $status"
  exit "$status"
fi

if ! python3 -c '
import csv
import sys

path = sys.argv[1]
with open(path, newline="", encoding="utf-8") as handle:
    rows = list(csv.DictReader(handle))
if not rows:
    print("JMeter produced no samples", file=sys.stderr)
    raise SystemExit(1)
failed = [row.get("label", "unknown") for row in rows if row.get("success", "").lower() != "true"]
for label in failed[:20]:
    print(f"FAILED: {label}", file=sys.stderr)
raise SystemExit(1 if failed else 0)
' "$JTL"; then
  echo "JMeter assertions failed. See jmeter-results/electrahub-regression.jtl and jmeter-report/index.html."
  exit 1
fi

__REQUIRED_SUMMARY_GATE__
echo "ElectraHub regression completed successfully."
"""
    script = script.replace("__JMETER_DOCKER_ENVIRONMENT__", docker_environment)
    script = script.replace("__RESPONSE_DATA_ON_ERROR__", response_data_on_error)
    script = script.replace("__REQUIRED_SUMMARY_GATE__", required_summary_gate)
    if cfg.jmeter_credential_safe_mode:
        trusted_parameters = {
            "%jmeter.image%": (cfg.jmeter_image, "jmeterImage"),
            "%jmeter.plan%": (cfg.jmeter_plan, "jmeterPlan"),
            "%regression.base.url%": (cfg.regression_base_url, "regressionBaseUrl"),
            "%regression.users%": (cfg.regression_users, "regressionUsers"),
            "%regression.ramp.seconds%": (cfg.regression_ramp_seconds, "regressionRampSeconds"),
            "%regression.hold.seconds%": (cfg.regression_hold_seconds, "regressionHoldSeconds"),
            "%regression.sse.seconds%": (cfg.regression_sse_seconds, "regressionSseSeconds"),
            "%regression.host.header%": (cfg.regression_host_header, "regressionHostHeader"),
            "%regression.dynamic.connector.selection%": (
                cfg.regression_dynamic_connector_selection, "regressionDynamicConnectorSelection"
            ),
            "%regression.connector.start.attempts%": (
                cfg.regression_connector_start_attempts, "regressionConnectorStartAttempts"
            ),
            "%regression.request.timeout.ms%": (
                cfg.regression_request_timeout_ms, "regressionRequestTimeoutMs"
            ),
            "%regression.session.command.timeout.ms%": (
                cfg.regression_session_command_timeout_ms, "regressionSessionCommandTimeoutMs"
            ),
        }
        for token, (value, name) in trusted_parameters.items():
            script = script.replace(token, trusted_shell_value(value, name))
    create_script_step(tc, cfg, cfg.jmeter_step_name, script)


def create_jmeter_load_ladder_step(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    script = """set -eu

RESULT_ROOT="outputs/jmeter/teamcity-load"
DOCKER_CONFIG_DIR="$RESULT_ROOT/docker-config"
SUMMARY="$RESULT_ROOT/load-summary.csv"
STAGES="%jmeter.load.stages%"
MAX_ERROR_PERCENT="%jmeter.load.max.error.percent%"

rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT" "$DOCKER_CONFIG_DIR"
printf "{}\n" > "$DOCKER_CONFIG_DIR/config.json"
printf "stage,users,ramp_seconds,hold_seconds,sse_seconds,total_samples,failed_samples,error_percent,avg_elapsed_ms,max_elapsed_ms,result\n" > "$SUMMARY"

echo "Running ElectraHub load ladder against %regression.base.url%"
echo "Stages=$STAGES"
echo "MaxErrorPercent=$MAX_ERROR_PERCENT"
echo "DynamicConnectorSelection=%regression.dynamic.connector.selection% ConnectorStartAttempts=%regression.connector.start.attempts%"
echo "JMeter image=%jmeter.image%"

if [ -z "${ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN:-}" ] && { [ -z "${ELECTRAHUB_LOAD_CLEANUP_ADMIN_EMAIL:-}" ] || [ -z "${ELECTRAHUB_LOAD_CLEANUP_ADMIN_PASSWORD:-}" ]; }; then
  echo "The card-burst cleanup flow requires a protected cleanup admin token or protected cleanup admin email/password parameters."
  exit 1
fi

DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker pull "%jmeter.image%"
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker run --rm --entrypoint java "%jmeter.image%" -version
DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker run --rm "%jmeter.image%" --version | head -25

stage_number=0
for stage in $STAGES; do
  stage_number=$((stage_number + 1))
  IFS=: read -r USERS RAMP HOLD SSE <<EOF_STAGE
$stage
EOF_STAGE

  if [ -z "$USERS" ] || [ -z "$RAMP" ] || [ -z "$HOLD" ] || [ -z "$SSE" ]; then
    echo "Invalid stage '$stage'. Expected users:rampSeconds:holdSeconds:sseSeconds"
    exit 1
  fi

  STAGE_DIR="$RESULT_ROOT/stage-${stage_number}-u${USERS}"
  JTL="$STAGE_DIR/results.jtl"
  LOG="$STAGE_DIR/jmeter.log"
  REPORT="$STAGE_DIR/report"
  mkdir -p "$STAGE_DIR" "$REPORT"

  echo "== Stage ${stage_number}: users=$USERS ramp=${RAMP}s hold=${HOLD}s sse=${SSE}s =="
  CID="$(DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker create \
    -w /work \
    -e ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN \
    -e ELECTRAHUB_LOAD_CLEANUP_ADMIN_EMAIL \
    -e ELECTRAHUB_LOAD_CLEANUP_ADMIN_PASSWORD \
    -e JVM_ARGS="-Dhttps.protocols=TLSv1.3,TLSv1.2 -Djdk.tls.client.protocols=TLSv1.3,TLSv1.2" \
    "%jmeter.image%" \
    -n \
    -t "%jmeter.plan%" \
    -l "$JTL" \
    -j "$LOG" \
    -e \
    -o "$REPORT" \
    -Jbase_url="%regression.base.url%" \
    -Jusers="$USERS" \
    -Jramp_seconds="$RAMP" \
    -Jhold_seconds="$HOLD" \
    -Jsse_seconds="$SSE" \
    -Jrequest_host_header="%regression.host.header%" \
    -Jdynamic_connector_selection="%regression.dynamic.connector.selection%" \
    -Jconnector_start_attempts="%regression.connector.start.attempts%" \
    -Jrequest_timeout_ms="%regression.request.timeout.ms%" \
    -Jsession_command_timeout_ms="%regression.session.command.timeout.ms%" \
    -Jsession_payment_method="CARD" \
    -Jexclusive_connector_allocation=true \
    -Jcleanup_test_account=true \
    -Jconnectors_file="$STAGE_DIR/connectors.csv" \
    -Jcharger_page_size=200 \
    -Jcharger_max_pages=10 \
    -Jcharger_id_prefix="EH-US-" \
    -Jrun_id="tc-%build.number%-stage${stage_number}-u${USERS}" \
    -Jjmeter.save.saveservice.output_format=csv \
    -Jjmeter.save.saveservice.print_field_names=true \
    -Jjmeter.save.saveservice.response_data.on_error=true)"

  tar --exclude=.git --exclude=outputs -cf - . | DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp - "$CID":/work
  status=0
  DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker start -a "$CID" || status=$?
  DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker cp "$CID":/work/"$STAGE_DIR" "$RESULT_ROOT/" || true
  DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker rm -f "$CID" >/dev/null 2>&1 || true

  if [ ! -f "$JTL" ]; then
    echo "Stage ${stage_number} produced no JTL"
    printf "%s,%s,%s,%s,%s,0,0,100,0,0,NO_JTL\n" "$stage_number" "$USERS" "$RAMP" "$HOLD" "$SSE" >> "$SUMMARY"
    exit 1
  fi

  # JMeter writes quoted response messages that can contain embedded newlines.
  # A line-oriented awk parser silently splits those records and can turn a
  # failed stage into a false pass. Parse the CSV records with Python instead.
  stats="$(python3 - "$JTL" <<'PY'
import csv
import sys

path = sys.argv[1]
total = 0
failed = 0
elapsed_sum = 0
max_elapsed = 0

with open(path, newline="", encoding="utf-8") as handle:
    reader = csv.DictReader(handle)
    if reader.fieldnames is None or not {"elapsed", "success"}.issubset(reader.fieldnames):
        raise SystemExit("JMeter JTL is missing the elapsed or success column")
    for row in reader:
        total += 1
        elapsed = int(row.get("elapsed") or 0)
        elapsed_sum += elapsed
        max_elapsed = max(max_elapsed, elapsed)
        if str(row.get("success")).lower() != "true":
            failed += 1

average = elapsed_sum / total if total else 0
print(f"{total},{average:.0f},{max_elapsed},{failed}")
PY
)"

  total="$(echo "$stats" | cut -d, -f1)"
  avg_elapsed="$(echo "$stats" | cut -d, -f2)"
  max_elapsed="$(echo "$stats" | cut -d, -f3)"
  failed="$(echo "$stats" | cut -d, -f4)"
  error_percent="$(awk -v total="$total" -v failed="$failed" 'BEGIN { printf "%.2f", (total ? (failed * 100 / total) : 100) }')"

  if [ "$status" -ne 0 ]; then
    result="JMETER_EXIT_${status}"
  elif awk "BEGIN { exit !($error_percent > $MAX_ERROR_PERCENT) }"; then
    result="BREAKPOINT"
  else
    result="PASS"
  fi

  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n" "$stage_number" "$USERS" "$RAMP" "$HOLD" "$SSE" "$total" "$failed" "$error_percent" "$avg_elapsed" "$max_elapsed" "$result" >> "$SUMMARY"
  echo "Stage ${stage_number} result=$result total=$total failed=$failed errorPercent=$error_percent avgMs=$avg_elapsed maxMs=$max_elapsed"

  if [ "$result" != "PASS" ]; then
    echo "Load ladder stopped at stage ${stage_number}; breakpoint is around users=$USERS."
    echo "Recent failures:"
    grep ',false,' "$JTL" | head -20 | sed 's/^/FAILED: /' || true
    echo "##teamcity[publishArtifacts '$RESULT_ROOT/** => jmeter-load']"
    exit 1
  fi
done

echo "Load ladder completed all stages below failure threshold."
cat "$SUMMARY"
echo "##teamcity[publishArtifacts '$RESULT_ROOT/** => jmeter-load']"
"""
    create_script_step(tc, cfg, "JMeter Load Ladder", script)


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


def create_steps_if_empty(
    tc: TeamCityClient,
    cfg: PipelineConfig,
    replace_existing_steps: bool = False,
) -> None:
    build_type = tc.request("GET", f"/app/rest/buildTypes/id:{cfg.build_type_id}")
    replaced_existing_steps = False
    if int(build_type.get("steps", {}).get("count", 0)) > 0:
        if cfg.build_kind == "jmeter":
            clear_steps(tc, cfg)
            create_jmeter_regression_step(tc, cfg)
            print("Replaced JMeter regression step")
            return
        if replace_existing_steps or cfg.build_kind in ("go", "node"):
            clear_steps(tc, cfg)
            replaced_existing_steps = True
        else:
            print("Build steps already configured; use --replace-steps to reconcile them")
            return

    if cfg.build_kind == "jmeter":
        create_jmeter_regression_step(tc, cfg)
        print("Created JMeter regression step")
        return

    create_source_build_step(tc, cfg)

    if cfg.build_kind == "flutter":
        if replaced_existing_steps:
            print("Replaced flutter build steps")
        else:
            print("Created Flutter artifact build step")
        return

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
# Keep one builder per agent rather than creating one per build. The old build-number
# builders accumulated persistent BuildKit volumes until the Docker host ran out of disk.
BUILDER="electrahub-ci-$(hostname | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9_.-' '-')"
if ! docker buildx inspect "$BUILDER" >/dev/null 2>&1; then
  docker buildx create --driver docker-container --name "$BUILDER"
fi
docker buildx inspect "$BUILDER" --bootstrap

cleanup_build_cache() {{
  docker buildx prune --builder "$BUILDER" --force --max-used-space 20GB >/dev/null 2>&1 || true
}}
trap cleanup_build_cache EXIT

docker buildx build \\
  --builder "$BUILDER" \\
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
    if replaced_existing_steps:
        print(f"Replaced {cfg.build_kind} build steps")
    else:
        print("Created build steps")


def ensure_trigger(tc: TeamCityClient, cfg: PipelineConfig) -> None:
    path = f"/app/rest/buildTypes/id:{cfg.build_type_id}/triggers"
    response = tc.request("GET", path)
    triggers = response.get("trigger", [])

    if cfg.add_vcs_trigger:
        if any(trigger.get("type") == "vcsTrigger" for trigger in triggers):
            print("VCS trigger already configured")
        else:
            tc.request("POST", path, {
                "type": "vcsTrigger",
                "properties": {"property": [
                    step_property("branchFilter", "+:<default>"),
                    step_property("enableQueueOptimization", "true"),
                    step_property("quietPeriodMode", "DO_NOT_USE"),
                ]},
            })
            print("Created VCS trigger")
    else:
        print("Skipped VCS trigger")

    if not cfg.finish_build_trigger_source_id:
        return

    def properties(trigger: dict[str, Any]) -> dict[str, str]:
        values = trigger.get("properties", {}).get("property", [])
        return {str(value.get("name")): str(value.get("value", "")) for value in values}

    if any(
        trigger.get("type") == "buildDependencyTrigger"
        and properties(trigger).get("dependsOn") == cfg.finish_build_trigger_source_id
        for trigger in triggers
    ):
        print("Finish-build trigger already configured")
        return
    tc.request("POST", path, {
        "type": "buildDependencyTrigger",
        "properties": {"property": [
            step_property("dependsOn", cfg.finish_build_trigger_source_id),
            step_property("afterSuccessfulBuildOnly", "true"),
            step_property("branchFilter", "+:<default>"),
        ]},
    })
    print(f"Created finish-build trigger: {cfg.finish_build_trigger_source_id}")


def create_pipeline(
    tc: TeamCityClient,
    cfg: PipelineConfig,
    replace_existing_steps: bool = False,
) -> None:
    ensure_parent_project(tc, cfg)
    ensure_project(tc, cfg)
    ensure_vcs_root(tc, cfg)
    ensure_build_type(tc, cfg)
    attach_vcs_root(tc, cfg)
    set_parameters(tc, cfg)
    ensure_agent_requirement(tc, cfg)
    create_steps_if_empty(tc, cfg, replace_existing_steps)
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
    parser.add_argument(
        "--replace-steps",
        action="store_true",
        help="Replace existing build steps with the checked-in pipeline definition.",
    )
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
            create_pipeline(tc, cfg, args.replace_steps)
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
