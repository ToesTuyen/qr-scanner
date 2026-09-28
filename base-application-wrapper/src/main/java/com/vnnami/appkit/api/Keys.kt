package com.vnnami.appkit.api

/**
 * API keys, from the KeyVault store instead of from source.
 *
 * The key blob is encrypted and lives on a static CDN; the app carries only the content-key that
 * decrypts it, so no API key ships in source, BuildConfig or local.properties. Rotating a key is
 * done in the KeyVault admin with no app update: on a 401/403 the caller asks for a refresh and
 * retries. A copy of the blob is bundled at `res/raw/keyvault_defaults.json`, so the first call
 * works offline too.
 *
 * Switched on by [AppKitHost.keyVaultAppId]. Left blank, every call here returns null / false and
 * the app runs exactly as if KeyVault were not integrated.
 *
 * Reached as `AppKit.keys`.
 *
 * Turning it on takes three things and no Gradle change in the app:
 *  1. `override val keyVaultAppId = "<app id from the KeyVault admin>"` on the host.
 *  2. `KV_CONTENT_KEY=<base64>` in the ROOT `local.properties` (git-ignored; never commit it).
 *  3. The encrypted blob at `app/src/main/res/raw/keyvault_defaults.json`.
 *
 * ```
 * // Read — straight from RAM, no I/O.
 * val key = AppKit.keys.get() ?: return
 *
 * // A rotated key: on 401/403, ask for a fresh blob and retry once.
 * suspend fun callAi(body: String): Response {
 *     var res = api.post(body, AppKit.keys.get().orEmpty())
 *     if (res.code == 401 && AppKit.keys.onRejected()) {
 *         res = api.post(body, AppKit.keys.get().orEmpty())
 *     }
 *     return res
 * }
 * ```
 * Never log or render a key. And note the vault fails SILENTLY by design — a bad content-key or a
 * missing blob leaves [isReady] false rather than crashing app start, so check it when a key that
 * should be there is not.
 */
interface KeysApi {

    /** True once the vault decrypted its blob. False means the app never turned KeyVault on. */
    val isReady: Boolean

    /** Current value for [name], straight from RAM — no I/O, safe on the main thread. */
    fun get(name: String = "default"): String?

    /**
     * Call when an API answers 401/403: fetches a freshly rotated blob from the CDN.
     * Returns true when a newer config arrived, meaning the request is worth retrying.
     */
    suspend fun onRejected(): Boolean
}
