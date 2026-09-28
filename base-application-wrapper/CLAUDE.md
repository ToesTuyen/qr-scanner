# Rules for agents working with `base-application-wrapper`

This file travels with the module. Whatever project the directory has been copied into, these rules
apply to it — the host app's own CLAUDE.md governs everything else.

## What this module is

Glue between an app and the binary `base-application` AAR. The AAR is R8-obfuscated, View-based and
carries the whole ad / IAP / language stack; this module turns it into a Compose-shaped API the app
can use without ever reading the AAR.

```
com.vnnami.appkit
├── api/        public — the app imports this and nothing else
└── internal/   `internal`, so reaching past the facade is a compile error
```

Read `README.md` next to this file for the integration steps and the AAR details. Every entry point
in `api/` carries usage KDoc with a working snippet, so IDE quick-doc answers most "how do I call
this" questions — do NOT read `internal/` to answer one. If the answer is not in `api/`, the API is
missing something: say so rather than reaching past it.

## 1. Do NOT modify this module

It is **shared**. Every app built on it uses the same copy, so a change made "just for this app"
silently changes behaviour in all of them, and nobody notices until a release goes out.

**Only edit it when the user explicitly asks, that request, that time.** "It would be easier if the
wrapper did X" is not a request. If something is missing, it belongs in the app module. If it
genuinely cannot live there, stop and ask.

The one deliberate exception is `res/values/ads_id.xml` — AdMob app id, Facebook app id and client
token, AppsFlyer dev key, Play licensing public key. They live here because this module's own
manifest and billing code read them by resource name, so editing that file for a new app is
expected. It is the only file in here a clone is meant to touch.

## 2. What the host app owns

Never move any of these into this module:

| Thing | Where it belongs in the app |
|---|---|
| Ad placement keys | the app's `AdKeys` implementation, e.g. `AppAdKeys` |
| Remote Config defaults | `app/src/main/assets/default_ads_config.json` |
| App name, splash icon, home Activity | the app's `AppKitHost` implementation |
| Firebase project | `app/google-services.json` — ad unit ids come from ITS Remote Config |
| KeyVault app id | `AppKitHost.keyVaultAppId` |
| KeyVault content-key | `KV_CONTENT_KEY` in the root `local.properties`, never committed |
| KeyVault key blob | `app/src/main/res/raw/keyvault_defaults.json` |
| The app's own ProGuard keeps | `app/proguard-rules.pro` |

The app implements `AppKitHost`, extends `BaseLibApplication`, and calls `AppKit.*`. That is the
whole seam.

### The host Activity must be a FragmentActivity

`AppKitHost.homeActivity` — and any Activity that shows an ad or opens the paywall — has to extend
`androidx.fragment.app.FragmentActivity` (`AppCompatActivity` counts, it is a subclass).
`ComponentActivity` is NOT enough.

The AAR is not pure Compose: it still shows three `DialogFragment`s, and a DialogFragment needs a
FragmentManager that `ComponentActivity` does not have.

| Dialog | When it appears |
|---|---|
| `com.brian.base_application.dialog.InterAdsDialog` | in front of an interstitial |
| `com.brian.base_iap.iap.FreeTrialDialog` | from the AAR paywall |
| `com.brian.base_application.dialog.UninstallConfirmDialog` | uninstall confirmation |

Nothing about this forces the app's own screens away from Compose — only the Activity base class.

## 3. Logging

`Logger` is public on purpose. Call `Logger.d` **directly at the call site** — never through a
helper. It walks the stack to print `(File.kt:line)`, so any wrapper around it makes every line in
the app point at that wrapper instead.

```
Logger.d("Enter HomeScreen")
Logger.d("Click Buy @ HomeScreen", "plan=yearly")   // -> ... | Params: plan=yearly
```

Shapes: `Enter <Screen>` · `Click <Control> @ <Screen>` · `<Component>: <action>`, extra state in the
second argument. One tag for every app: `adb logcat -s VN_NAMI`.

## 4. Two things that break silently

**Play Billing is pinned to 8.0.0 and must not be lowered.** The AAR's `BillingProcessor` implements
`ProductDetailsResponseListener` with the 8.x signature only, so on 7.x the release build fails in R8
and, in debug, the paywall opens with no prices because the callback never fires.

**The AAR ships no POM**, so its 40 transitive dependencies are hand-declared as `api(...)` in
`build.gradle.kts`. Swapping the AAR means updating that list; get it wrong and it still builds, then
fails at runtime with `NoClassDefFoundError`.

## 5. Before finishing

```
./gradlew :app:assembleDebug      # the minimum bar
./gradlew :app:assembleRelease    # R8, KeyVault and billing all behave differently here
```

Run the release build whenever you touch dependencies, ProGuard rules, KeyVault or anything the AAR
calls. A clean build is not proof it works — say plainly what you verified and what you did not.
