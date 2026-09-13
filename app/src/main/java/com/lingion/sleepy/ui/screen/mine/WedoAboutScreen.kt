package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WedoAboutScreen(
    onBack: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val colors = SleepyTheme.colors
    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("关于 wedo课程表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("wedo课程表", style = MaterialTheme.typography.headlineMedium)
                    Text("版本 ${BuildConfig.VERSION_NAME}", color = colors.onSurfaceVariant)
                    Text(
                        "面向吉林建筑大学学生的非官方、本地优先 Android 课程表。当前教务导入仍处于真实取证阶段。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().background(
                        colors.surfaceContainer,
                        SleepyTheme.shapes.large,
                    ),
                ) {
                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        label = "GNU GPL v3 与开源许可",
                        onClick = onOpenLicense,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.PrivacyTip,
                        label = "不保存密码，不上传个人课表",
                        onClick = {},
                    )
                }
            }
            item {
                Text(
                    "本项目基于 Sleepy 二次开发，与吉林建筑大学不存在隶属、商业合作或官方授权关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
