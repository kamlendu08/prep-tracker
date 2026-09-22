# The Revise tab

Subject → topic → concept cards, for revising away from a desk. The content is the **same** as the
website's `/revise` section: `gate-exam-platform/data/revision/*.json` is the single source of
truth, and the app ships a generated copy of it.

## Why it is bundled, not fetched

The screen exists for the ten minutes in a queue or on a train. Anything that needs a connection to
show a formula defeats the purpose, so the whole section is in the APK — about 2.9 MB, of which
2.7 MB is content and 280 kB is KaTeX's stylesheet and fonts.

## Why the maths is pre-rendered

Compose cannot typeset maths, and flattening fractions, integrals and subscripts into Unicode
produces exactly the half-legible formula that is worse than none when you are checking something
you half-remember. So each topic is laid out as HTML in **one** WebView (not one per card — that
costs a renderer process each and scrolls badly), and KaTeX is run **at build time on the Mac**.
The phone ships the resulting markup plus KaTeX's CSS and fonts, and runs no maths JavaScript at
all.

The only JavaScript on the page is a two-function bridge for remarks.

## Regenerating the assets

After editing anything under `gate-exam-platform/data/revision/`:

```bash
cd ../gate-exam-platform
npm run check:revision              # every expression must compile in KaTeX first
node scripts/build-revision-assets.mjs   # writes ../prep-tracker/app/src/main/assets/
```

It rewrites `app/src/main/assets/revision/` (one file per subject plus `index.json`) and
`app/src/main/assets/katex/`. Commit the result — the assets are checked in so a clone builds
without needing the other repo present.

Only the `.woff2` fonts are copied. KaTeX's stylesheet lists woff2 first and a browser stops at
the first format it supports, so the `.woff` and `.ttf` fallbacks are 800 kB of dead weight in a
2026 WebView.

## Remarks

A note attaches to a **card**, identified by its slug path `subject/topic/card` — the same
identity the website uses, so a note written on the phone appears on the laptop and the other way
round. There is no row to point a foreign key at: revision content ships with the app rather than
living in a table.

- Local: Room table `revision_remarks` (database version 3).
- Server: `RevisionRemark` in the platform's Postgres, and the `revisionRemarks` array on
  `POST /api/mobile/sync`.
- Clearing a note is a **tombstone**, not a delete, on both sides. A row that simply vanished is
  indistinguishable from one the other device never had, and would be pushed straight back on the
  next sync.

Editing happens in a native bottom sheet rather than an HTML textarea — the keyboard, the
selection handles and the back gesture all behave properly that way. The saved text is written
back into the page by `RemarkPage.setRemark`, so the WebView is never rebuilt and the scroll
position survives.
