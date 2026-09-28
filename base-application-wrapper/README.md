# base-application-wrapper

Glue between an app and the binary `base-application` AAR. The AAR is R8-obfuscated, View-based and
carries the whole ad / IAP / language stack; this module turns it into a Compose-shaped API that a
clone can use without ever reading the AAR.

> **Do not edit this module for one app.** It is shared by every app built on it. The single
> exception is `res/values/ads_id.xml`. The full rules are in [CLAUDE.md](CLAUDE.md) beside this
> file — it travels with the directory, so they apply in whatever project the module was copied into.

## Using it in another app

Copy the `base-application-wrapper/` directory in as-is — including `libs/base-application-1.0.0.aar`
— then:

**1. `settings.gradle.kts`** (root). The AAR resolves through a `flatDir` repo, and
`RepositoriesMode.FAIL_ON_PROJECT_REPOS` means it has to be declared here, not in the module:

```kotlin
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        flatDir { dirs(rootDir.resolve("base-application-wrapper/libs")) }
    }
}
include(":app")
include(":base-application-wrapper")
```

**2. `app/build.gradle.kts`** — one dependency. It is `api(...)` inside the module, so the AAR and
its 40 transitive libraries arrive with it; the app declares no ads, billing or Firebase deps of its
own:

```kotlin
implementation(project(":base-application-wrapper"))
```

**3. Three files in the app module** — `AppAdKeys`, `AppHost`, `MyApplication`. The KDoc on
`AppKit` has them side by side; the short version:

```kotlin
object AppAdKeys : AdKeys { const val BANNER_HOME = "banner_home" }

object AppHost : AppKitHost {
    override val appNameRes = R.string.app_name
    override val splashIconRes = R.drawable.ic_splash
    override val homeActivity = MainActivity::class.java
    override val adKeys = AppAdKeys
}

@HiltAndroidApp
class MyApplication : BaseLibApplication() { override val host = AppHost }
```

…plus `android:name=".MyApplication"` in the manifest.

The Activity named as `homeActivity` — and any Activity that shows an ad or opens the paywall —
must extend `androidx.fragment.app.FragmentActivity` (`AppCompatActivity` counts). The AAR still
shows three `DialogFragment`s (`InterAdsDialog` before an interstitial, `FreeTrialDialog` from the
paywall, `UninstallConfirmDialog`), and a DialogFragment needs a FragmentManager that
`ComponentActivity` does not have. The screens themselves stay Compose.

**4. Four config files that are per-app.** Getting one of these wrong is silent, not a build error:

| File | Why it is per-app |
|---|---|
| `app/google-services.json` | Ad unit ids come from this project's Remote Config. A leftover file serves **another app's live ad config**. |
| `app/src/main/assets/default_ads_config.json` | Every `AdKeys` name + every placement key → unit id. Missing or `""` = that slot draws nothing. |
| `base-application-wrapper/res/values/ads_id.xml` | AdMob / Facebook / AppsFlyer / Play license key. The one file inside this module a clone edits. |
| `app/proguard-rules.pro` | The app's own keeps. See [ProGuard / R8](#proguard--r8). |

### Offline POC without Firebase

The normal wrapper bootstrap starts Firebase Remote Config before the first Activity, so a regular
host needs its own `app/google-services.json`. An intentionally offline prototype may instead set
`AppHost.bootstrapMode = AppKitBootstrapMode.LOCAL_ONLY`. This skips the legacy AAR bootstrap and
therefore Firebase, ads SDK, AppsFlyer and AAR-owned notification/billing setup; it is not a
release substitute. The app must use local data and keep those integration paths inactive.

**5. Build both variants before believing it works.** `assembleDebug` passing proves very little —
the ads SDK, billing and KeyVault all fail differently under R8. Run `:app:assembleRelease` too.

### How a key becomes an ad

```
"banner_home"  →  assets/default_ads_config.json  →  Firebase Remote Config  →  AdMob unit id
```

Remote Config wins over the bundled JSON, and a non-blank `AdKeys.dev*UnitId` wins over both. A name
that resolves to `""` produces a slot that measures zero and draws nothing — that is how a placement
is switched off, not by deleting the Composable.

## Two packages, and only one is reachable

```
com.vnnami.appkit
├── api/        public — the app imports this and nothing else
└── internal/   `internal`, so reaching past the facade is a compile error
```

Trying to call an internal from the app fails with `Cannot access 'X': it is internal in file`. That
is the point: the boundary is enforced by the compiler, not by review.

### `api/` — one file per feature

| File | Reached as | Holds |
|---|---|---|
| `AppKit.kt` | — | the facade object; each property points at the file below |
| `Ads.kt` | `AppKit.ads` | `AppBanner`, `AppNativeAd`, `rememberInterstitial`, `rememberRewarded`, `BannerSize`, `NativeSize` |
| `Premium.kt` | `AppKit.premium` | `isPremium`, `refresh`, `set` — entitlement only |
| `Paywall.kt` | `AppKit.paywall` | `Paywall.Base` / `Paywall.Local`, `open`, `gate`, `rememberPremiumGate`, `LocalPaywallRoute` |
| `Billing.kt` | `AppKit.billing` | `start` |
| `Language.kt` | `AppKit.language` | `currentTag`, `wrapContext`, `LanguageRoute` |
| `Keys.kt` | `AppKit.keys` | `isReady`, `get`, `onRejected` — API keys from KeyVault |
| `AppKitHost.kt` | — | the one interface an app implements |
| `AdKeys.kt` | `AppKit.adKeys` | the key names the AAR resolves for itself, plus dev unit-id overrides |
| `BaseLibApplication.kt` | — | the Application base an app extends |
| `Logger.kt` | — | `Logger.d`, public on purpose |

