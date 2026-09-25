package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Old content slides out left while the new slides in from the right, both fading. */
fun <S> AnimatedContentTransitionScope<S>.slideSwap(): ContentTransform =
    (fadeIn(tween(150)) + slideInHorizontally(tween(150)) { width -> width / 6 })
        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { width -> -width / 6 })
