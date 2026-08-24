"""Acceptance tests for the fail-closed release verification gate.

The tests inspect the Ant project and the SignPath workflow.  The one dynamic
negative-path check imports the real Ant project but replaces expensive build
targets and ``install4jc`` with local test doubles; it never builds or runs an
installer and never uses the network.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NamedTuple
from xml.sax.saxutils import quoteattr


REPO_ROOT = Path(__file__).resolve().parents[2]
BUILD_XML = REPO_ROOT / "build.xml"
VERSION_FILE = REPO_ROOT / "VERSION.txt"
WORKFLOW_FILE = REPO_ROOT / ".github" / "workflows" / "sign-windows-test.yml"
FAILURE_FIXTURE = (
    REPO_ROOT / "test" / "release" / "fixtures" / "IntentionalFailureTest.java.fixture"
)
JUNIT_JAR = REPO_ROOT / "lib" / "junit-4.13.1.jar"
HAMCREST_JAR = REPO_ROOT / "lib" / "hamcrest-core-1.3.jar"
RELEASE_TAG_PROPERTY = "jaer.release.tag"
INTENTIONAL_FAILURE = "INTENTIONAL_RELEASE_GATE_FAILURE"

# These are existing, deterministic production-path regression programs.  They
# must stay explicit so adding a new main class cannot silently change the
# release gate.
REQUIRED_HEADLESS_PROBES = {
    "net.sf.jaer.eventio.RecordingConfigSnapshotDemo",
    "net.sf.jaer.eventio.Aedat4ConfigSnapshotDemo",
    "net.sf.jaer.eventio.DataLoggerMetadataDemo",
    "net.sf.jaer.eventio.AEDZRoundtripDemo",
    "net.sf.jaer.eventio.aedat4.Aedat4RoundtripDemo",
    "net.sf.jaer.graphics.AEViewerSnapshotProbe",
}


class WorkflowStep(NamedTuple):
    name: str
    condition: str
    uses: str
    run: str
    continue_on_error: str


def _local_name(element: ET.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def _split_dependencies(value: str | None) -> list[str]:
    if not value:
        return []
    return [part.strip() for part in value.split(",") if part.strip()]


def _yaml_scalar(block: str, key: str) -> str:
    match = re.search(rf"(?m)^        {re.escape(key)}:\s*(.*?)\s*$", block)
    return match.group(1).strip("'\"") if match else ""


def _yaml_run(block: str) -> str:
    match = re.search(r"(?m)^        run:\s*(.*)$", block)
    if not match:
        return ""
    marker = match.group(1).strip()
    if marker not in {"|", ">", "|-", ">-"}:
        return marker.strip("'\"")

    lines: list[str] = []
    for line in block[match.end() :].splitlines():
        if line and len(line) - len(line.lstrip()) <= 8:
            break
        lines.append(line[10:] if line.startswith("          ") else line.lstrip())
    return "\n".join(lines)


def _workflow_steps(text: str) -> list[WorkflowStep]:
    starts = list(re.finditer(r"(?m)^      - name:\s*(.*?)\s*$", text))
    steps: list[WorkflowStep] = []
    for index, start in enumerate(starts):
        stop = starts[index + 1].start() if index + 1 < len(starts) else len(text)
        block = text[start.start() : stop]
        steps.append(
            WorkflowStep(
                name=start.group(1).strip("'\""),
                condition=_yaml_scalar(block, "if"),
                uses=_yaml_scalar(block, "uses"),
                run=_yaml_run(block),
                continue_on_error=_yaml_scalar(block, "continue-on-error"),
            )
        )
    return steps


def _has_status_bypass(condition: str) -> bool:
    return bool(
        re.search(r"\b(?:always|failure|cancelled)\s*\(", condition, re.IGNORECASE)
    )


def _is_tag_only(condition: str) -> bool:
    compact = re.sub(r"\s+", "", condition).lower().replace('"', "'")
    return (
        "github.ref_type=='tag'" in compact
        or "startswith(github.ref,'refs/tags/')" in compact
    )


def _is_non_tag_only(condition: str) -> bool:
    compact = re.sub(r"\s+", "", condition).lower().replace('"', "'")
    return (
        "github.ref_type!='tag'" in compact
        or "!startswith(github.ref,'refs/tags/')" in compact
    )


def _resolve_tool(name: str) -> str | None:
    on_path = shutil.which(name)
    if on_path:
        return on_path

    candidates: list[Path] = []
    if name == "ant":
        ant_home = os.environ.get("ANT_HOME")
        if ant_home:
            candidates.append(Path(ant_home) / "bin" / "ant")
        candidates.extend(
            sorted(
                (Path.home() / ".local" / "share").glob("apache-ant-*/bin/ant"),
                reverse=True,
            )
        )
    elif name in {"java", "javac"}:
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            candidates.append(Path(java_home) / "bin" / name)
        candidates.extend(
            sorted(
                (Path.home() / ".local" / "share").glob(f"jdk-*/bin/{name}"),
                reverse=True,
            )
        )
    return str(next((candidate for candidate in candidates if candidate.is_file()), "")) or None


class ReleaseGateAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.build_root = ET.parse(BUILD_XML).getroot()
        cls.targets = {
            target.get("name"): target
            for target in cls.build_root
            if _local_name(target) == "target" and target.get("name")
        }
        cls.version = VERSION_FILE.read_text(encoding="utf-8").strip()
        cls.workflow_text = WORKFLOW_FILE.read_text(encoding="utf-8")
        cls.steps = _workflow_steps(cls.workflow_text)

    def _require_verify_target(self) -> ET.Element:
        self.assertIn(
            "verify-release",
            self.targets,
            "build.xml must define a verify-release target before any installer packaging",
        )
        return self.targets["verify-release"]

    def _closure(self, target_name: str) -> set[str]:
        seen: set[str] = set()
        pending = [target_name]
        while pending:
            current = pending.pop()
            if current in seen:
                continue
            seen.add(current)
            target = self.targets.get(current)
            if target is not None:
                pending.extend(_split_dependencies(target.get("depends")))
        return seen

    def _execution_order(self, target_name: str) -> list[str]:
        ordered: list[str] = []
        visited: set[str] = set()

        def visit(current: str) -> None:
            if current in visited:
                return
            visited.add(current)
            target = self.targets.get(current)
            if target is not None:
                for dependency in _split_dependencies(target.get("depends")):
                    visit(dependency)
            ordered.append(current)

        visit(target_name)
        return ordered

    def _elements_in_closure(self, target_name: str, element_name: str) -> list[ET.Element]:
        elements: list[ET.Element] = []
        for name in self._closure(target_name):
            target = self.targets.get(name)
            if target is not None:
                elements.extend(
                    element for element in target.iter() if _local_name(element) == element_name
                )
        return elements

    def _release_tag_contract(self) -> tuple[str, str]:
        self._require_verify_target()
        closure = self._closure("verify-release")
        self.assertIn(
            "-load-version",
            closure,
            "verify-release must load the trimmed VERSION.txt value",
        )

        load_version = self.targets.get("-load-version")
        self.assertIsNotNone(load_version, "build.xml must retain the -load-version target")
        version_loads = [
            element
            for element in load_version.iter()
            if _local_name(element) == "loadfile"
            and (element.get("srcFile") or "").endswith("VERSION.txt")
        ]
        self.assertEqual(1, len(version_loads), "-load-version must read VERSION.txt exactly once")
        self.assertTrue(
            any(_local_name(element) == "trim" for element in version_loads[0].iter()),
            "VERSION.txt must be trimmed before release-tag comparison",
        )

        matching_contracts: list[tuple[str, ET.Element]] = []
        for condition in self._elements_in_closure("verify-release", "condition"):
            result_property = condition.get("property")
            if not result_property:
                continue
            for equals in condition.iter():
                if _local_name(equals) != "equals":
                    continue
                arguments = {equals.get("arg1"), equals.get("arg2")}
                if arguments == {
                    "${jaer.version}",
                    "${" + RELEASE_TAG_PROPERTY + "}",
                }:
                    matching_contracts.append((result_property, condition))

        self.assertEqual(
            1,
            len(matching_contracts),
            "verify-release must compare ${jaer.release.tag} exactly with trimmed ${jaer.version}",
        )
        result_property, condition = matching_contracts[0]

        equality = next(
            element
            for element in condition.iter()
            if _local_name(element) == "equals"
            and {element.get("arg1"), element.get("arg2")}
            == {"${jaer.version}", "${" + RELEASE_TAG_PROPERTY + "}"}
        )
        self.assertNotEqual(
            "false",
            equality.get("casesensitive", "true").lower(),
            "release-tag equality must be case-sensitive",
        )
        self.assertNotEqual(
            "true",
            equality.get("trim", "false").lower(),
            "only VERSION.txt may be trimmed; the supplied tag must match exactly",
        )

        optional_when_absent = any(
            _local_name(element) == "or"
            and any(
                _local_name(descendant) == "equals"
                and {descendant.get("arg1"), descendant.get("arg2")}
                == {"${jaer.version}", "${" + RELEASE_TAG_PROPERTY + "}"}
                for descendant in element.iter()
            )
            and any(
                _local_name(descendant) == "not"
                and any(
                    _local_name(candidate) == "isset"
                    and candidate.get("property") == RELEASE_TAG_PROPERTY
                    for candidate in descendant.iter()
                )
                for descendant in element.iter()
            )
            for element in condition.iter()
        )
        self.assertTrue(
            optional_when_absent,
            "verify-release must permit an omitted tag for manual branch test-signing",
        )

        enforcing_failures = [
            failure
            for failure in self._elements_in_closure("verify-release", "fail")
            if failure.get("unless") == result_property
        ]
        self.assertTrue(
            enforcing_failures,
            "the exact tag/version comparison must fail verify-release on mismatch",
        )
        defaults = [
            element
            for element in self.build_root
            if _local_name(element) == "property"
            and element.get("name") == RELEASE_TAG_PROPERTY
        ]
        self.assertEqual(
            [],
            defaults,
            "jaer.release.tag must remain genuinely absent when manual signing omits it",
        )
        return RELEASE_TAG_PROPERTY, result_property

    def test_verify_release_runs_junit_and_named_headless_probes(self) -> None:
        self._require_verify_target()
        closure = self._closure("verify-release")
        self.assertIn("test", closure, "verify-release must run the complete JUnit test target")

        probe_tasks: dict[str, tuple[str, ET.Element]] = {}
        for target_name in closure:
            target = self.targets.get(target_name)
            if target is None:
                continue
            for java in target.iter():
                if _local_name(java) == "java" and java.get("classname") in REQUIRED_HEADLESS_PROBES:
                    probe_tasks[java.get("classname")] = (target_name, java)

        self.assertEqual(
            REQUIRED_HEADLESS_PROBES,
            set(probe_tasks),
            "verify-release must run the complete explicit headless-probe set",
        )
        order = self._execution_order("verify-release")
        for classname, (owner, java) in probe_tasks.items():
            with self.subTest(probe=classname):
                self.assertEqual(
                    "true",
                    java.get("failonerror", "false").lower(),
                    f"{classname} must fail the release gate on a non-zero exit",
                )
                self.assertEqual(
                    "true",
                    java.get("fork", "false").lower(),
                    f"{classname} must run in an isolated JVM",
                )
                headless = any(
                    (
                        _local_name(element) == "jvmarg"
                        and "-Djava.awt.headless=true"
                        in " ".join(filter(None, (element.get("value"), element.get("line"))))
                    )
                    or (
                        _local_name(element) == "sysproperty"
                        and element.get("key") == "java.awt.headless"
                        and element.get("value", "").lower() == "true"
                    )
                    for element in java.iter()
                )
                self.assertTrue(headless, f"{classname} must set java.awt.headless=true")
                self.assertLess(
                    order.index("test"),
                    order.index(owner),
                    "JUnit must execute before the registered headless probes",
                )

    def test_release_tag_must_equal_trimmed_version_exactly(self) -> None:
        tag_property, _ = self._release_tag_contract()
        property_argument = f"-D{tag_property}="
        tagged_builds = [
            step
            for step in self.steps
            if "release-windows-ci" in step.run
            and property_argument in step.run
            and "github.ref_name" in step.run
            and (
                _is_tag_only(step.condition)
                or (
                    "github.ref_type" in step.run
                    and re.search(r"(?i)\btag\b", step.run)
                    and re.search(r"(?i)\bif\b", step.run)
                )
            )
        ]
        self.assertTrue(
            tagged_builds,
            "tag-triggered Windows builds must pass github.ref_name as -Djaer.release.tag",
        )

    def test_every_installer_creation_path_depends_on_verify_release(self) -> None:
        self._require_verify_target()
        for public_target in ("install4j", "release", "release-windows-ci"):
            with self.subTest(target=public_target):
                self.assertIn(public_target, self.targets, f"missing public target {public_target}")
                self.assertIn(
                    "verify-release",
                    self._closure(public_target),
                    f"{public_target} can package an installer without verify-release",
                )

        installer_targets = []
        for target_name, target in self.targets.items():
            if any(
                _local_name(element) == "exec"
                and "install4jc" in (element.get("executable") or "").lower()
                for element in target.iter()
            ):
                installer_targets.append(target_name)
        self.assertTrue(installer_targets, "build.xml must retain explicit install4jc execution targets")
        for target_name in installer_targets:
            with self.subTest(target=target_name):
                self.assertIn(
                    "verify-release",
                    self._closure(target_name),
                    f"{target_name} can invoke install4jc without verify-release",
                )

    def test_intentional_failure_fixture_is_a_real_junit_failure(self) -> None:
        javac = _resolve_tool("javac")
        java = _resolve_tool("java")
        self.assertIsNotNone(javac, "javac is required to validate the intentional-failure fixture")
        self.assertIsNotNone(java, "java is required to validate the intentional-failure fixture")
        self.assertTrue(JUNIT_JAR.is_file(), f"missing project JUnit jar: {JUNIT_JAR}")
        self.assertTrue(HAMCREST_JAR.is_file(), f"missing project Hamcrest jar: {HAMCREST_JAR}")

        with tempfile.TemporaryDirectory(prefix="jaer-release-fixture-") as temporary:
            root = Path(temporary)
            source = root / "IntentionalFailureTest.java"
            classes = root / "classes"
            classes.mkdir()
            shutil.copyfile(FAILURE_FIXTURE, source)
            classpath = os.pathsep.join((str(JUNIT_JAR), str(HAMCREST_JAR)))
            compile_result = subprocess.run(
                [javac, "-cp", classpath, "-d", str(classes), str(source)],
                cwd=REPO_ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(
                0,
                compile_result.returncode,
                "intentional JUnit fixture must compile without missing imports:\n"
                + compile_result.stdout
                + compile_result.stderr,
            )
            run_result = subprocess.run(
                [
                    java,
                    "-cp",
                    os.pathsep.join((str(classes), classpath)),
                    "org.junit.runner.JUnitCore",
                    "IntentionalFailureTest",
                ],
                cwd=REPO_ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            output = run_result.stdout + run_result.stderr
            self.assertNotEqual(0, run_result.returncode, "intentional JUnit fixture must fail")
            self.assertIn(
                INTENTIONAL_FAILURE,
                output,
                "fixture must fail for its deliberate assertion, not a loader/import error",
            )

    def test_deliberate_junit_failure_blocks_installer_creation(self) -> None:
        ant = _resolve_tool("ant")
        java = _resolve_tool("java")
        self.assertIsNotNone(ant, "Ant is required for the release-gate negative-path test")
        self.assertIsNotNone(java, "Java is required for the release-gate negative-path test")
        self.assertTrue(self.version, "VERSION.txt must contain a release version")

        with tempfile.TemporaryDirectory(prefix="jaer-release-gate-") as temporary:
            root = Path(temporary)
            source_dir = root / "fixture-src"
            classes_dir = root / "fixture-classes"
            fake_bin = root / "bin"
            marker = root / "INSTALLER_CREATION_REACHED"
            source_dir.mkdir()
            classes_dir.mkdir()
            fake_bin.mkdir()

            fake_install4jc = fake_bin / "install4jc"
            fake_install4jc.write_text(
                "#!/bin/sh\n: > \"$RELEASE_GATE_INSTALLER_MARKER\"\nexit 0\n",
                encoding="utf-8",
            )
            fake_install4jc.chmod(0o755)

            harness = root / "release-gate-harness.xml"
            harness.write_text(
                textwrap.dedent(
                    f"""\
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project name="release-gate-negative" default="release-windows-ci"
                             basedir={quoteattr(str(REPO_ROOT))}>
                        <target name="test">
                            <copy file={quoteattr(str(FAILURE_FIXTURE))}
                                  tofile={quoteattr(str(source_dir / 'IntentionalFailureTest.java'))}/>
                            <javac srcdir={quoteattr(str(source_dir))}
                                   destdir={quoteattr(str(classes_dir))}
                                   includeantruntime="false">
                                <classpath>
                                    <pathelement location={quoteattr(str(JUNIT_JAR))}/>
                                    <pathelement location={quoteattr(str(HAMCREST_JAR))}/>
                                </classpath>
                            </javac>
                            <java classname="org.junit.runner.JUnitCore" fork="true"
                                  failonerror="true">
                                <classpath>
                                    <pathelement location={quoteattr(str(classes_dir))}/>
                                    <pathelement location={quoteattr(str(JUNIT_JAR))}/>
                                    <pathelement location={quoteattr(str(HAMCREST_JAR))}/>
                                </classpath>
                                <arg value="IntentionalFailureTest"/>
                            </java>
                        </target>

                        <!-- Keep this acceptance test offline and non-packaging. -->
                        <target name="clean"/>
                        <target name="jar"/>
                        <target name="generate-splash"/>
                        <target name="-sync-install4j-version"/>
                        <target name="split-opencv-natives"/>
                        <target name="stage-install4j-runtime"/>

                        <import file={quoteattr(str(BUILD_XML))}/>
                    </project>
                    """
                ),
                encoding="utf-8",
            )
            environment = os.environ.copy()
            java_bin = Path(java).parent
            environment["JAVA_HOME"] = str(java_bin.parent)
            environment["PATH"] = os.pathsep.join(
                (str(fake_bin), str(java_bin), str(Path(ant).parent), environment.get("PATH", ""))
            )
            environment["RELEASE_GATE_INSTALLER_MARKER"] = str(marker)
            result = subprocess.run(
                [
                    ant,
                    "-f",
                    str(harness),
                    f"-D{RELEASE_TAG_PROPERTY}={self.version}",
                    "release-windows-ci",
                ],
                cwd=REPO_ROOT,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            output = result.stdout + result.stderr
            actual = (
                result.returncode != 0,
                INTENTIONAL_FAILURE in output,
                marker.exists(),
            )
            self.assertEqual(
                (True, True, False),
                actual,
                "a deliberate JUnit failure must stop release-windows-ci before install4jc; "
                f"observed exit={result.returncode}, intentional_failure_seen={actual[1]}, "
                f"installer_reached={actual[2]}\n{output}",
            )

    def test_signpath_and_github_release_upload_have_no_verification_bypass(self) -> None:
        build_indices = [
            index for index, step in enumerate(self.steps) if "release-windows-ci" in step.run
        ]
        self.assertTrue(build_indices, "SignPath workflow must invoke ant release-windows-ci")
        for index in build_indices:
            step = self.steps[index]
            self.assertNotEqual(
                "true",
                step.continue_on_error.lower(),
                f"{step.name} must not convert release-verification failure into success",
            )
            self.assertFalse(
                _has_status_bypass(step.condition),
                f"{step.name} must not run through a failure-status bypass",
            )
        self.assertIn(
            "verify-release",
            self._closure("release-windows-ci"),
            "the workflow build can reach signing after compile-only release-windows-ci success",
        )

        signpath_indices = [
            index for index, step in enumerate(self.steps) if "signpath/" in step.uses.lower()
        ]
        publication_indices = [
            index
            for index, step in enumerate(self.steps)
            if "action-gh-release" in step.uses.lower()
            or re.search(r"\bgh\s+release\s+(?:create|edit|upload)\b", step.run)
        ]
        self.assertTrue(signpath_indices, "workflow must retain the SignPath submission step")
        self.assertTrue(publication_indices, "workflow must retain the GitHub Release upload step")

        last_build = max(build_indices)
        first_signpath = min(signpath_indices)
        self.assertGreater(first_signpath, last_build, "SignPath must run after verified packaging")
        for index in signpath_indices + publication_indices:
            step = self.steps[index]
            with self.subTest(step=step.name):
                self.assertGreater(index, last_build, f"{step.name} runs before release verification")
                self.assertFalse(
                    _has_status_bypass(step.condition),
                    f"{step.name} must not use a failure/always path around release verification",
                )
                self.assertNotEqual(
                    "true",
                    step.continue_on_error.lower(),
                    f"{step.name} must not continue after verification failure",
                )
        for index in publication_indices:
            self.assertGreater(
                index,
                first_signpath,
                "GitHub Release upload must occur only after successful SignPath signing",
            )

    def test_manual_branch_test_signing_can_omit_tag_but_cannot_publish(self) -> None:
        self.assertRegex(
            self.workflow_text,
            r"(?m)^\s*workflow_dispatch:\s*$",
            "workflow must support manual test-signing dispatch",
        )
        tag_argument = f"-D{RELEASE_TAG_PROPERTY}="
        manual_builds = []
        for step in self.steps:
            if "release-windows-ci" not in step.run:
                continue
            has_tag_argument = tag_argument in step.run
            conditional_argument = (
                has_tag_argument
                and "github.ref_type" in step.run
                and re.search(r"(?i)\btag\b", step.run)
                and re.search(r"(?i)\bif\b", step.run)
            )
            if conditional_argument or (
                not has_tag_argument and (not step.condition or _is_non_tag_only(step.condition))
            ):
                manual_builds.append(step)
        self.assertTrue(
            manual_builds,
            "manual branch test-signing must have a release-windows-ci path that omits the tag",
        )

        signpath_steps = [step for step in self.steps if "signpath/" in step.uses.lower()]
        self.assertTrue(signpath_steps, "manual dispatch must retain SignPath test-signing")
        self.assertTrue(
            any(not _is_tag_only(step.condition) for step in signpath_steps),
            "SignPath test-signing must remain reachable from a manual branch dispatch",
        )

        publication_steps = [
            step
            for step in self.steps
            if "action-gh-release" in step.uses.lower()
            or re.search(r"\bgh\s+release\s+(?:create|edit|upload)\b", step.run)
        ]
        self.assertTrue(publication_steps, "workflow must retain a tagged publication step")
        for step in publication_steps:
            with self.subTest(step=step.name):
                self.assertTrue(
                    _is_tag_only(step.condition),
                    f"manual branch dispatch can publish through unguarded step: {step.name}",
                )
                self.assertFalse(
                    _has_status_bypass(step.condition),
                    f"publication step {step.name} must not override its tag guard after failure",
                )


if __name__ == "__main__":
    unittest.main()
