Local SignPath / install4j credentials (Dropbox)
================================================

This folder stays on your machine (gitignored except this README).
Do not commit API tokens or license keys.

Fill in these files (one value per file; see stubs created beside this README):

  signpath-organization-id.txt
      SignPath Organization ID (UUID). You may paste the whole project
      overview block; scripts extract the UUID automatically.

  signpath-api-token.txt
      API token from SignPath CI user "CI builds" (Users and Groups →
      CI users → that user → Generate token). Not your personal token.
      Single line, no quotes. GitHub secret SIGNPATH_API_TOKEN must be
      this same value; GitHub has no separate submitter setting.

  install4j-license.txt
      Your install4j license key (single line). Fallback for local Ant and
      this folder's sync script when install4j/license.txt is missing.
      Preferred location: install4j/license.txt (gitignored).

  signpath-project-slug.txt
      Default: jaer

  signpath-signing-policy-slug.txt
      Current CI policy: test-signing2 (workflow hardcodes this).
      Use release-signing later when that policy is VALID.

Sync to GitHub Actions (secrets are set via gh CLI; nothing is committed):

    powershell -File scripts/sync-signpath-secrets-to-github.ps1

Recreate empty stubs if missing:

    powershell -File scripts/init-signpath-local.ps1

GitHub Actions still needs those secrets/vars on the repo (runners cannot
read your Dropbox). The sync script is the bridge; values never live in git.

Tracked artifact config (not secret): .signpath/artifact-configurations/windows-installer-2.xml
See also README-releasing-tagging.md → SignPath Windows CI.
