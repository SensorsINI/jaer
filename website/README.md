# jAER landing page (jaerproject.org)

Static files in this folder are published to **GitHub Pages** by [`.github/workflows/pages.yml`](../.github/workflows/pages.yml). Installer binaries stay on GitHub Releases; the page only links to them.

Local preview (from repo root):

```powershell
python website/bake-latest.py
python -m http.server 8080 --directory website
```

Then open http://127.0.0.1:8080/

`latest.json` is baked at deploy (and by that script). It is gitignored. The in-app updater still uses repo-root `updates.xml`, which can be ahead of the published Latest tag.

## GitHub Pages (once)

1. Repo **Settings → Pages → Build and deployment → Source** = **GitHub Actions**.
   That dropdown is the only Pages setting to change. Do **not** click **Configure** on the
   **GitHub Pages Jekyll** or **Static HTML** cards. Those would add a second starter
   workflow.
2. **Commit and push** `website/` and [`.github/workflows/pages.yml`](../.github/workflows/pages.yml)
   to `master`. Until that push, GitHub has no **Deploy landing page** workflow to run
   (it only exists on this machine).
3. **Actions → Deploy landing page → Run workflow** (or any later push under `website/`).
   The Pages screen stays empty (“Use a suggested workflow…”) until the first deploy
   finishes; that is normal.
4. After a green run, the project URL is https://sensorsini.github.io/jaer/
5. **Custom domain:** `jaerproject.org`. Wait for the DNS check, then **Enforce HTTPS**.

Verify the org domain in **SensorsINI → Settings → Pages → Verified domains** so another repo cannot claim it.

## DNS cutover (registrar)

Today `jaerproject.org` **URL-forwards** to the GitHub repo. Remove that forwarding or it will fight Pages.

Apex `A` records for `@`:

- `185.199.108.153`
- `185.199.109.153`
- `185.199.110.153`
- `185.199.111.153`

Apex `AAAA` for `@`:

- `2606:50c0:8000::153`
- `2606:50c0:8001::153`
- `2606:50c0:8002::153`
- `2606:50c0:8003::153`

`CNAME` `www` → `sensorsini.github.io`

Then in repo Pages settings set the custom domain to `jaerproject.org` (this folder’s `CNAME` file already has that name). TLS can take up to about an hour.

Leave `jaerproject.net` and `jaerproject.ch` as registrar **301 / URL forward → https://jaerproject.org**. GitHub’s `CNAME` file may list only one domain.

Do not change the Authenticode `description-url` (`https://jaerproject.org`).
