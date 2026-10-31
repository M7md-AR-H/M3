package com.m3.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.data.PlanRepository
import com.m3.core.data.BodyRepository
import com.m3.core.data.DailyRepository
import com.m3.core.data.FoodRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.StepRepository
import com.m3.core.data.WorkoutRepository
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.Nutrition
import com.m3.core.model.WeekLogs
import com.m3.core.model.WeekReview
import com.m3.core.model.WeeklyReview
import com.m3.core.model.WeightTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

@HiltViewModel
class WeekReviewViewModel @Inject constructor(
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val workouts: WorkoutRepository,
    private val food: FoodRepository,
    private val steps: StepRepository,
    private val body: BodyRepository,
    private val daily: DailyRepository,
) : ViewModel() {
    /** The last finished Monday-to-Sunday week. Null when there's nothing to say, or it couldn't be read. */
    fun lastWeek(today: LocalDate): Flow<WeekReview?> = flow {
        val review = try {
            WeeklyReview.review(logs(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        emit(review)
    }

    private suspend fun logs(monday: LocalDate): WeekLogs {
        val sunday = monday.plusDays(6)
        val profile = profiles.observe().first()
        val plan = plans.observeWeek().first()
        val points = WeightTrend.points(body.observeWeights().first().filter { !it.date.isAfter(sunday) })
        return WeekLogs(
            monday = monday,
            plannedSessions = plan.sessions.size,
            workoutsDone = workouts.observeRecent().first().count { it.workout.date in monday..sunday },
            foodByDay = food.between(monday, sunday).groupBy { it.date }.mapValues { (_, entries) -> Nutrition.totals(entries) },
            targets = profile?.let { Nutrition.targets(it, monday.year, plan.sessions.size) },
            stepsByDay = steps.between(monday, sunday),
            weightKg = points.lastOrNull()?.trendKg,
            weeklyChangeKg = WeightTrend.weeklyChangeKg(points),
            goal = profile?.goal,
            checkIns = daily.checkInsBetween(monday, sunday),
        )
    }
}

/** Last week at a glance, at the top of You. Shows nothing until there's a finished week with logs. */
@Composable
fun WeekReviewCard(today: LocalDate, viewModel: WeekReviewViewModel = hiltViewModel()) {
    val flow = remember(today) { viewModel.lastWeek(today) }
    val review by flow.collectAsStateWithLifecycle(initialValue = null)
    val week = review ?: return
    M3Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Kicker(text = "Weekly review")
            Kicker(text = "Week ${today.minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}")
        }
        Spacer(Modifier.height(M3.spacing.s))
        Text(text = week.headline, style = M3.typography.headline, color = M3.colors.ink)
        Spacer(Modifier.height(M3.spacing.s))
        week.facts.forEach { fact -> Text(text = fact, style = M3.typography.bodySmall, color = M3.colors.muted) }
        Spacer(Modifier.height(M3.spacing.m))
        Kicker(text = "This week", color = M3.colors.accent)
        Text(text = week.advice, style = M3.typography.body, color = M3.colors.ink)
    }
}
