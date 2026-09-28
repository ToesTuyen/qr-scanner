package com.vnnami.appkit.internal.iap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.vnnami.appkit.internal.util.requireActivity

private val defaultCreditPackPurchase: (String) -> Unit = {}
/**
 * Which Play flow is about to open. The host uses it to recognise the next ON_RESUME as a return
 * from billing rather than an ordinary one.
 */
internal enum class IapPurchaseKind { CREDIT_PACK, SUBSCRIPTION }

private val defaultPurchaseStarted: (IapPurchaseKind) -> Unit = {}

@Composable
/**
 * Wires [LocalIapScreen] to [IapViewModel]. Kept apart from the screen so the screen stays a pure
 * function of its state and can be previewed.
 */
internal fun LocalIapRoute(
    onNavigateBack: () -> Unit,
    /** Optional credit packs rendered inline under the subscription cards. Host apps
     *  load these from their own catalog API and pass the mapped list. */
    creditPacks: List<CreditPackInfo> = emptyList(),
    /** Credit allowances loaded by the host from its backend account response. */
    subscriptionCredits: SubscriptionCreditAllowances = SubscriptionCreditAllowances(),
    /** Fires when the user taps "Buy" on a credit pack row. Host wires the Play
     *  INAPP purchase flow (BillingClient launchBillingFlow with the pack's productId). */
    onBuyCreditPack: (packId: String) -> Unit = defaultCreditPackPurchase,
    /** Called immediately before this route launches any Play purchase sheet. Hosts use it
     *  to recognize the following ON_RESUME as a billing return and reclaim the receipt. */
    onPurchaseStarted: (IapPurchaseKind) -> Unit = defaultPurchaseStarted,
    onTopUp: (() -> Unit)? = null,
    /** Pro state from host's source of truth (e.g. CreditsHolder). Drives the
     *  tier-filter — hide already-owned sub cards + show the "current plan" banner. */
    isVip: Boolean = false,
    currentSubType: String? = null,
    /** Visible but non-purchasable plans. Use this while a host backend has not yet
     *  implemented the corresponding entitlement. */
    unavailablePlans: Set<PremiumPlan> = emptySet(),
    viewModel: IapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = remember(context) { context.requireActivity() }

    // Seed the VM with host-known Pro state on each (re)mount + whenever it changes.
    LaunchedEffect(isVip, currentSubType) { viewModel.setProState(isVip, currentSubType) }

    LaunchedEffect(viewModel.uiEffect) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                IapEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    LocalIapScreen(
        state = state,
        onClose = { viewModel.setIntent(IapIntent.Close) },
        onPlanSelected = { viewModel.setIntent(IapIntent.SelectPlan(it)) },
        onSubscribe = {
            onPurchaseStarted(IapPurchaseKind.SUBSCRIPTION)
            viewModel.subscribe(activity)
        },
        creditPacks = creditPacks,
        subscriptionCredits = subscriptionCredits,
        onBuyCreditPack = { productId ->
            onPurchaseStarted(IapPurchaseKind.CREDIT_PACK)
            if (onBuyCreditPack === defaultCreditPackPurchase) {
                viewModel.buyCreditPack(activity, productId)
            } else {
                onBuyCreditPack(productId)
            }
        },
        onTopUp = onTopUp,
        unavailablePlans = unavailablePlans,
    )
}
