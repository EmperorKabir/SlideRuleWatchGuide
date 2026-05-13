# Slide Rule Watch Guide — Play Store closed-testing setup

Step-by-step to produce a signed Android App Bundle (`.aab`) and upload it to
the Play Console as a closed-testing release. Run these once; subsequent
releases reuse the keystore and skip steps 1–3.

## 1. Generate a release keystore (one-time)

From the project root (`C:\Users\Kabir\Slide Rule Watch Guide\`):

```powershell
keytool -genkeypair -v `
  -keystore slide-rule.jks `
  -alias slide-rule-release `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storetype JKS
```

Pick strong passwords; store them in a password manager.
`*.jks` is in `.gitignore` — the file must never be committed.

## 2. Wire the keystore into Gradle

Create `keystore.properties` in the project root (sibling of `build.gradle.kts`):

```properties
storeFile=slide-rule.jks
storePassword=<your store password>
keyAlias=slide-rule-release
keyPassword=<your key password>
```

`keystore.properties` is also in `.gitignore`. The `app/build.gradle.kts`
loads this file at build time; if the file is missing, the release build
falls back to the debug keystore (suitable for local testing but rejected by
the Play Console).

## 3. Build the signed App Bundle

```powershell
./gradlew :app:bundleRelease
```

The signed `.aab` lands at:
`app\build\outputs\bundle\release\app-release.aab`

Inspect the signature:
```powershell
keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
```

## 4. Bump versions on each subsequent release

In `app/build.gradle.kts`:
- `versionCode` — integer, MUST increase by at least 1 every upload to Play.
- `versionName` — semver-ish string shown to users (`"1.0.0"`, `"1.0.1"`, etc.).

## 5. Privacy policy

The privacy policy lives at `docs/privacy-policy.html` and is published via
GitHub Pages. Enable Pages on this repo (`Settings → Pages → Source: main →
folder: /docs`) and the URL becomes:

```
https://emperorkabir.github.io/SlideRuleWatchGuide/privacy-policy.html
```

Paste that URL into the Play Console under
`Policy → App content → Privacy Policy`.

## 6. Play Console closed testing — once-only setup

1. Sign in to https://play.google.com/console/.
2. **Create app** → name `Slide Rule Watch Guide`, default language English (UK),
   App type "App", Free.
3. Complete the **App content** declarations: privacy policy URL (step 5),
   data-safety form (declare: no data collected), content rating questionnaire
   (mark as no sensitive content, suitable for all ages).
4. Under **Testing → Closed testing**, create a track called `closed-alpha`,
   add the tester email list (your own Gmail, plus any reviewers).
5. Upload `app-release.aab` to the closed-alpha track, fill in the
   release notes, and roll out.

## 7. Distribute the tester opt-in link

Play Console gives you an opt-in URL once the track is rolled out. Send that
to your testers; they accept and the app appears in Play Store on their
account.

## Store listing draft (paste into Play Console)

**Title**
> Slide Rule Watch Guide

**Short description (80 char)**
> Learn the chronograph slide-rule bezel. Live readouts; no ads, no tracking.

**Full description**
> Slide Rule Watch Guide is a hands-on tutorial for the circular logarithmic
> slide-rule bezel — the kind printed on classic pilot chronograph watches.
>
> Rotate the bezel with your finger and the on-screen dial recalculates
> live:
>
> • Multiplication and division at any factor of 10
> • Speed / time / distance conversions
> • Miles ↔ kilometres and nautical miles ↔ kilometres
> • Hours / minutes / seconds via the 36 and 60 markers
> • A full set of worked-example chips (×2.5, ×3.5, mi→km, nm→km, Hours)
>
> A plain-English equations panel shows the live answer for every section
> as you turn the bezel.
>
> Fully offline. No accounts, no ads, no analytics, no tracking — see the
> privacy policy for details.

**Category**
> Education
