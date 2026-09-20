# jAER landing page (jaerproject.org)

Static files in this folder are published to **GitHub Pages** by [`.github/workflows/pages.yml`](../.github/workflows/pages.yml). Installer binaries stay on GitHub Releases; the page only links to them.

Local preview (from repo root):

```powershell
ant website
```

That bakes `latest.json`, serves `website/` at http://127.0.0.1:8080/, and opens the default browser. Ctrl+C stops the server. Another port: `ant website -Djaer.website.port=8081`.

Same steps by hand:

```powershell
python website/bake-latest.py
python -m http.server 8080 --directory website
```

Then open http://127.0.0.1:8080/

`latest.json` is baked at deploy (and by that script). It is gitignored. Stable assets come from GitHub Latest. A nested `prerelease` object is included only when a published rc (not `sample-data-current`) is newer than Latest. The in-app updater still uses repo-root `updates.xml`, which can be ahead of the published Latest tag.

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
   A published product or rc Release (not `sample-data-current`) also
   runs this workflow and rebakes `latest.json` from GitHub Latest plus a newer
   prerelease when one exists. The job always checks out `master` (Pages
   environment protection).
4. After a green run, the project URL is https://sensorsini.github.io/jaer/
5. Still on **Settings → Pages**, **Custom domain** = `jaerproject.org` → **Save**, then
   wait for the DNS check and **Enforce HTTPS**. Do this **before** (or right as) you
   change Swizzonic DNS. With an Actions publishing source, GitHub ignores `website/CNAME`
   until this field is set.

Verify the org domain in **SensorsINI → Settings → Pages → Verified domains** so another repo cannot claim it.

## DNS cutover (Swizzonic)

The old setup was a Darwin / Plesk web host (`ns1.darwin.sui-inter.net` /
`ns2.darwin.sui-inter.net`, A record `94.126.18.110`) plus URL forwarding to GitHub.
The new setup is **DNS A records** so the browser stays on `jaerproject.org` and
GitHub Pages serves the landing page.

Swizzonic shows **Our configurations have been disabled** and has no **Advanced**
editor because the nameservers are still Darwin.

**Start the DNS modification** is a **nameserver switch**, not the A-record editor.
Darwin does not have to “give up” the domain first. Swizzonic is already the
registrar; changing NS from Darwin to Swizzonic is how Darwin stops answering.
After TTL (hours, sometimes a day), **Advanced** appears.

The **Edit DNS** warning is written for the *other* direction: leaving Swizzonic
default DNS for someone else’s NS, which turns off Swizzonic extras (redirect,
hosting DNS). You are **already** on Darwin, so those extras are already off.
Checking the box and **PROCEED** is only useful if the next screen sets
**Swizzonic default DNS** (`dns1.swizzonic.ch` / `dns2.swizzonic.ch`), not Darwin.

Do not proceed until lab IT confirms `mail.jaerproject.org` can break or will be
recreated on Swizzonic. After NS is Swizzonic, add the GitHub A/AAAA/CNAME below;
do not point A records at SWIZZfree hosting.

**Intended end state (easier to self-manage):** Swizzonic nameservers
(`dns1.swizzonic.ch` / `dns2.swizzonic.ch`), then **DNS configuration → Advanced**
in this control panel. Ask lab IT first (mail, other Darwin hosts). Then a
Swizzonic support ticket can move NS and plant the GitHub records.

Paste for IT / Swizzonic:

> Please point jaerproject.org nameservers at Swizzonic (dns1.swizzonic.ch /
> dns2.swizzonic.ch) so we can edit the zone in the Swizzonic control panel.
> Current NS: ns1.darwin.sui-inter.net / ns2.darwin.sui-inter.net (legacy Plesk).
> After the move, set website records to GitHub Pages (no SWIZZ hosting, no
> redirect to github.com): four A + four AAAA on jaerproject.org as in
> https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/managing-a-custom-domain-for-your-github-pages-site
> (185.199.108.153 … 111.153 and 2606:50c0:8000::153 … 8003::153);
> CNAME www → sensorsini.github.io.
> Preserve MX mail.jaerproject.org unless we confirm that mailbox is unused.
> jaerproject.net should URL-forward (301) to https://jaerproject.org only.

Do not turn on SWIZZ hosting for `.org`.

### jaerproject.org (this is the Pages site)

In [controlpanel.swizzonic.ch](https://controlpanel.swizzonic.ch), select **jaerproject.org**
(right-hand domain list), then:

1. Open **Domain & DNS** → **DNS configuration** → **Advanced** → **OK**.
   ([Swizzonic: manage DNS zone](https://swizzonic.support/en/manage-dns-zone/))
2. **Turn off** any redirect / web-forwarding / cPanel Redirect that sends `.org` to
   GitHub. Leave **MX / mail / TXT** alone unless you know they are unused.
3. Replace the apex website records. Swizzonic does not use `@`; the name is
   `jaerproject.org` (no `www`).

   Delete existing **A** (and **AAAA**, if any) for `jaerproject.org` that point at
   Swizzonic or a redirect host. Add **four A** and **four AAAA**:

   | Type | Name | Value |
   |------|------|--------|
   | A | `jaerproject.org` | `185.199.108.153` |
   | A | `jaerproject.org` | `185.199.109.153` |
   | A | `jaerproject.org` | `185.199.110.153` |
   | A | `jaerproject.org` | `185.199.111.153` |
   | AAAA | `jaerproject.org` | `2606:50c0:8000::153` |
   | AAAA | `jaerproject.org` | `2606:50c0:8001::153` |
   | AAAA | `jaerproject.org` | `2606:50c0:8002::153` |
   | AAAA | `jaerproject.org` | `2606:50c0:8003::153` |

4. **www:** add (or edit) **CNAME** name `www` → `sensorsini.github.io`
   (trailing dot if the form requires it: `sensorsini.github.io.`).
5. **Apply**. Propagation is often 1–2 hours (TTL). Then GitHub Pages **Enforce HTTPS**.

Check from PowerShell: `Resolve-DnsName jaerproject.org -Type A` should list those four
GitHub IPs, not a Swizzonic redirect host.

If the address bar still opens `github.com/SensorsINI/jaer`, that is a **cached 301**
from the old URL-forward. Shift+F5 and `chrome://net-internals/#hsts` delete did
**not** clear it here. Chrome: `chrome://settings/clearBrowserData` → **Cached images
and files** (All time) did. Incognito also bypasses it.

### jaerproject.net (and .ch)

Keep **URL forward / 301 → `https://jaerproject.org`**. Do **not** point `.net` at the
GitHub Pages A records. GitHub’s custom domain is only `jaerproject.org`.

Do not change the Authenticode `description-url` (`https://jaerproject.org`).
