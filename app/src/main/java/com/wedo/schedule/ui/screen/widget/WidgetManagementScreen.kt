package com.wedo.schedule.ui.screen.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.R
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.widget.PlacedWidgetItem
import com.wedo.schedule.widget.WidgetManagementViewModel

/**
 * Lists every wedo widget instance currently placed on the home screen,
 * across all 9 variants. Tapping a row opens [WidgetEditScreen] for that
 * widget id, where the user can pick a per-widget schedule binding.
 *
 * Empty-state copy tells the user how to add a widget — the actions live
 * entirely in the system launcher / home screen, not in our app.
 */
@Composable
fun WidgetManagementScreen(
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val vm = remember { WidgetManagementViewModel() }
    val items by vm.state.collectAsState()
    val colors = WedoTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.widget_manage_empty),
                        style = WedoAppleType.body(),
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.widgetId }) { item ->
                        PlacedWidgetRow(item = item, onClick = { onSelect(item.widgetId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacedWidgetRow(item: PlacedWidgetItem, onClick: () -> Unit) {
    val colors = WedoTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.variant.displayNameRes),
                    style = WedoAppleType.body(),
                    color = colors.onSurface
                )
                val tableLabel = item.tableName
                    ?: stringResource(R.string.widget_edit_default_label)
                Text(
                    text = tableLabel,
                    style = WedoAppleType.subheadline(),
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
