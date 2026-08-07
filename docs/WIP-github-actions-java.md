# WIP: GitHub Actions / Java runtime upgrades

Status: **partial** — SignPath Windows workflow uses current action majors; compile target still Java 21.

## Done (this pass)

| Item | Detail |
|------|--------|
| `actions/checkout` | `@v5` in `.github/workflows/sign-windows-test.yml` |
| `actions/setup-java` | `@v5` (Node 24 runtime; clears setup-java@v4 / Node 20 deprecation) |
| `actions/upload-artifact` | `@v7` for signed installer upload |
| install4j localization | Track `installers/install4j-custom-resources.utf8` so CI media builds succeed |

## Still open

1. **JDK version for CI / `javac`** — project is `javac.source/target=21` (`nbproject/project.properties`). Evaluate Temurin **25** (or latest LTS) for Actions and local builds; update `setup-java` `java-version`, NetBeans properties, and smoke-test `ant compile` / install4j.
2. **Other Actions** — when adding more workflows, prefer Node-24-based majors (`checkout@v5`, `setup-java@v5`, `upload-artifact@v7+`). Third-party actions (install4j setup, SignPath) may lag; pin and revisit.
3. **install4j on CI** — currently pinned to **13.0.2** to match local; bump when upgrading the desktop install4j license install.

## Notes

- GitHub-hosted `windows-latest` already provides Ant; do not reintroduce removed `stCarolas/setup-ant`.
- Media under `installers/<version>/` remains gitignored; only the custom localization `.utf8` is tracked.
