package com.mapmonarchs.domain.model

import androidx.annotation.StringRes
import com.mapmonarchs.R

/**
 * What Pro is, stated as a fixed list rather than a sales pitch.
 *
 * THE ONE RULE THIS LIST ENFORCES: nothing here touches who wins ground. Every
 * benefit is cosmetic, an account convenience, or a richer view of the player's
 * OWN history. None of them makes a claim land faster, a hex hold longer, or a
 * walk count for more. That is not a marketing choice -- it is the same rule the
 * shop's "WHAT WILL NEVER BE SOLD" panel commits to, and it is what lets the
 * game take money without the game being for sale. A benefit that failed this
 * test would not be added here; it would be rejected as pay-to-win.
 *
 * [live] marks whether the benefit can actually be delivered in this build. A
 * profile picture needs avatar storage; premium cosmetics need a catalog. Until
 * those exist the benefit is listed but marked not-yet-active, because promising
 * a capability the app cannot honour is the same fabrication as a fake badge.
 */
data class ProBenefit(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    /** Delivered by this build, versus designed but awaiting backend/storage. */
    val live: Boolean,
)

object ProBenefits {

    /**
     * The full Free-versus-Pro difference. Order is by how visible the benefit
     * is to the player, most tangible first.
     */
    val all: List<ProBenefit> = listOf(
        ProBenefit(R.string.pro_benefit_avatar_title, R.string.pro_benefit_avatar_detail, live = false),
        ProBenefit(R.string.pro_benefit_crest_title, R.string.pro_benefit_crest_detail, live = false),
        ProBenefit(R.string.pro_benefit_frame_title, R.string.pro_benefit_frame_detail, live = false),
        ProBenefit(R.string.pro_benefit_history_title, R.string.pro_benefit_history_detail, live = false),
        ProBenefit(R.string.pro_benefit_support_title, R.string.pro_benefit_support_detail, live = false),
    )

    /** True once ANY benefit can actually be delivered. False in this build. */
    val anyLive: Boolean get() = all.any { it.live }
}
