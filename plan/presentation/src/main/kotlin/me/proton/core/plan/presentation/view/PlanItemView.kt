/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.plan.presentation.view

import android.content.Context
import android.content.res.Resources
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.text.bold
import me.proton.core.plan.presentation.R
import me.proton.core.plan.presentation.databinding.PlanItemBinding
import me.proton.core.plan.presentation.entity.PlanCurrency
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.entity.PlanDetailsListItem
import me.proton.core.presentation.ui.view.ProtonButton
import me.proton.core.presentation.utils.PRICE_ZERO
import me.proton.core.presentation.utils.formatCentsPriceDefaultLocale
import me.proton.core.presentation.utils.onClick
import me.proton.core.util.kotlin.exhaustive

class PlanItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = PlanItemBinding.inflate(LayoutInflater.from(context), this, true)
    private lateinit var selectBtn: ProtonButton

    var planSelectionListener: ((String, String, Double) -> Unit)? = null

    private var billableAmount = PRICE_ZERO
    private lateinit var planName: String
    private lateinit var planDisplayName: String

    lateinit var cycle: PlanCycle
    lateinit var currency: PlanCurrency

    var planDetailsListItem: PlanDetailsListItem? = null
        set(value) {
            value?.let { plan ->
                field = value
                planName = plan.name
                when (plan) {
                    is PlanDetailsListItem.FreePlanDetailsListItem -> {
                        selectBtn = plan.selectButton(context)
                        bindFreePlan(plan)
                    }
                    is PlanDetailsListItem.PaidPlanDetailsListItem -> {
                        selectBtn = plan.selectButton(context)
                        bindPaidPlan(plan)
                    }
                }.exhaustive
                addSelectButtonToView()
            }
        }

    private fun addSelectButtonToView() = with(binding) {
        planGroup.addView(selectBtn)
        val set = ConstraintSet()
        set.clone(planGroup)
        set.connect(selectBtn.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(selectBtn.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.connect(selectBtn.id, ConstraintSet.TOP, planRenewalText.id, ConstraintSet.BOTTOM)
        set.connect(planPriceDescriptionText.id, ConstraintSet.TOP, selectBtn.id, ConstraintSet.BOTTOM)
        set.applyTo(planGroup)
        selectBtn.onClick {
            planSelectionListener?.invoke(planName, planDisplayName, billableAmount)
        }
    }

    private fun bindFreePlan(plan: PlanDetailsListItem.FreePlanDetailsListItem) = with(binding) {
        planDisplayName = context.getString(R.string.plans_free_name)
        planNameText.text = planDisplayName
        if (plan.current) {
            planCycleText.text = context.getString(R.string.plans_current_plan)
        } else {
            planCycleText.visibility = View.GONE
        }
        planPriceDescriptionText.visibility = View.GONE
        planPriceText.visibility = View.GONE
        billableAmount = PRICE_ZERO

        val features = resources.getStringArray(R.array.free)
        binding.planDescriptionText.text = features[0]
        planContents.removeAllViews()
        features.drop(1).forEach { item ->
            planContents.addView(PlanContentItemView(context).apply {
                planItem = item
            })
        }

        if (!plan.selectable) {
            selectBtn.visibility = GONE
        }
    }

    private fun bindPaidPlan(plan: PlanDetailsListItem.PaidPlanDetailsListItem) = with(binding) {
        planDisplayName = plan.displayName
        planNameText.text = planDisplayName

        plan.renewalDate?.let {
            planRenewalText.apply {
                text = SpannableStringBuilder(context.getString(R.string.plans_renewal_date))
                    .bold { append(" $it") }
                visibility = View.VISIBLE
            }
            separator.visibility = View.VISIBLE
        }
        if (plan.current) {
            planCycleText.text = context.getString(R.string.plans_current_plan)
            planPriceText.visibility = View.GONE
        }

        billableAmount = plan.price?.yearly ?: PRICE_ZERO

        val planFeatures = context.getStringArrayByName("plan_id_${plan.name}")
        planFeatures?.let {
            binding.planDescriptionText.text = it[0]
            planContents.removeAllViews()
            it.drop(1).forEach { item ->
                planContents.addView(item.createPlanFeature(context, plan))
            }
        }

        selectBtn.visibility = if (plan.selectable) VISIBLE else GONE
        if (!plan.selectable) {
            selectBtn.visibility = GONE
        }
        if (plan.upgrade) {
            selectBtn.text = context.getString(R.string.plans_upgrade_plan)
        }
        calculateAndUpdatePriceUI()
    }

    private fun calculateAndUpdatePriceUI() = with(binding) {

        planDetailsListItem.let {
            billableAmount = when (it) {
                is PlanDetailsListItem.FreePlanDetailsListItem -> PRICE_ZERO
                is PlanDetailsListItem.PaidPlanDetailsListItem -> {
                    val planPricing = it.price
                    planPricing?.let { price ->
                        cycle.getPrice(price)
                    } ?: PRICE_ZERO
                }
                null -> PRICE_ZERO
            }.exhaustive
        }

        planCycleText.visibility = VISIBLE
        val monthlyPrice: Double = when (cycle) {
            PlanCycle.MONTHLY -> {
                planPriceDescriptionText.visibility = GONE
                billableAmount
            }
            PlanCycle.YEARLY -> {
                planPriceDescriptionText.visibility = VISIBLE
                billableAmount / 12
            }
            PlanCycle.TWO_YEARS -> {
                planPriceDescriptionText.visibility = VISIBLE
                billableAmount / 24
            }
        }.exhaustive.toDouble()

        planPriceText.text = monthlyPrice.formatCentsPriceDefaultLocale(currency.name)
        planPriceDescriptionText.text = String.format(
            context.getString(R.string.plans_billed_yearly),
            billableAmount.formatCentsPriceDefaultLocale(currency.name, fractionDigits = 2)
        )
    }
}

fun Spinner.selected(action: (Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            action(position)
        }
    }
}

private fun Context.getStringArrayByName(aString: String) =
    try {
        resources.getStringArray(resources.getIdentifier(aString, "array", packageName))
    } catch (notFound: Resources.NotFoundException) {
        null
    }
