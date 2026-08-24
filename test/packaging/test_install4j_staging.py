"""Acceptance tests for the fail-closed install4j runtime stage.

These tests intentionally use only the Python standard library.  They inspect
the Ant and install4j XML; they do not run Ant, install4j, or an installer.
"""

from __future__ import annotations

import fnmatch
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath
from typing import NamedTuple


REPO_ROOT = Path(__file__).resolve().parents[2]
BUILD_XML = REPO_ROOT / "build.xml"
INSTALL4J_PROJECT = REPO_ROOT / "install4j" / "jaer.install4j"
RUNTIME_STAGE = REPO_ROOT / "build" / "install4j-stage"

# Runtime trees must remain recursive: the contents change as dependencies and
# device presets change.  The other entries are deliberately individual files.
REQUIRED_RUNTIME_PROBES = {
    "COPYING",
    "README.md",
    "VERSION.txt",
    "biasgenSettings/SciDVS/runtime-preset.xml",
    "conf/Logging.properties",
    "deviceSettings/DVS128/runtime-preset.xml",
    "dist/jAER.jar",
    "filterSettings/runtime-filter.xml",
    "images/1024w/SplashScreen.png",
    "jars/runtime-dependency.jar",
    "lib/runtime-dependency.jar",
    "sounds/runtime-sound.wav",
}

ALLOWED_SOURCE_TOP_LEVEL = {
    "COPYING",
    "README.md",
    "VERSION.txt",
    "biasgenSettings",
    "conf",
    "deviceSettings",
    "dist",
    "filterSettings",
    "images",
    "jars",
    "lib",
    "sounds",
}

UNKNOWN_SENTINEL = "INSTALL4J_UNKNOWN_TOP_LEVEL_SENTINEL.txt"
FORBIDDEN_PROBES = {
    UNKNOWN_SENTINEL,
    ".git/config",
    ".github/workflows/release.yml",
    ".ocs/session.json",
    ".cs/handoffs/session.md",
    ".cursor/rules/agent.mdc",
    ".signpath/private-key.pem",
    ".vscode/settings.json",
    "build/classes/Application.class",
    "credentials.json",
    "docs/design-source.md",
    "images/design-source.psd",
    "install4j/license.txt",
    "nbproject/project.xml",
    "packaging/release.yml",
    "scripts/release.sh",
    "secrets/token.txt",
    "signpath/install4j-license.txt",
    "src/Application.java",
    "test/ApplicationTest.java",
    "unknown-runtime-tree/payload.bin",
    # Source/VCS/credential material nested below otherwise valid runtime roots.
    "conf/.gitignore",
    "conf/credentials.properties",
    "dist/javadoc/index.html",
    "dist/jAER.jar.7z",
    "jars/dependency-sources.jar",
    "jars/dependency-src.zip",
    "lib/dependency-javadoc.jar",
    "lib/private-key.p12",
}


class Selector(NamedTuple):
    include: str
    excludes: tuple[str, ...]
    follows_symlinks: bool


