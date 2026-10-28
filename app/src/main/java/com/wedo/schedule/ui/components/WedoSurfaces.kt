package com.wedo.schedule.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.glass.wedoGlassSurface
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoGlassLevel
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass
import com.wedo.schedule.ui.theme.WedoTheme

/** iOS inset grouped 卡片：G0 面层、10dp 圆角、左右 14dp。 */
@Composable
fun WedoInsetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .wedoGlassSurface(
                surface = WedoGlassSurfaceClass.Content,
                level = WedoGlassLevel.Card,
                shape = shape,
            ),
        content = content
    )
}

/** 分组页眉：小字灰色，左右 18dp。 */
@Composable
fun WedoSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        style = WedoAppleType.footnote(),
        color = WedoTheme.colors.onSurfaceVariant
    )
}