`Logger` is the one public thing outside the facade. It walks the stack to print `(File.kt:line)`,
so routing it through `AppKit` would make every log line point at the facade. Call it directly:

```
Logger.d("Enter HomeScreen")
Logger.d("Click Buy @ HomeScreen", "plan=yearly")   // -> … | Params: plan=yearly
```

### `internal/`

| Package | Holds |
|---|---|
| `ads/` | `AdsManagerImpl` (banner, native, interstitial, rewarded), the Compose slots, `RemoteConfigProvider` |
| `billing/` | `BillingRepository`, `IAPUtils`, `PremiumProvider` |
| `iap/` | the Compose paywall: `IapActivity`, `LocalIapRoute`, `LocalIapScreen`, `IapViewModel`, `IapContract` |
| `language/` | picker screen, ViewModel, repository, `LocaleManager` |
| `util/` | `ContextExt` |
| `AppKitImpl.kt` | the implementations behind the `api` interfaces |

`com.goldenboat.keyvault` sits beside them, keeping its own package because it is a third-party SDK
rather than part of this module.

## Why `AppKitHost` and not overrides

The AAR asks for ~45 configuration hooks. All of them are implemented in `BaseLibApplication` and
marked `final`, so an app cannot override one in passing. When an app genuinely needs a hook changed,
the knob is added to `AppKitHost` — one interface, one place to read, and the change is visible to
every clone instead of hiding in one app's Application class. That is what keeps this module
identical across apps and makes it safe to copy the newer version over an older one.

## The AAR

`base-application` is not a Gradle module. The `.aar` sits in `libs/` and resolves through a
`flatDir` repository declared in the root `settings.gradle.kts`, because AGP rejects a direct local
`.aar` dependency inside an android-library module — which is what this is.

The AAR ships no POM, so **all 40 of its transitive dependencies are re-declared as `api(...)`** in
`build.gradle.kts`. Replacing the AAR means updating that list; get it wrong and it still builds,
then fails at runtime with `NoClassDefFoundError`.

Because that list is hand-maintained, a version in it can silently disagree with what the AAR was
compiled against. One is pinned deliberately:

```kotlin
api("com.android.billingclient:billing:8.0.0")   // do not lower
```

The AAR's `BillingProcessor` implements `ProductDetailsResponseListener` with the 8.x signature
only — `onProductDetailsResponse(BillingResult, QueryProductDetailsResult)`. On 7.x that interface
method does not exist, so the release build fails in R8 *and*, in debug, the product-details callback
never fires and the paywall opens with no prices. 9.1.0 was checked API-compatible (all 70 billing
references in the AAR resolve, listener signatures identical) if Play ever raises the floor.

## `res/values/ads_id.xml` — the one file a clone edits here

AdMob app id, Facebook app id and client token, AppsFlyer dev key, Play licensing public key. They
live here rather than in the app because this module's own manifest and billing code read them by
resource name. As shipped they are the PicMove account's, so leaving them points ad revenue,
attribution and purchase verification at it.

## ProGuard / R8

Two files, and the split matters:

| File | Scope | Holds |
|---|---|---|
| `base-application-wrapper/consumer-rules.pro` | shared — R8 in the app applies it automatically | rules for this module, the AAR and its libraries |
| `app/proguard-rules.pro` | per-app | the app's own class names: Retrofit interfaces, Gson DTOs, anything reflected |

A rule about the wrapper or the AAR that lives in the *app* file has to be copied by hand into every
clone, and a clone that forgets gets a release build that compiles and then misbehaves at runtime.

**As shipped this split is not yet done.** `consumer-rules.pro` is empty, and `app/proguard-rules.pro`
carries both kinds mixed together — the AAR's `BaseActivity`, `com.brian.base_application.language.**`,
the ViewBinding `inflate`/`bind` name keeps, the Facebook mediation keeps and the
`-assumenosideeffects` block that strips `Logger` from release all belong in the consumer file. It
also still names classes from the app this template was cut from, which match nothing here.

`app/proguard-rules.pro` also references `proguard-dictionary.txt` for the obfuscation dictionaries;
copy that file across too, or drop the three `-*obfuscationdictionary` lines.

### KeyVault under R8

Not yet confirmed working in a minified build. `libsodium.so` and `libjnidispatch.so` are packaged
and the native symbol names survive (`-keepclasseswithmembernames class * { native <methods>; }` in
the app rules), but `KeyVaultProvider.init` wraps everything in `runCatching {}.getOrNull()`, so a
link failure is swallowed and the vault silently reports `isReady == false`. Check a release build on
a device before shipping an app that depends on a key.

## Known rough edges

- `BaseLibApplication.getKeyRemoteIntervalShowInterstitial()` feeds `AdKeys.devInterstitialUnitId` to
  the AAR where it expects an interval, so a unit id in that field silently stops interstitials from
  showing. Put an interstitial test id in the app's `default_ads_config.json` instead.
- Four members of `AdKeys` are declared but unread — `openResume`, `nativeLanguage`,
  `privacyPolicyUrl`, `termsOfUseUrl`. Nothing in the wrapper, the app or the AAR looks them up.
  `openResume` is the misleading one: the AAR's resume ad follows `openSplash`.
- The 29 locale files hard-code the word "Pictovio" in notification and paywall copy. Fixing it
  properly needs the AAR to format a `%s`, which a binary cannot be made to do.
- The IAP feature rows and notification strings still describe a document-scanner app, inherited from
  the clone this wrapper was cut from.
