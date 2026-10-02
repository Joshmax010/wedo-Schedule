package com.wedo.schedule.ui.screen.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.ui.component.WedoToggle
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 提醒设置页 — V3 重设计（REQ-P5-03 / REQ-P5-04）。
 *
 * 仅保留**每节课前提醒**：主开关 + 课前提醒开关 + 提前分钟数 + 横幅样式。
 * 「每日提醒」（旧 [DailyNotifyReceiver]）与「流体云 / 超级岛」（旧 `FluidCloudService`）
 * 已随 REQ-P5-04 / REQ-P4-04 删除，对应 UI 段一并移除，避免开关空转误导用户。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val colors = WedoTheme.colors
    val context = LocalContext.current

    var masterEnabled by remember { mutableStateOf(AppPrefs.isReminderEnabled(context)) }
    var beforeClassEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassEnabled(context)) }
    var beforeClassMinutes by remember { mutableStateOf(AppPrefs.getBeforeClassMinutes(context)) }
    var minutesInput by remember { mutableStateOf(beforeClassMinutes.toString()) }
    var bannerEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassBannerEnabled(context)) }

    // debounce：分钟输入停止 500ms 后才持久化并重排提醒，
    //   避免每敲一键就触发一次全量 cancelAll + scheduleAll（查库 + 重排全部闹钟）。
    LaunchedEffect(minutesInput) {
        if (minutesInput.isBlank()) return@LaunchedEffect
        delay(500)
        val v = minutesInput.toIntOrNull()?.coerceIn(1, 999) ?: return@LaunchedEffect
        beforeClassMinutes = v
        AppPrefs.setBeforeClassMinutes(context, v)
        WedoApp.get().notificationScheduler.scheduleAll()
    }

    // Permission launcher — NOT one-shot, can be re-triggered by clicking toggle again
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            WedoApp.get().notificationScheduler.scheduleAll()
        } else {
            // Permission denied → revert to off
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13: permission auto-granted at install
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            WedoApp.get().notificationScheduler.scheduleAll()
        }
    }

    fun onMasterToggle(on: Boolean) {
        if (on) {
            // Check if already granted
            val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true

            if (alreadyGranted) {
                masterEnabled = true
                AppPrefs.setReminderEnabled(context, true)
                WedoApp.get().notificationScheduler.scheduleAll()
            } else {
                requestNotificationPermission()
            }
        } else {
            // 关闭 master 只设 reminder_master=false + cancelAll(); scheduleAll 与各 Receiver 均双重检查
            //   isReminderEnabled, 无需覆写子开关(否则重开 master 后 beforeClass 配置全丢)。
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
            // cancelAll 现为 suspend，由 IO 协程调用，避免主线程查库阻塞
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                WedoApp.get().notificationScheduler.cancelAll()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master toggle card
            item {
                ReminderCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconBox(icon = Icons.Outlined.Notifications, color = colors.primary)
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.reminder_master_title),
                                    style = WedoAppleType.body().copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.reminder_master_sub),
                                    style = WedoAppleType.footnote(),
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        WedoToggle(
                            checked = masterEnabled,
                            onCheckedChange = { onMasterToggle(it) }
                        )
                    }
                }
            }

            // Sub-settings — only visible when master is on
            if (masterEnabled) {
                // Before-class reminder
                item {
                    ReminderCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconBox(icon = Icons.Outlined.School, color = colors.primary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_title),
                                        style = WedoAppleType.body().copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_sub),
                                        style = WedoAppleType.footnote(),
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            WedoToggle(
                                checked = beforeClassEnabled,
                                onCheckedChange = { on ->
                                    beforeClassEnabled = on
                                    AppPrefs.setBeforeClassEnabled(context, on)
                                    WedoApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                        }

                        if (beforeClassEnabled) {
                            SubDivider()
                            // Free-input minutes field
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_before_minutes_label),
                                    style = WedoAppleType.subheadline(),
                                    color = colors.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextField(
                                    value = minutesInput,
                                    onValueChange = { txt ->
                                        val digits = txt.filter { it.isDigit() }
                                        if (digits.isEmpty()) {
                                            minutesInput = ""
                                        } else {
                                            val v = digits.toIntOrNull() ?: 0
                                            if (v <= 999) minutesInput = digits
                                        }
                                    },
                                    modifier = Modifier.width(120.dp),
                                    shape = WedoTheme.fieldShape,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    suffix = {
                                        Text(
                                            text = stringResource(R.string.reminder_before_minutes_unit),
                                            style = WedoAppleType.subheadline(),
                                            color = colors.onSurfaceVariant
                                        )
                                    },
                                    colors = WedoTheme.fieldColors()
                                )
                            }
                            SubDivider()
                            Text(
                                text = stringResource(R.string.reminder_before_class_preview),
                                style = WedoAppleType.footnote(),
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            )
                            SubDivider()
                            // 横幅样式：开关「横幅提醒」决定课前通知是否以悬浮横幅（heads-up）呈现。
                            // 后端按此设置选择高优先级 / 普通优先级通知渠道。
                            ReminderToggleRow(
                                title = stringResource(R.string.reminder_banner_title),
                                subtitle = stringResource(R.string.reminder_banner_sub),
                                checked = bannerEnabled,
                                onCheckedChange = {
                                    bannerEnabled = it
                                    AppPrefs.setBeforeClassBannerEnabled(context, it)
                                    WedoApp.get().notificationScheduler.scheduleAll()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
        }
        // 开关本体用共享 WedoToggle，保证与全 app 三个主开关同一观感
        WedoToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReminderCard(content: @Composable () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WedoTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun IconBox(icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    val colors = WedoTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(WedoTheme.shapes.small)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SubDivider() {
    val colors = WedoTheme.colors
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = colors.outline.copy(alpha = WedoTheme.Alpha.hairline)
    )
}