def _local_name(element: ET.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def _normalise_ant_path(value: str) -> str:
    value = value.replace("\\", "/")
    while value.startswith("./"):
        value = value[2:]
    return value.rstrip("/") or "."


def _split_patterns(value: str | None) -> list[str]:
    if not value:
        return []
    return [part for part in re.split(r"[\s,]+", value.strip()) if part]


def _ant_match(pattern: str, candidate: str) -> bool:
    """Match the Ant ``**``/glob subset used by staging filesets."""

    pattern_parts = PurePosixPath(_normalise_ant_path(pattern)).parts
    candidate_parts = PurePosixPath(_normalise_ant_path(candidate)).parts

    def match(pattern_index: int, candidate_index: int) -> bool:
        if pattern_index == len(pattern_parts):
            return candidate_index == len(candidate_parts)
        pattern_part = pattern_parts[pattern_index]
        if pattern_part == "**":
            return match(pattern_index + 1, candidate_index) or (
                candidate_index < len(candidate_parts)
                and match(pattern_index, candidate_index + 1)
            )
        return (
            candidate_index < len(candidate_parts)
            and fnmatch.fnmatchcase(candidate_parts[candidate_index], pattern_part)
            and match(pattern_index + 1, candidate_index + 1)
        )

    return match(0, 0)


def _selected(selectors: list[Selector], candidate: str) -> bool:
    return any(
        _ant_match(selector.include, candidate)
        and not any(_ant_match(exclude, candidate) for exclude in selector.excludes)
        for selector in selectors
    )


class Install4jStagingAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.build_tree = ET.parse(BUILD_XML)
        cls.build_root = cls.build_tree.getroot()
        cls.targets = {
            target.get("name"): target
            for target in cls.build_root
            if _local_name(target) == "target" and target.get("name")
        }
        cls.properties = {"basedir": str(REPO_ROOT)}
        for element in cls.build_root:
            if _local_name(element) == "property" and element.get("name") and element.get("value"):
                cls.properties.setdefault(element.get("name"), element.get("value"))
        for _ in range(len(cls.properties) + 1):
            changed = False
            for name, value in tuple(cls.properties.items()):
                resolved = cls._resolve(value)
                if resolved != value:
                    cls.properties[name] = resolved
                    changed = True
            if not changed:
                break

        cls.install4j_tree = ET.parse(INSTALL4J_PROJECT)
        cls.install4j_root = cls.install4j_tree.getroot()

    @classmethod
    def _resolve(cls, value: str) -> str:
        result = value
        for _ in range(len(cls.properties) + 1):
            replaced = re.sub(
                r"\$\{([^}]+)\}",
                lambda match: cls.properties.get(match.group(1), match.group(0)),
                result,
            )
            if replaced == result:
                break
            result = replaced
        return result

    def _stage_target(self) -> tuple[str, ET.Element]:
        candidates = [
            (name, target)
            for name, target in self.targets.items()
            if "install4j" in name.lower() and "stage" in name.lower()
        ]
        self.assertEqual(
            1,
            len(candidates),
            "build.xml must define one explicit install4j runtime-stage target",
        )
        return candidates[0]

    def _stage_selectors(self, target: ET.Element) -> list[Selector]:
        selectors: list[Selector] = []
        stage = RUNTIME_STAGE.resolve(strict=False)
        copies = [element for element in target.iter() if _local_name(element) == "copy"]
        self.assertTrue(copies, "the install4j runtime-stage target must copy its allowlist")

        for copy in copies:
            self.assertIsNone(
                copy.get("file"),
                "stage inputs must use followsymlinks=false filesets, not direct file copies",
            )
            destination = copy.get("todir")
            self.assertIsNotNone(destination, "each runtime-stage copy needs a todir")
            destination_path = Path(self._resolve(destination)).resolve(strict=False)
            self.assertEqual(
                stage,
                destination_path,
                "runtime allowlist copies must preserve paths under build/install4j-stage",
            )

            filesets = [element for element in copy if _local_name(element) == "fileset"]
            self.assertTrue(filesets, "each runtime-stage copy must contain a fileset")
            for fileset in filesets:
                source = fileset.get("dir")
                self.assertIsNotNone(source, "runtime-stage filesets need an explicit source dir")
                source_path = Path(self._resolve(source)).resolve(strict=False)
                self.assertEqual(
                    REPO_ROOT.resolve(strict=False),
                    source_path,
                    "runtime allowlist must select explicit paths relative to the repository root",
                )
                follows_symlinks = fileset.get("followsymlinks", "true").lower() == "true"
                self.assertFalse(
                    follows_symlinks,
                    "runtime-stage filesets must set followsymlinks=false to prevent escapes",
                )

                includes = _split_patterns(fileset.get("includes"))
                excludes = _split_patterns(fileset.get("excludes"))
                for child in fileset:
                    if _local_name(child) == "include" and child.get("name"):
                        includes.append(child.get("name"))
                    elif _local_name(child) == "exclude" and child.get("name"):
                        excludes.append(child.get("name"))
                self.assertTrue(includes, "runtime-stage filesets must have explicit includes")
                selectors.extend(
                    Selector(
                        _normalise_ant_path(include),
                        tuple(_normalise_ant_path(exclude) for exclude in excludes),
                        follows_symlinks,
                    )
                    for include in includes
                )
        return selectors

    def test_build_recreates_and_wires_explicit_runtime_stage(self) -> None:
        stage_name, stage_target = self._stage_target()
        stage_path = RUNTIME_STAGE.resolve(strict=False)
        operations = list(stage_target)
        deletes = [
            index
            for index, element in enumerate(operations)
            if _local_name(element) == "delete"
            and element.get("dir")
            and Path(self._resolve(element.get("dir"))).resolve(strict=False) == stage_path
        ]
        mkdirs = [
            index
            for index, element in enumerate(operations)
            if _local_name(element) == "mkdir"
            and element.get("dir")
            and Path(self._resolve(element.get("dir"))).resolve(strict=False) == stage_path
        ]
        self.assertEqual(1, len(deletes), "runtime stage must be deleted exactly once")
        self.assertEqual(1, len(mkdirs), "runtime stage must be created exactly once")
        self.assertLess(deletes[0], mkdirs[0], "runtime stage must be deleted before it is recreated")

        def transitive_dependencies(target_name: str) -> set[str]:
            seen: set[str] = set()
            pending = [target_name]
            while pending:
                current = pending.pop()
                target = self.targets.get(current)
                if target is None:
                    continue
                for dependency in _split_patterns(target.get("depends")):
                    if dependency not in seen:
                        seen.add(dependency)
                        pending.append(dependency)
            return seen

        for public_target in ("install4j", "release", "release-windows-ci"):
            self.assertIn(public_target, self.targets, f"build.xml is missing target {public_target}")
            self.assertIn(
                stage_name,
                transitive_dependencies(public_target),
                f"{public_target} must recreate the runtime stage before install4j compilation",
            )

    def test_install4j_main_root_is_only_the_runtime_stage(self) -> None:
        mount_points = self.install4j_root.findall("./files/mountPoints/mountPoint")
        main_mounts = [mount.get("id") for mount in mount_points if mount.get("root") is None]
        self.assertEqual(1, len(main_mounts), "install4j must have one main installation mount point")

        entries = self.install4j_root.findall("./files/entries/*")
        main_entries = [entry for entry in entries if entry.get("mountPoint") == main_mounts[0]]
        main_directories = [entry for entry in main_entries if _local_name(entry) == "dirEntry"]
        self.assertEqual(1, len(main_directories), "install4j must have one main runtime dirEntry")

        project_dir = INSTALL4J_PROJECT.parent
        main_source = (project_dir / main_directories[0].get("file")).resolve(strict=False)
        self.assertEqual(
            RUNTIME_STAGE.resolve(strict=False),
            main_source,
            "install4j main dirEntry must be exactly build/install4j-stage",
        )
        for entry in main_entries:
            source = (project_dir / entry.get("file")).resolve(strict=False)
            self.assertTrue(
                source == RUNTIME_STAGE.resolve(strict=False)
                or RUNTIME_STAGE.resolve(strict=False) in source.parents,
                f"install4j main mount entry escapes runtime stage: {entry.get('file')}",
            )

    def test_runtime_allowlist_is_complete_and_fail_closed(self) -> None:
        _, stage_target = self._stage_target()
        selectors = self._stage_selectors(stage_target)

        for selector in selectors:
            top_level = PurePosixPath(selector.include).parts[0]
            self.assertIn(
                top_level,
                ALLOWED_SOURCE_TOP_LEVEL,
                f"runtime-stage include is not an allowed top-level path: {selector.include}",
            )

        missing = sorted(path for path in REQUIRED_RUNTIME_PROBES if not _selected(selectors, path))
        self.assertEqual([], missing, f"runtime-stage allowlist omits required runtime paths: {missing}")

        admitted = sorted(path for path in FORBIDDEN_PROBES if _selected(selectors, path))
        self.assertEqual(
            [],
            admitted,
            "runtime-stage allowlist admits source/VCS/session/credential/generated/unknown paths: "
            f"{admitted}",
        )
        self.assertTrue(
            all(not selector.follows_symlinks for selector in selectors),
            "runtime-stage allowlist permits a symlink escape",
        )


if __name__ == "__main__":
    unittest.main()
