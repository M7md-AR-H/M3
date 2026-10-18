package com.m3.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class M3Shapes(val shape: RoundedCornerShape)

fun cornerRadiusToM3Shapes(cornerRadiusDp: Int): M3Shapes = M3Shapes(RoundedCornerShape(cornerRadiusDp.dp))
