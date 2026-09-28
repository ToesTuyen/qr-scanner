# Rules for agents working in this repo

## 0. Read this first

**BaseTemplate** is the skeleton every VN_NAMI app clone starts from: an empty Compose app already
wired to the `base-application` ad / IAP / language SDK. There is no product here — the value is the
wiring, so the job in this repo is almost always "keep the seam clean", not "add a feature".

```
:app                        the app being built. Package is still ai.picmove.photo.video.editor.
:base-application-wrapper   glue over the base-application AAR. SHARED — see rule 1.
```

`base-application` is not a module: the `.aar` sits in `base-application-wrapper/libs/` and resolves
through a `flatDir` repo in `settings.gradle.kts`. It has no POM, so its 40 transitive dependencies
are hand-declared as `api(...)` in the wrapper.

Read in this order, and stop as soon as you have what you need:

| Read | For | Travels with the module? |
|---|---|---|
| this file | rules for THIS repo. Non-negotiable, and they override convenience. | no |
| `README.md` | what the project is, the clone checklist, how ads resolve, the two paywalls | no |
| `base-application-wrapper/CLAUDE.md` | rules for the wrapper itself — the copy an agent in another project gets | **yes** |
| `base-application-wrapper/README.md` | integrating into another app, the AAR, ProGuard, known rough edges | **yes** |
| KDoc in `base-application-wrapper/.../api/*.kt` | how to actually call each API — every entry point has a usage snippet | **yes** |

The last three are the canonical documentation for the wrapper, and they are inside the module
directory on purpose: copy `base-application-wrapper/` into any project and its rules, its
integration guide and its API docs arrive with it. This file and `README.md` are about BaseTemplate
the repo, and stay behind. So anything an agent must know **wherever the wrapper is used** belongs in
the module's own docs, not here.

Do not read the wrapper's `internal/` to answer a "how do I use X" question. If the answer is not in
`api/`, the API is missing something — say so rather than reaching past it.

### Building

```
./gradlew :app:assembleDebug      # the minimum bar
./gradlew :app:assembleRelease    # R8 + KeyVault + billing all behave differently here
```

Test devices seen in this project: Samsung `R5GL52MCN4A` (has the real PicMove installed — same
package as this template, so installing replaces it), Meizu `511HGDHL222M3` / `511HGDHL223RU`.
Build and install, then hand the device check to the user; do not drive the UI over adb for them.

### State, as of the last session

Working: both build variants, all four ad formats verified on device, the two paywalls, the language
picker, KeyVault in a debug build.

Open, and worth knowing before you start:

- **KeyVault under R8 is unverified.** It fails silently by design, so "no crash" proves nothing.
- **`app/proguard-rules.pro` is dirty** — keeps for classes from an unrelated app, and rules that
  belong in the wrapper's (currently empty) `consumer-rules.pro`.
- **Four `AdKeys` members are dead**: `openResume`, `nativeLanguage`, `privacyPolicyUrl`,
  `termsOfUseUrl`. Nothing reads them; the AAR's resume ad follows `openSplash`.
- **`getKeyRemoteIntervalShowInterstitial()` feeds a unit id where the AAR wants an interval.**
- **The app is still PicMove's identity**: `applicationId`, `google-services.json`, and the ids in
  `ads_id.xml` are all the PicMove account's.

None of those are bugs to fix on your own initiative — they are documented, and several sit inside
the wrapper. Ask.

## 1. Do NOT modify `base-application-wrapper`

`base-application-wrapper` is a **shared module**. Every cloned app uses the same copy, so a change
made "just for this app" silently changes behaviour in all of them, and nobody notices until a
release goes out.

**Only edit it when the user explicitly asks, that request, that time.** "It would be easier if the
wrapper did X" is not a request.

If something is missing, it belongs in the `app` module. If it genuinely cannot live there, stop and
ask the user before touching the wrapper.

The same rule, in the form every other project gets it, is
[`base-application-wrapper/CLAUDE.md`](base-application-wrapper/CLAUDE.md). Keep the two in step:
a rule that should hold wherever the wrapper is used goes in that file, not this one.

## 2. The app talks to the wrapper through one package

The app imports `com.vnnami.appkit.api` and nothing else. Everything under
`com.vnnami.appkit.internal` is `internal`, so a wrong call is a compile error, not a review comment.

Things that belong to the app and must NEVER be moved into the wrapper:

| Thing | Where it goes |
|---|---|
| Ad placement keys | `AppAdKeys` in `app/.../AppHost.kt` |
| Remote Config defaults | `app/src/main/assets/default_ads_config.json` |
| App name, launcher icon, home Activity | `AppHost` |
| KeyVault app id | `AppHost.keyVaultAppId` |
| KeyVault content-key | `KV_CONTENT_KEY` in the root `local.properties` |
| KeyVault key blob | `app/src/main/res/raw/keyvault_defaults.json` |

The one deliberate exception is `base-application-wrapper/res/values/ads_id.xml`: the AdMob,
Facebook, AppsFlyer and Play-licensing ids live there because the wrapper's own manifest and code
read them by resource name. Editing THAT file for a new app is expected — it is the only file in
the wrapper a clone is meant to touch.

## 3. Logging

Call `Logger.d` **directly at the call site** — never through a helper object. `Logger` walks the
stack to print `(File.kt:line)`, so any wrapper around it makes every line point at the wrapper.

```
Logger.d("Enter HomeScreen")
Logger.d("Click Buy @ HomeScreen", "plan=yearly")   // -> ... | Params: plan=yearly
```

Shapes: `Enter <Screen>` · `Click <Control> @ <Screen>` · `<Component>: <action>`, extra state in the
second argument.

## 4. Before finishing

Run `./gradlew :app:assembleDebug`. If you touched dependencies, ProGuard rules, KeyVault or
anything the AAR calls, run `./gradlew :app:assembleRelease` too — those paths only break under R8.

A clean build is the minimum bar, not proof it works. Say plainly what you actually verified and
what you did not.
