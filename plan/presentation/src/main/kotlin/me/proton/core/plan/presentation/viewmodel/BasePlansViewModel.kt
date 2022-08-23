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

package me.proton.core.plan.presentation.viewmodel

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.presentation.PaymentsOrchestrator
import me.proton.core.payment.presentation.entity.BillingResult
import me.proton.core.payment.presentation.onPaymentResult
import me.proton.core.payment.domain.entity.PaymentMethod
import me.proton.core.payment.domain.entity.SubscriptionCycle
import me.proton.core.payment.presentation.entity.PlanShortDetails
import me.proton.core.plan.domain.entity.PLAN_VENDOR_GOOGLE
import me.proton.core.plan.domain.entity.Plan
import me.proton.core.plan.domain.entity.PlanVendorName
import me.proton.core.plan.presentation.R
import me.proton.core.plan.presentation.entity.PlanCurrency
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.entity.PlanCycle.Companion.toPlanCycle
import me.proton.core.plan.presentation.entity.PlanDetailsItem
import me.proton.core.plan.presentation.entity.PlanPricing
import me.proton.core.plan.presentation.entity.SelectedPlan
import me.proton.core.plan.presentation.view.calculateUsedSpacePercentage
import me.proton.core.presentation.viewmodel.ProtonViewModel
import me.proton.core.user.domain.entity.User
import me.proton.core.usersettings.domain.entity.Organization
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

internal abstract class BasePlansViewModel(private val paymentsOrchestrator: PaymentsOrchestrator) : ProtonViewModel() {

    protected val state = MutableStateFlow<PlanState>(PlanState.Idle)
    val availablePlansState = state.asStateFlow()

    sealed class PlanState {
        object Idle : PlanState()
        object Processing : PlanState()
        sealed class Success : PlanState() {
            data class Plans(
                val plans: List<PlanDetailsItem>,
                val purchaseEnabled: Boolean
            ) : Success()

            data class PaidPlanPayment(val selectedPlan: SelectedPlan, val billing: BillingResult) : Success()
        }

        sealed class Error : PlanState() {
            data class Exception(val error: Throwable) : Error()
            data class Message(@StringRes val message: Int) : Error()
        }
    }

    fun register(context: Fragment) {
        paymentsOrchestrator.register(context)
    }

    protected fun createFreePlan(
        freePlan: Plan
    ): PlanDetailsItem =
        PlanDetailsItem.FreePlanDetailsItem(
            name = freePlan.name,
            displayName = freePlan.title,
            storage = freePlan.maxRewardSpace ?: freePlan.maxSpace,
            members = freePlan.maxMembers,
            addresses = freePlan.maxAddresses,
            calendars = freePlan.maxCalendars,
            domains = freePlan.maxDomains,
            connections = freePlan.maxVPN
        )

    protected fun createCurrentPlan(
        plan: Plan,
        user: User,
        paymentMethods: List<PaymentMethod>,
        organization: Organization?, // there can be no organization for free plans
        endDate: Date?
    ): PlanDetailsItem {
        val autoRenewal = paymentMethods.isNotEmpty() || user.credit >= plan.amount

        val usedMembers = organization?.usedMembers ?: 1
        val usedDomains = organization?.usedDomains ?: 1
        val usedCalendars = organization?.usedCalendars ?: 1

        val usedAddresses = max(organization?.usedAddresses ?: 1, 1)
        val usedVpn = max(organization?.usedVPN ?: 1, 1)

        val maxAddresses = max(organization?.maxAddresses ?: 1, 1)
        val maxMembers = max(organization?.maxMembers ?: 1, 1)
        val maxDomains = max(organization?.maxDomains ?: 1, 1)
        val maxCalendars = max(organization?.maxCalendars ?: 1, 1)
        val maxVpn = max(organization?.maxVPN ?: 1, 1)

        return PlanDetailsItem.CurrentPlanDetailsItem(
            name = plan.name,
            displayName = plan.title,
            price = PlanPricing.fromPlan(plan),
            isAutoRenewal = autoRenewal,
            endDate = endDate,
            cycle = plan.cycle?.let { PlanCycle.map[it] },
            storage = plan.maxSpace,
            members = maxMembers,
            addresses = maxAddresses,
            calendars = maxCalendars,
            domains = maxDomains,
            connections = max(usedVpn, maxVpn),
            usedSpace = user.usedSpace,
            maxSpace = user.maxSpace,
            progressValue = user.calculateUsedSpacePercentage().roundToInt(),
            usedAddresses = usedAddresses,
            usedDomains = usedDomains,
            usedMembers = usedMembers,
            usedCalendars = usedCalendars
        )
    }

    protected fun Plan.toPaidPlanDetailsItem(starred: Boolean) =
        PlanDetailsItem.PaidPlanDetailsItem(
            name = name,
            displayName = title,
            cycle = cycle?.let { PlanCycle.map[it] },
            price = PlanPricing.fromPlan(this),
            storage = maxSpace,
            members = maxMembers,
            addresses = maxAddresses,
            calendars = maxCalendars,
            domains = maxDomains,
            connections = maxVPN,
            currency = PlanCurrency.valueOf(currency!!), // paid plan has to have currency
            starred = starred,
            services = services ?: 0,
            type = type,
            vendorNames = vendorNames.groupByVendorAndCycle()
        )

    fun startBillingForPaidPlan(userId: UserId?, selectedPlan: SelectedPlan, cycle: SubscriptionCycle) {
        with(paymentsOrchestrator) {
            onPaymentResult { result ->
                result?.let { billingResult ->
                    if (billingResult.paySuccess) {
                        viewModelScope.launch {
                            state.emit(PlanState.Success.PaidPlanPayment(selectedPlan, billingResult))
                        }
                    }
                } ?: run {
                    viewModelScope.launch {
                        state.emit(PlanState.Error.Message(message = R.string.plans_payment_error))
                    }
                }
            }
            startBillingWorkFlow(
                userId = userId,
                selectedPlan = PlanShortDetails(
                    name = selectedPlan.planName,
                    displayName = selectedPlan.planDisplayName,
                    subscriptionCycle = cycle,
                    currency = selectedPlan.currency.toSubscriptionCurrency(),
                    services = selectedPlan.services,
                    type = selectedPlan.type,
                    vendorNames = selectedPlan.vendorNames.filterByCycle(cycle.toPlanCycle())
                ),
                codes = null
            )
        }
    }
}

@VisibleForTesting
internal fun Map<AppStore, Map<PlanCycle, String>>.filterByCycle(planCycle: PlanCycle): Map<AppStore, String> {
    return mapNotNull { (appVendor, names: Map<PlanCycle, String>) ->
        val vendorPlanName = names[planCycle] ?: return@mapNotNull null
        appVendor to vendorPlanName
    }.toMap()
}

@VisibleForTesting
internal fun List<PlanVendorName>.groupByVendorAndCycle(): Map<AppStore, Map<PlanCycle, String>> {
    return groupBy { it.vendorName }.mapNotNull { (vendorName, planVendorNames) ->
        val planCycleToName = planVendorNames.mapNotNull { planVendorName ->
            PlanCycle.map[planVendorName.cycle]?.let { it to planVendorName.name }
        }.toMap()
        when (vendorName) {
            PLAN_VENDOR_GOOGLE -> AppStore.GooglePlay to planCycleToName
            else -> null
        }
    }.toMap()
}
