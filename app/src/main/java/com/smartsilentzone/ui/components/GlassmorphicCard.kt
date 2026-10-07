package com.smartsilentzone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartsilentzone.ui.theme.GlassBackground
import com.smartsilentzone.ui.theme.GlassBorder

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    glowColor: Color = GlassBorder,
    backgroundColor: Color = GlassBackground,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        border = BorderStroke(
            borderWidth,
            Brush.linearGradient(
                listOf(
                    glowColor.copy(alpha = 0.5f),
                    glowColor.copy(alpha = 0.1f),
                    Color.Transparent
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
                .padding(18.dp)
        ) {
            content()
        }
    }
}
