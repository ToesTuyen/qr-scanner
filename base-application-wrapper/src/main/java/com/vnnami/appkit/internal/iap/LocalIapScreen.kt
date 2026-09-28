package com.vnnami.appkit.internal.iap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vnnami.appkit.R
import com.vnnami.appkit.internal.iap.CreditPackInfo
import com.vnnami.appkit.internal.iap.IapState
import com.vnnami.appkit.internal.iap.PremiumPlan
import com.vnnami.appkit.internal.iap.SubscriptionCreditAllowance
import com.vnnami.appkit.internal.iap.SubscriptionCreditAllowances
import java.util.Locale

/* Kept local so the shared wrapper does not inherit a host app's theme. */
private val IapBackground = Color(0xFF081127)
private val IapSurface = Color(0xFF101D3A)
private val IapSurfaceMuted = Color(0xFF0C1730)
private val IapPrimary = Color(0xFF7C6CFF)
private val IapCyan = Color(0xFF45D9FF)
private val IapText = Color.White
private val IapTextMuted = Color(0xFFAEB9D2)
private val IapSuccess = Color(0xFF67E7B0)

/**
 * Shared Compose paywall used by host applications. Subscription plans are driven by
 * [IapState], while one-time credit packs are supplied by the host so the wrapper stays
 * catalog-agnostic. [unavailablePlans] keeps a plan visible without allowing a paid flow
 * for an entitlement the host backend cannot grant yet.
 */
@Composable
internal fun LocalIapScreen(
    state: IapState,
    onClose: () -> Unit,
    onPlanSelected: (PremiumPlan) -> Unit,
    onSubscribe: () -> Unit,
    creditPacks: List<CreditPackInfo> = emptyList(),
    subscriptionCredits: SubscriptionCreditAllowances = SubscriptionCreditAllowances(),
    onBuyCreditPack: (packId: String) -> Unit = {},
    onTopUp: (() -> Unit)? = null,
    unavailablePlans: Set<PremiumPlan> = emptySet(),
) {
    val selectedPlanUnavailable = state.selectedPlan in unavailablePlans

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C1B50), IapBackground, IapBackground),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                // The bottom padding clears the pinned CTA below, which floats over this list.
                .padding(start = 20.dp, top = 76.dp, end = 20.dp, bottom = 132.dp),
        ) {
            PaywallHero()

            Spacer(Modifier.height(20.dp))

            if (state.isVip) {
                CurrentPlanBanner(state.currentSubType)
                Spacer(Modifier.height(20.dp))
            }

            Text(
                text = stringResource(R.string.choose_plan),
                color = IapText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            PlanCard(
                plan = PremiumPlan.WEEKLY,
                price = state.weeklyPrice,
                subscriptionCredit = subscriptionCredits.weekly,
                selected = state.selectedPlan == PremiumPlan.WEEKLY,
                unavailable = PremiumPlan.WEEKLY in unavailablePlans,
                onClick = { onPlanSelected(PremiumPlan.WEEKLY) },
            )
            Spacer(Modifier.height(10.dp))
            PlanCard(
                plan = PremiumPlan.MONTHLY,
                price = state.monthlyPrice,
                subscriptionCredit = subscriptionCredits.monthly,
                selected = state.selectedPlan == PremiumPlan.MONTHLY,
                unavailable = PremiumPlan.MONTHLY in unavailablePlans,
                onClick = { onPlanSelected(PremiumPlan.MONTHLY) },
            )
            Spacer(Modifier.height(10.dp))
            PlanCard(
                plan = PremiumPlan.YEARLY,
                price = state.yearlyPrice,
                subscriptionCredit = subscriptionCredits.yearly,
                selected = state.selectedPlan == PremiumPlan.YEARLY,
                unavailable = PremiumPlan.YEARLY in unavailablePlans,
                onClick = { onPlanSelected(PremiumPlan.YEARLY) },
            )

            if (creditPacks.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.credit_packs),
                    color = IapText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.credit_packs_description),
                    color = IapTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                CreditPackGrid(creditPacks = creditPacks, onBuyCreditPack = onBuyCreditPack)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.payment_disclaimer),
                color = IapTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The CTA is pinned, not scrolled. It used to sit inline after the plan cards, which on a
        // phone put it below the fold: the hero and three plans fill the first screen on their own,
        // so the one control the screen exists for was only found by scrolling past it. Floating it
        // over the list keeps the price cards and the button that buys them on screen together.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    // Deliberately thin: enough tint to keep the label readable, not enough to hide
                    // what is behind it. The card the user is scrolling stays visible under the bar,
                    // so the pinned button never reads as the end of the list.
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            IapBackground.copy(alpha = 0.30f),
                            IapBackground.copy(alpha = 0.62f),
                        ),
                    ),
                )
                .navigationBarsPadding()
                // Match the host app's Generate CTA: it uses a 9dp screen gutter.
                .padding(start = 9.dp, end = 9.dp, top = 28.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubscribeButton(
                plan = state.selectedPlan,
                enabled = !selectedPlanUnavailable,
                onClick = onSubscribe,
            )
            if (selectedPlanUnavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.plan_unavailable_message),
                    color = IapTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(IapSurface.copy(alpha = 0.8f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_content_description), tint = IapText)
            }
        }
    }
}

