package com.vnnami.appkit.internal.ads;

import com.brian.base_iap.utils.FirebaseRemoteConfigUtil;

/**
 * Single access point to the shared {@link FirebaseRemoteConfigUtil} singleton.
 *
 * <p>{@code BaseApplication.onCreate()} (in the base-application AAR) already loads
 * {@code assets/default_ads_config.json} into {@code FirebaseRemoteConfigUtil.getInstance()}
 * and runs the remote fetch against it, so the whole wrapper must read from that exact
 * instance — never a parallel one.
 *
 * <p>This lives in Java because the AAR was shipped R8-obfuscated: its Kotlin companion
 * object is renamed, so {@code FirebaseRemoteConfigUtil.getInstance()} is unresolvable from
 * Kotlin source. Java resolves the {@code @JvmStatic} {@code getInstance()} method directly.
 */
public final class RemoteConfigProvider {

    private RemoteConfigProvider() {}

    public static FirebaseRemoteConfigUtil get() {
        return FirebaseRemoteConfigUtil.getInstance();
    }
}
