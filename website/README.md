# PianoScoreFollower project website

A bilingual static introduction website at https://jimmy-xuzimo.github.io/PianoScoreFollower/.

## Preview locally

From the repository root:

```sh
python3 -m http.server 8080 --directory website
```

Open http://localhost:8080. The site needs no build step or package installation. In this simple preview, `privacy-policy.html` is not inside `website/`; preview the policy from the repository root, or copy it into a temporary preview folder alongside the site.

## Deploy

GitHub Settings → Pages → Build and deployment → **GitHub Actions**. The `Deploy project website` workflow publishes `website/` after relevant pushes to `main`, and can also be run manually.

The workflow copies the original root `privacy-policy.html` into the artifact to preserve its existing public URL. The Android app, release APKs and the independent `PianoScoreFollower-Web` repository are not modified by this deployment.

## Edit

- `index.html`: structure, initial Chinese content, links and sharing metadata.
- `app.js`: Chinese/English dictionaries, theme and navigation controls, screenshot tabs, visual illustration.
- `preferences.js`: reads saved preferences before CSS loads to avoid a wrong-theme flash.
- `style.css`: light/dark design tokens and responsive layouts.
- `assets/`: favicon, optimized versions of repository screenshots, cropped score screenshot and PNG social cover.

The screenshot crops and conversions come from `docs/images/`; they are not generated product mockups. Re-export them after updating application screenshots. Screenshots retain their captured UI language and theme regardless of the website language/theme. Update alternate text and translations when replacing them.

The illustration is a fixed visual sequence over an actual score screenshot. It does not synthesize audio, record sound, read a microphone, perform pitch recognition or control the real application. It starts only when clicked, and stops when the document is hidden or the card leaves the viewport. Reduced-motion preferences remove cursor transitions.

First visits follow the system theme and browser language. Manual preferences persist locally. The theme control cycles **System → Light → Dark → System**. The screenshot tabs support arrow keys, Home and End; mobile navigation supports Escape.

## Content and indexing

Feature descriptions refer to the current Android main branch. APK capabilities must be checked against their release notes. MIDI/MusicXML following is distinct from timed PDF/photo scrolling; do not imply optical music recognition or guaranteed recognition accuracy.

Both languages share one URL. Initial HTML is Chinese, while JavaScript updates text, document language and metadata when English is selected. This is not separate bilingual indexing; social crawlers generally read initial metadata.

For a repository/domain change, update canonical and Open Graph URLs. Keep `privacy-policy.html` available at its established address. Source code is BSD 3-Clause; bundled third-party resources retain their original licenses as described in the root README.

## Verify changes

Check narrow mobile, landscape mobile, tablet in both orientations, desktop and large screens. Verify Chinese/English, both themes, system-theme updates, saved preferences, screenshot tabs, illustration play/pause/reset, reduced motion, navigation, release/web links and the existing privacy policy URL. Test under the project subpath as well as a local root.

## Android 1.0 content

The page now documents the native Magenta / Parangonar pipeline, score-constrained prediction, USB MIDI recording, assessment, two-row Jianpu, dark performance mode and bundled open-source notices. It does not imply feature parity with the independent web application. Screenshots are from the 1.0 Android build. Recovery limitations remain explicit. Browser regression: `node tools/verify-website.cjs <URL> <output>` with Playwright and Chrome installed.