@Composable
private fun PaywallHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF665DDF), Color(0xFF28206F), Color(0xFF14234B)),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // Badge beside the headline rather than stacked above it. Stacked, the banner alone ran
        // most of a phone screen and pushed the plan cards -- the thing being sold -- off the fold.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = IapCyan, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.iap_hero_title),
                color = IapText,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        // The standfirst that stood here ("Unlock premium AI tools, fewer interruptions...") said
        // the same three things as the ticks below it, so it cost height and added nothing.
        FeatureLine(stringResource(R.string.iap_feature_ai_tools))
        FeatureLine(stringResource(R.string.iap_feature_ad_free))
        FeatureLine(stringResource(R.string.iap_feature_restore))
    }
}
@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(IapSuccess.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = IapSuccess, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text = text, color = IapText, fontSize = 13.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CurrentPlanBanner(currentSubType: String?) {
    val planName = when (currentSubType?.lowercase(Locale.ROOT)) {
        "weekly", "weekly_pro" -> stringResource(R.string.plan_weekly)
        "monthly", "monthly_pro" -> stringResource(R.string.plan_monthly)
        "yearly", "annual", "yearly_pro" -> stringResource(R.string.plan_yearly)
        else -> stringResource(R.string.plan_pro)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IapSuccess.copy(alpha = 0.12f))
            .border(1.dp, IapSuccess.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = IapSuccess, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(R.string.iap_current_plan), color = IapTextMuted, fontSize = 12.sp)
            Text(stringResource(R.string.iap_current_plan_name, planName), color = IapText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    price: String,
    subscriptionCredit: SubscriptionCreditAllowance?,
    selected: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val subtitle = when (plan) {
        PremiumPlan.WEEKLY -> stringResource(R.string.billing_weekly)
        PremiumPlan.MONTHLY -> stringResource(R.string.billing_monthly)
        PremiumPlan.YEARLY -> stringResource(R.string.billing_yearly)
    }
    val title = when (plan) {
        PremiumPlan.WEEKLY -> stringResource(R.string.plan_weekly)
        PremiumPlan.MONTHLY -> stringResource(R.string.plan_monthly)
        PremiumPlan.YEARLY -> stringResource(R.string.plan_yearly)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) IapPrimary.copy(alpha = 0.19f) else IapSurfaceMuted)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = when {
                    unavailable -> IapTextMuted.copy(alpha = 0.25f)
                    selected -> IapPrimary
                    else -> Color.White.copy(alpha = 0.10f)
                },
                shape = shape,
            )
            .clickable(enabled = !unavailable, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) IapPrimary else IapTextMuted.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(IapPrimary),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (unavailable) IapTextMuted else IapText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (plan == PremiumPlan.YEARLY) {
                    Spacer(Modifier.width(8.dp))
                    PlanBadge(text = stringResource(R.string.best_value), color = IapCyan)
                }
                if (unavailable) {
                    Spacer(Modifier.width(8.dp))
                    PlanBadge(text = stringResource(R.string.coming_soon), color = IapTextMuted)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, color = IapTextMuted, fontSize = 13.sp)
            subscriptionCredit?.let { allowance ->
                Text(
                    text = stringResource(
                        R.string.subscription_credits_every_days,
                        formatCredits(allowance.credits),
                        (allowance.cycleSeconds / 86_400).coerceAtLeast(1),
                    ),
                    color = IapCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = price,
                color = if (unavailable) IapTextMuted else IapText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = if (unavailable) stringResource(R.string.unavailable) else "", color = IapTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlanBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun SubscribeButton(plan: PremiumPlan, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // The wrapper cannot reference the host app's generated R class at compile time, but the font
    // is part of the merged APK resources. Resolve that exact bundled TTF so Continue and Generate
    // use the same Space Grotesk Medium face rather than merely matching size and weight.
    val continueFont = remember(context) {
        val resourceId = context.resources.getIdentifier(
            "space_grotesk_medium",
            "font",
            context.packageName,
        )
        if (resourceId != 0) {
            FontFamily(Font(resourceId, FontWeight.Medium))
        } else {
            FontFamily.Default
        }
    }
    val planName = when (plan) {
        PremiumPlan.WEEKLY -> stringResource(R.string.plan_weekly)
        PremiumPlan.MONTHLY -> stringResource(R.string.plan_monthly)
        PremiumPlan.YEARLY -> stringResource(R.string.plan_yearly)
    }
    Box(
        modifier = Modifier
            // A paywall action reads better as a focused control than a second full-width bar.
            // `widthIn` is outside `fillMaxWidth`, so it remains responsive on narrow phones.
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            // Same geometry and brand selector as GenerateButton in the host app.
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) SubscribeEnabledBrush else SubscribeDisabledBrush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Just "Continue": the selected plan is already named and highlighted in the card
            // right above, so repeating it on the button only makes the label longer.
            text = if (enabled) stringResource(R.string.continue_text) else stringResource(R.string.plan_coming_soon, planName),
            color = if (enabled) IapText else Color.White.copy(alpha = 0.50f),
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontFamily = continueFont,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val SubscribeEnabledBrush =
    Brush.horizontalGradient(listOf(Color(0xFF8A79FF), Color(0xFF22B8FB)))

private val SubscribeDisabledBrush =
    Brush.horizontalGradient(listOf(Color(0x808A79FF), Color(0x8022B8FB)))

@Composable
private fun CreditPackGrid(
    creditPacks: List<CreditPackInfo>,
    onBuyCreditPack: (String) -> Unit,
) {
    creditPacks.chunked(2).forEachIndexed { rowIndex, rowPacks ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowPacks.forEach { pack ->
                CreditPackCard(
                    pack = pack,
                    onClick = { onBuyCreditPack(pack.packId) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowPacks.size == 1) Spacer(Modifier.weight(1f))
        }
        if (rowIndex < creditPacks.chunked(2).lastIndex) Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CreditPackCard(pack: CreditPackInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(IapSurface)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Text(text = stringResource(R.string.credit_pack), color = IapCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.credits_count, formatCredits(pack.credits)),
            color = IapText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = pack.price, color = IapText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        pack.originalPrice?.let { originalPrice ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = originalPrice,
                color = IapTextMuted,
                fontSize = 12.sp,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(text = stringResource(R.string.buy_credits), color = IapCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatCredits(credits: Int): String = String.format(Locale.US, "%,d", credits)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LocalIapScreenPreview() {
    MaterialTheme {
        LocalIapScreen(
            state = IapState(weeklyPrice = "$14.99", monthlyPrice = "$29.99", yearlyPrice = "$149.99"),
            onClose = {},
            onPlanSelected = {},
            onSubscribe = {},
            unavailablePlans = setOf(PremiumPlan.WEEKLY),
            creditPacks = listOf(
                CreditPackInfo("credit_1", 84, "263.000 ₫"),
                CreditPackInfo("credit_2", 172, "526.000 ₫"),
                CreditPackInfo("credit_3", 350, "1.050.000 ₫"),
                CreditPackInfo("credit_4", 720, "2.100.000 ₫"),
                CreditPackInfo("credit_5", 1_534, "4.200.000 ₫"),
            ),
        )
    }
}
