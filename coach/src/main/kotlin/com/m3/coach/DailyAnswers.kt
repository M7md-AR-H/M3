package com.m3.coach

import com.m3.core.model.StepLedger
import com.m3.core.model.Units

/**
 * Plain questions about today's numbers, answered in Kotlin without the model. In the eval it was told "58g left" and
 * still said 53g, and it made up last week's weight, so these never reach it (ADR 0003). Advice stays with the model.
 */
object DailyAnswers {
    private val ASKS_AMOUNT = Regex("\\b(how much|how many|what'?s|what is|left|remaining|so far|still need)\\b")
    private val ASKS_TREND = Regex("\\b(going|trend|trending|losing|gaining|down|up|changing)\\b")
    private val ASKS_ADVICE = Regex("\\b(should i (eat|have)|what can i|what to eat|ideas?|suggest|recipe|why|meal)\\b")

    fun answer(ownerMessage: String, daily: DailyFacts): String? {
        val text = ownerMessage.lowercase()
        if (ASKS_ADVICE.containsMatchIn(text)) return null
        return when {
            Regex("\\bprotein\\b").containsMatchIn(text) && ASKS_AMOUNT.containsMatchIn(text) -> protein(daily)
            Regex("\\b(calories?|kcal)\\b").containsMatchIn(text) && ASKS_AMOUNT.containsMatchIn(text) -> calories(daily)
            Regex("\\bsteps?\\b").containsMatchIn(text) && ASKS_AMOUNT.containsMatchIn(text) -> steps(daily)
            Regex("\\bweight\\b").containsMatchIn(text) && !Regex("\\b(lift|use|kg for|bench|squat)\\b").containsMatchIn(text) &&
                ASKS_TREND.containsMatchIn(text) -> weight(daily)
            else -> null
        }
    }

    private fun protein(daily: DailyFacts): String? = daily.targets?.let { t ->
        val eaten = daily.eaten.proteinG
        val left = t.proteinG - eaten
        val rest = if (left > 0) "so ${Units.grams(left)} to go" else "so you've reached it"
        "You've had ${Units.grams(eaten)} of protein today, out of ${Units.grams(t.proteinG)}, $rest."
    }

    private fun calories(daily: DailyFacts): String? = daily.targets?.let { t ->
        val eaten = daily.eaten.kcal
        val left = t.kcal - eaten
        val rest = if (left >= 0) "so ${Units.kcal(left)} left" else "so ${Units.kcal(-left)} over"
        "You've had ${Units.kcal(eaten)} today, out of ${Units.kcal(t.kcal)}, $rest."
    }

    private fun steps(daily: DailyFacts): String =
        "${Units.count(daily.steps ?: 0)} steps so far today. Your goal is ${Units.count(StepLedger.DAILY_GOAL)}."

    private fun weight(daily: DailyFacts): String {
        val trend = daily.trendKg ?: return "There are no weigh-ins yet. Log your weight on the You tab and I'll track the trend."
        val change = daily.weeklyChangeKg
            ?: return "Your trend is ${Units.oneDecimal(trend)} ${Units.KG}. After a week of weigh-ins I can tell you which way it's moving."
        return "Your trend is ${Units.oneDecimal(trend)} ${Units.KG}, ${Units.signed(change)} ${Units.KG} a week."
    }
}
