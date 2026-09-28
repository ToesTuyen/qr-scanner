package com.vnnami.appkit.internal.iap

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnnami.appkit.internal.billing.BillingRepository
import com.vnnami.appkit.api.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin presentation layer over [BillingRepository]: mirrors prices into [IapState],
 * forwards user intents, and turns a backend-confirmed purchase into a NavigateBack effect.
 * All billing/Google Play work lives in the repository — the VM never touches IAPUtils.
 */
@HiltViewModel
internal class IapViewModel @Inject constructor(
    private val billing: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IapState())
    val uiState: StateFlow<IapState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<IapEffect>()
    val uiEffect: SharedFlow<IapEffect> = _uiEffect.asSharedFlow()

    init {
        billing.init()
        // Mirror repository prices into UI state.
        viewModelScope.launch {
            combine(billing.yearlyPrice, billing.monthlyPrice, billing.weeklyPrice) { y, m, w ->
                Triple(y, m, w)
            }.collect { (yearly, monthly, weekly) ->
                Logger.d("IAP VM: prices mirrored into IapState -> yearly='$yearly', monthly='$monthly', weekly='$weekly'")
                _uiState.update { it.copy(yearlyPrice = yearly, monthlyPrice = monthly, weeklyPrice = weekly) }
            }
        }
        // Dismiss the paywall once a purchase completes.
        viewModelScope.launch {
            billing.purchaseSuccess.collect { _uiEffect.emit(IapEffect.NavigateBack) }
        }
    }

    fun setIntent(intent: IapIntent) {
        when (intent) {
            is IapIntent.SelectPlan -> {
                Logger.d("IAP VM: selectPlan=${intent.plan}")
                _uiState.update { it.copy(selectedPlan = intent.plan) }
            }
            IapIntent.Close -> viewModelScope.launch { _uiEffect.emit(IapEffect.NavigateBack) }
            // Subscribing needs an Activity for the Play billing flow — see subscribe(activity).
            IapIntent.Subscribe -> Unit
        }
    }

    /** Seed the host-known Pro state into UI state on each LocalIapRoute mount so the
     *  paywall can hide already-owned tiers + show the "current plan" banner. */
    fun setProState(isVip: Boolean, subType: String?) {
        _uiState.update { it.copy(isVip = isVip, currentSubType = subType) }
    }

    /** Launches the Play subscription flow for the currently selected plan. */
    fun subscribe(activity: Activity) {
        val basePlanId = when (_uiState.value.selectedPlan) {
            PremiumPlan.WEEKLY -> billing.weeklyBasePlanId
            PremiumPlan.MONTHLY -> billing.monthlyBasePlanId
            PremiumPlan.YEARLY -> billing.yearlyBasePlanId
        }
        Logger.d("IAP VM subscribe: plan=${_uiState.value.selectedPlan}, basePlanId='$basePlanId'")
        billing.subscribe(activity, basePlanId)
    }

    /** Launches a Play one-time purchase for a host-supplied credit pack. */
    fun buyCreditPack(activity: Activity, productId: String) {
        Logger.d("IAP VM buyCreditPack: productId='$productId'")
        billing.buyOneTime(activity, productId)
    }
}
