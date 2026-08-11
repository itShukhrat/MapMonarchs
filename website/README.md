# MapMonarchs Trust Center — website

Static, production-grade trust center for **mapmonarchs.com**. No build step, no
framework, no JavaScript. Cloudflare Pages serves clean URLs (`/privacy` from
`privacy.html`), so every route below is public and reachable without auth.

## Routes
| Route | File | Purpose |
|-------|------|---------|
| `/` | `index.html` | Landing + links to the trust pages |
| `/privacy` | `privacy.html` | Privacy policy (the Play **Privacy policy URL**) |
| `/account-deletion` | `account-deletion.html` | The Play **external account-deletion URL** |
| `/terms` | `terms.html` | Terms of Use |
| `/safety` | `safety.html` | Safety & community rules |
| `/support` | `support.html` | Support / help |
| `/contact` | `contact.html` | Contact |
| `/about` | `about.html` | About |

## Before publishing — fill the placeholders
Search for the dashed-orange `[…]` tokens and replace them everywhere:

`[OPERATOR LEGAL ENTITY]`, `[ADDRESS]`, `[JURISDICTION]`, `[DATE]`,
`[CONTACT EMAIL]`, `[DELETION CONTACT EMAIL]`, `[SAFETY CONTACT EMAIL]`, `[N]` (deletion SLA days).

```bash
grep -rn '\[' website/*.html   # list every placeholder to fill
```

## Deploy to Cloudflare Pages (domain already on Cloudflare)
**Option A — Dashboard (no credentials needed here):**
Cloudflare Dashboard → Workers & Pages → Create → Pages → Connect to Git →
select this repo → Framework preset **None**, Build command **(empty)**,
Build output directory **`website`** → Save and Deploy. Then add the custom
domain `mapmonarchs.com` (and `www`) under the Pages project → Custom domains.

**Option B — Wrangler (needs a Cloudflare API token with Pages edit):**
```bash
npx wrangler pages deploy website --project-name mapmonarchs
```

## Verify after deploy
```bash
for p in "" privacy terms safety account-deletion support contact about; do
  curl -sI "https://mapmonarchs.com/$p" | head -1
done   # each must be 200
```
Then set in Play Console → App content:
- **Privacy policy** = `https://mapmonarchs.com/privacy`
- **App content → Data deletion** = `https://mapmonarchs.com/account-deletion`
