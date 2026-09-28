package com.vnnami.appkit.api

/**
 * Selects how [BaseLibApplication] starts the legacy base-application AAR.
 *
 * [STANDARD] is the normal, production path. It starts Firebase Remote Config, the ads SDK,
 * AppsFlyer and the AAR-owned splash/notification setup, so the host must supply its own valid
 * Firebase configuration.
 *
 * [LOCAL_ONLY] is an explicit development/POC mode for a host that intentionally has no cloud
 * configuration. It keeps the public [AppKit] host binding but skips the AAR bootstrap because
 * that bootstrap unconditionally reads Firebase Remote Config before the first Activity appears.
 * It is only appropriate when the app provides local data and keeps ads, Remote Config, AppsFlyer,
 * AAR-owned notifications and billing out of its active runtime paths.
 */
enum class AppKitBootstrapMode {
    STANDARD,
    LOCAL_ONLY,
}
