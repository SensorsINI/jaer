# WIP: GitHub Actions / Java runtime upgrades

Status: **partial** — SignPath Windows workflow uses current action majors; compile target still Java 21.

## Done (this pass)

| Item | Detail |
|------|--------|
| `actions/checkout` | `@v5` in `.github/workflows/sign-windows-test.yml` |
| `actions/setup-java` | `@v5` (Node 24 runtime; clears setup-java@v4 / Node 20 deprecation) |
| `actions/upload-artifact` | `@v7` for signed installer upload |
| install4j localization | Track `install4j/install4j-custom-resources.utf8` so CI media builds succeed |

## Still open

1. **JDK 25 installer bundle** — done for **3.3.0** (`install4j/jaer.install4j` `release="25/jdk-25.0.4+7"`; CI `setup-java` 25). `javac` still targets 21. 3.2.0 media stays Temurin 21.
2. **Other Actions** — when adding more workflows, prefer Node-24-based majors (`checkout@v5`, `setup-java@v5`, `upload-artifact@v7+`). Third-party actions (install4j setup, SignPath) may lag; pin and revisit.
3. **install4j on CI** — currently pinned to **13.0.2** to match local; bump when upgrading the desktop install4j license install.

## Notes

- GitHub-hosted `windows-latest` already provides Ant; do not reintroduce removed `stCarolas/setup-ant`.
- Media under `currentInstallers/<version>/` remains gitignored; only the custom localization `.utf8` under `install4j/` is tracked. Historical media is `jaer-older-installers/` (also gitignored).
