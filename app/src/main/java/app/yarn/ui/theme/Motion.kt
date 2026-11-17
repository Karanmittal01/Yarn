package app.yarn.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Yarn's motion, modelled on iOS: critically damped springs (no bounce, no overshoot) that settle
 * in about a third of a second, and a soft ease for fades. Everything that moves uses these, so
 * the whole app shares one rhythm: never snappy, never sluggish.
 */
object Motion {
    /** Settles in ~350 ms without bouncing, like UIKit's default spring. */
    private const val STIFFNESS = 420f

    /** UIKit's ease-in-out curve, for fades and colour changes. */
    val ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> spring(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = STIFFNESS, visibilityThreshold = visibilityThreshold)

    val offset: FiniteAnimationSpec<IntOffset> = spring(IntOffset.VisibilityThreshold)
    val size: FiniteAnimationSpec<IntSize> = spring(IntSize.VisibilityThreshold)
    val float: FiniteAnimationSpec<Float> = spring(0.001f)
    val dp: FiniteAnimationSpec<Dp> = spring(Dp.VisibilityThreshold)

    fun <T> fade(durationMs: Int = 260): FiniteAnimationSpec<T> = tween(durationMs, easing = ease)

    // ---- screens: an iOS push. The new screen slides in from the right edge over the old one,
    // which drifts a quarter to the left and dims a little; back reverses it (and follows the
    // predictive back gesture on Android 14+).

    val pushEnter: EnterTransition = slideInHorizontally(offset) { it }
    val pushExit: ExitTransition = slideOutHorizontally(offset) { -it / 4 } + fadeOut(fade(300), targetAlpha = 0.72f)
    val popEnter: EnterTransition = slideInHorizontally(offset) { -it / 4 } + fadeIn(fade(300), initialAlpha = 0.72f)
    val popExit: ExitTransition = slideOutHorizontally(offset) { it }

    // ---- small things appearing and leaving in place.

    val appear: EnterTransition = fadeIn(fade(220)) + scaleIn(float, initialScale = 0.92f)
    val disappear: ExitTransition = fadeOut(fade(180)) + scaleOut(float, targetScale = 0.92f)

    /** Sheets and pages that rise into view. */
    val rise: EnterTransition = fadeIn(fade(240)) + slideInVertically(offset) { it / 10 }
    val sink: ExitTransition = fadeOut(fade(200)) + slideOutVertically(offset) { it / 10 }

    /** Swapping one bar or panel for another (e.g. the selection bar). */
    fun <S> crossfade(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn(fade(220)) togetherWith fadeOut(fade(160))
    }
}
