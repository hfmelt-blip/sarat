package com.sarat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {

    private val profile = UserProfile(
        sex = Sex.MALE, age = 35, heightCm = 175.0, startWeightKg = 90.0,
        activity = Activity.MODERATE, goals = setOf(DietGoal.INSULIN_RESISTANCE),
        targetWeightKg = 80.0, weeklyRateKg = 0.5, startEpochDay = 0,
    )

    @Test
    fun planMatchesDesignExample() {
        val plan = Nutrition.plan(profile, 90.0, 0)
        assertEquals(2827, plan.tdee)
        assertEquals(550, plan.dailyDeficit)
        assertEquals(2277, plan.dailyTarget)
        assertEquals(20.0, plan.weeksToGoal!!, 0.1)
        assertFalse(plan.limitedByMinimum)
    }

    @Test
    fun targetNeverBelowMinimum() {
        val small = profile.copy(sex = Sex.FEMALE, startWeightKg = 60.0, heightCm = 158.0, age = 50,
            activity = Activity.SEDENTARY, targetWeightKg = 52.0, weeklyRateKg = 1.0)
        val plan = Nutrition.plan(small, 60.0, 0)
        assertEquals(1200, plan.dailyTarget)
        assertTrue(plan.limitedByMinimum)
    }

    @Test
    fun historyStatusesAndStreak() {
        val totals = mapOf(0L to 2480, 1L to 2240, 2L to 2600, 3L to 2260, 4L to 2150, 5L to 2210, 6L to 2190)
        val days = Nutrition.buildHistory(totals, 0, 6, 2280, 1500)
        assertEquals(7, days.size)
        assertEquals(6L, days.first().epochDay)
        assertEquals(DayStatus.OVER, days.first { it.epochDay == 2L }.status)
        assertEquals(DayStatus.ON_TARGET, days.first { it.epochDay == 1L }.status)
        assertEquals(4, Nutrition.currentStreak(days))
    }

    @Test
    fun calibrationLearnsHigherBurn() {
        val intake = (0L until 14L).associateWith { 2340 }
        val c = Nutrition.calibrate(profile, 90.0, 0, 88.9, 14, intake)
        assertTrue(c.reliable)
        assertEquals(605, c.actualDailyDeficit)
        assertTrue(c.tdeeError > 0)
        assertTrue(c.newAdjustment in 1..c.tdeeError)
    }

    @Test
    fun calibrationNeedsEnoughLoggedDays() {
        val intake = mapOf(0L to 2300, 1L to 2200)
        val c = Nutrition.calibrate(profile, 90.0, 0, 89.0, 14, intake)
        assertFalse(c.reliable)
        assertEquals(profile.tdeeAdjustment, c.newAdjustment)
    }

    @Test
    fun idealWeightAndRange() {
        assertEquals(67.5, Nutrition.idealWeight(175.0, 35), 0.01)
        assertEquals(73.5, Nutrition.idealWeight(175.0, 70), 0.01)
        val (low, high) = Nutrition.healthyRange(175.0)
        assertEquals(56.5, low, 0.01)
        assertEquals(76.5, high, 0.01)
        assertEquals(81.0, Nutrition.stagedTarget(90.0), 0.01)
    }
}
