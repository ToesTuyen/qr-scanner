package com.vnnami.appkit.internal.iap

/** The three subscription tiers the Compose paywall renders, cheapest first. */
internal enum class PremiumPlan { WEEKLY, MONTHLY, YEARLY }

/** Server-owned credit allowance for one subscription refill cycle. */
internal data class SubscriptionCreditAllowance(
    val credits: Int,
    val cycleSeconds: Int,
)

/** Allowances displayed beside the three subscription plans. Hosts should populate this from
 * their own account API rather than bake entitlement amounts into the wrapper. */
internal data class SubscriptionCreditAllowances(
    val weekly: SubscriptionCreditAllowance? = null,
    val monthly: SubscriptionCreditAllowance? = null,
    val yearly: SubscriptionCreditAllowance? = null,
)

/**
 * What the Compose paywall draws. Prices start as placeholders and are replaced once Play answers,
 * so the screen never shows an empty price box while BillingClient is still connecting.
 */
internal data class IapState(
    // Fallback prices mirror BillingRepository placeholders (US tier in skus.py)
    // so the paywall renders the same numbers either before BillingClient resolves
    // real Play prices or when it fails to query the catalog at all.
    // Target US tiers: weekly $9.99, monthly $29.99, yearly $199.99.
    val yearlyPrice: String = "$199.99",
    val monthlyPrice: String = "$29.99",
    val weeklyPrice: String = "$9.99",
    val selectedPlan: PremiumPlan = PremiumPlan.YEARLY,
    /** When true, the paywall switches to "manage / upgrade" mode: a "Your current
     *  plan" banner appears, lower-tier plan cards are hidden, and (when no higher
     *  tier exists) the subscription Continue button disappears entirely. */
    val isVip: Boolean = false,
    /** Active sub SKU (weekly_pro / monthly_pro / yearly_pro) — drives the tier
     *  filter so a monthly_pro user sees only the Yearly upgrade and a yearly_pro
     *  user sees no sub cards at all (already top tier — packs only). */
    val currentSubType: String? = null,
)

/** One-time credit pack rendered inline under the subscription cards. Host apps
 *  fetch these from their own backend (Kiss AI uses CreditsShopViewModel) and pass
 *  the mapped list to LocalIapRoute — the wrapper stays catalog-agnostic. */
internal data class CreditPackInfo(
    val packId: String,
    val credits: Int,
    val price: String,
    val originalPrice: String? = null,
)

/** Everything the user can do on the paywall. */
internal sealed class IapIntent {
    data class SelectPlan(val plan: PremiumPlan) : IapIntent()
    object Subscribe : IapIntent()
    object Close : IapIntent()
}

/** One-shot results the paywall sends back to its host, as opposed to state it renders. */
internal sealed class IapEffect {
    object NavigateBack : IapEffect()
}
