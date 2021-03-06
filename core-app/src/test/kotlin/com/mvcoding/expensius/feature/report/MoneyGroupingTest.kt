/*
 * Copyright (C) 2016 Mantas Varnagiris.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.mvcoding.expensius.feature.report

import com.mvcoding.expensius.anAlwaysOneExchangeRateProvider
import com.mvcoding.expensius.extensions.toInterval
import com.mvcoding.expensius.extensions.toPeriod
import com.mvcoding.expensius.feature.FilterData
import com.mvcoding.expensius.model.Money
import com.mvcoding.expensius.model.NullModels.noTag
import com.mvcoding.expensius.model.ReportGroup.DAY
import com.mvcoding.expensius.model.TransactionState.CONFIRMED
import com.mvcoding.expensius.model.TransactionState.PENDING
import com.mvcoding.expensius.model.TransactionType.EXPENSE
import com.mvcoding.expensius.model.TransactionType.INCOME
import com.mvcoding.expensius.model.aCurrency
import com.mvcoding.expensius.model.aTag
import com.mvcoding.expensius.model.aTransaction
import com.mvcoding.expensius.model.withAmount
import com.mvcoding.expensius.model.withOrder
import com.mvcoding.expensius.model.withTags
import com.mvcoding.expensius.model.withTimestamp
import com.mvcoding.expensius.model.withTransactionState
import com.mvcoding.expensius.model.withTransactionType
import org.hamcrest.CoreMatchers.equalTo
import org.joda.time.Interval
import org.junit.Assert.assertThat
import org.junit.Test
import java.lang.System.currentTimeMillis
import java.math.BigDecimal
import java.math.BigDecimal.ZERO

class MoneyGroupingTest {
    val exchangeRateProvider = anAlwaysOneExchangeRateProvider()
    val moneyGrouping = MoneyGrouping(exchangeRateProvider)

    @Test
    fun groupsAmountsIntoIntervalsThatFitWithinFilterData() {
        val reportGroup = DAY
        val period = reportGroup.toPeriod()
        val interval = reportGroup.toInterval(currentTimeMillis()).withPeriodAfterStart(period.multipliedBy(4))
        val filterData = FilterData(interval, EXPENSE, CONFIRMED)
        val currency = aCurrency()

        val tooEarly = aTransaction().withAmount(10).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.startMillis - 1)
        val notExpense = aTransaction().withAmount(11).withTransactionType(INCOME).withTransactionState(CONFIRMED).withTimestamp(interval.startMillis)
        val notConfirmed = aTransaction().withAmount(12).withTransactionType(EXPENSE).withTransactionState(PENDING).withTimestamp(interval.startMillis)
        val groupA1 = aTransaction().withAmount(13).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.startMillis)
        val groupA2 = aTransaction().withAmount(14).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.startMillis + 1)
        val groupB1 = aTransaction().withAmount(15).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.start.plus(period).millis)
        val groupC1 = aTransaction().withAmount(16).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.endMillis - 1)
        val groupC2 = aTransaction().withAmount(17).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.endMillis - 2)
        val groupC3 = aTransaction().withAmount(18).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.endMillis - 3)
        val tooLate = aTransaction().withAmount(19).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(interval.endMillis)
        val transactions = listOf(tooEarly, notExpense, notConfirmed, groupA1, groupA2, groupB1, groupC1, groupC2, groupC3, tooLate)

        val expectedGroupedAmounts = listOf(
                GroupedMoney(Interval(interval.start, period), Money(BigDecimal.valueOf(27.toDouble()), currency)),
                GroupedMoney(Interval(interval.start.plus(period), period), Money(BigDecimal.valueOf(15.toDouble()), currency)),
                GroupedMoney(Interval(interval.start.plus(period.multipliedBy(2)), period), Money(ZERO, currency)),
                GroupedMoney(Interval(interval.start.plus(period.multipliedBy(3)), period), Money(BigDecimal.valueOf(51.toDouble()), currency))
        )

        val groupedMoney = moneyGrouping.groupToIntervals(transactions, currency, filterData, reportGroup)

        assertThat(groupedMoney, equalTo(expectedGroupedAmounts))
    }

    @Test
    fun groupsAmountsIntoTagsThatFitWithinFilterData() {
        val reportGroup = DAY
        val period = reportGroup.toPeriod()
        val interval = reportGroup.toInterval(currentTimeMillis()).withPeriodAfterStart(period.multipliedBy(4))
        val timestamp = interval.startMillis
        val filterData = FilterData(interval, EXPENSE, CONFIRMED)
        val currency = aCurrency()
        val tagA = aTag()
        val tagB = aTag()
        val tagC = aTag().withOrder(1)
        val tagD = aTag().withOrder(2)
        val noTag = noTag

        val tooEarly = aTransaction().withAmount(10).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(timestamp - 1)
        val notExpense = aTransaction().withAmount(11).withTransactionType(INCOME).withTransactionState(CONFIRMED).withTimestamp(timestamp)
        val notConfirmed = aTransaction().withAmount(12).withTransactionType(EXPENSE).withTransactionState(PENDING).withTimestamp(timestamp)
        val withNoTag = aTransaction().withAmount(13).withTags(emptySet()).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(timestamp)
        val withTagA = aTransaction().withAmount(14).withTags(setOf(tagA)).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(timestamp)
        val withTagB1 = aTransaction().withAmount(15).withTags(setOf(tagB)).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(timestamp)
        val withTagB2 = aTransaction().withAmount(16).withTags(setOf(tagB)).withTransactionType(EXPENSE).withTransactionState(CONFIRMED).withTimestamp(timestamp)
        val withTagCD = aTransaction().withAmount(17).withTags(setOf(tagC, tagD)).withTransactionType(EXPENSE).withTransactionState(CONFIRMED)
                .withTimestamp(timestamp)

        val transactions = listOf(tooEarly, notExpense, notConfirmed, withNoTag, withTagA, withTagB1, withTagB2, withTagCD)

        val expectedGroupedAmounts = listOf(
                GroupedMoney(tagB, Money(BigDecimal.valueOf(31.toDouble()), currency)),
                GroupedMoney(tagC, Money(BigDecimal.valueOf(17.toDouble()), currency)),
                GroupedMoney(tagD, Money(BigDecimal.valueOf(17.toDouble()), currency)),
                GroupedMoney(tagA, Money(BigDecimal.valueOf(14.toDouble()), currency)),
                GroupedMoney(noTag, Money(BigDecimal.valueOf(13.toDouble()), currency))
        )

        val groupedMoney = moneyGrouping.groupToTags(transactions, currency, filterData)

        assertThat(groupedMoney, equalTo(expectedGroupedAmounts))
    }
}