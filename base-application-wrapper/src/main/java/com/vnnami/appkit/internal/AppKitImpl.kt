package com.vnnami.appkit.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.brian.base_iap.utils.NativeCodecSnowFlakeCortexAI
import com.vnnami.appkit.api.AdsApi
import com.goldenboat.keyvault.KeyVaultProvider
import com.vnnami.appkit.api.BillingApi
import com.vnnami.appkit.api.KeysApi
import com.vnnami.appkit.api.LanguageApi
import com.vnnami.appkit.api.Paywall
import com.vnnami.appkit.api.PaywallApi
import com.vnnami.appkit.api.PremiumApi
import com.vnnami.appkit.internal.ads.AdsManagerImpl
import com.vnnami.appkit.internal.billing.PremiumProvider
import com.vnnami.appkit.internal.iap.IapActivity
import com.vnnami.appkit.internal.language.LocaleManager

/**
 * Wiring behind [com.vnnami.appkit.api.AppKit].
 *
 * Kept out of the api package on purpose: an app binds to the interfaces, never to these, so what
 * sits underneath — the AAR's obfuscated entry points, Hilt, the singleton ad manager — can be
 * replaced without any app noticing.
 */
internal object AdsApiImpl : AdsApi {
    override fun refreshPremiumState(context: Context) {
        AdsManagerImpl.getInstance(context).refreshPremiumStatus()
    }

    override fun showInterstitial(activity: Activity, adKey: String, onComplete: () -> Unit) {
        AdsManagerImpl.getInstance(activity).showInterstitial(activity, adKey, onComplete = onComplete)
    }

    override fun showRewarded(
        activity: Activity,
        adKey: String,
        onRewardEarned: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        AdsManagerImpl.getInstance(activity).showRewarded(
            activity = activity,
            adUnitKey = adKey,
            onRewardEarned = onRewardEarned,
            onAdClosed = onDismissed,
            onAdFailedToShow = onDismissed,
        )
    }
}

/** Entitlement, read through [PremiumProvider] so billing and the ad stack agree on one answer. */
internal object PremiumApiImpl : PremiumApi {
    override fun isPremium(context: Context) = PremiumProvider.isPremium(context)
    override fun refresh(context: Context, onResult: (Boolean) -> Unit) =
        PremiumProvider.refresh(context, onResult)
    override fun set(context: Context, isPremium: Boolean) {
        PremiumProvider.set(context, isPremium)
        // Ad slots cache the entitlement, so tell them the moment it changes.
        AdsApiImpl.refreshPremiumState(context)
    }
}

/**
 * Opens whichever paywall the caller asked for: the AAR's own Activity behind an obfuscated entry
 * point, or this project's Compose screen hosted in [IapActivity]. Both are skipped for a user who
 * is already entitled.
 */
internal object PaywallApiImpl : PaywallApi {

    override fun open(context: Context, paywall: Paywall) {
        if (PremiumProvider.isPremium(context)) return
        when (paywall) {
            // The AAR exposes its paywall through this single obfuscated entry point.
            Paywall.Base -> runCatching { NativeCodecSnowFlakeCortexAI.nativeAiStartIapActivity(context) }
            Paywall.Local -> runCatching {
                val intent = IapActivity.newIntent(context)
                // A non-Activity context (Application, Service) cannot start an Activity in place.
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    override fun gate(context: Context, paywall: Paywall, onPremium: () -> Unit): Boolean {
        val entitled = PremiumProvider.isPremium(context)
        if (entitled) onPremium() else open(context, paywall)
        return entitled
    }
}

/**
 * Starts the Play Billing connection. Reached through Hilt at call time rather than by injection,
 * so an app never has to know the wrapper uses Hilt at all.
 */
internal object BillingApiImpl : BillingApi {
    override fun start(context: Context) {
        runCatching {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, BillingEntryPoint::class.java)
                .billingRepository()
                .init()
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
/** Hilt hatch that hands [BillingApiImpl] the singleton repository from a plain Context. */
internal interface BillingEntryPoint {
    fun billingRepository(): com.vnnami.appkit.internal.billing.BillingRepository
}

/** API keys, delegated to the KeyVault SDK. Every call is a no-op while the vault is not set up. */
internal object KeysApiImpl : KeysApi {
    override val isReady: Boolean get() = KeyVaultProvider.isReady()
    override fun get(name: String): String? = KeyVaultProvider.getKey(name)
    override suspend fun onRejected(): Boolean = KeyVaultProvider.onKeyRejected()
}

/**
 * In-app language. Reads the tag the wrapper's own Language screen persisted, so the app never
 * keeps a second copy of the user's choice.
 */
internal object LanguageApiImpl : LanguageApi {
    override fun currentTag(context: Context): String =
        context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .getString("language_tag", "").orEmpty()

    override fun wrapContext(context: Context, tag: String): Context =
        if (tag.isBlank()) context else LocaleManager.wrapContext(context, tag)
}
