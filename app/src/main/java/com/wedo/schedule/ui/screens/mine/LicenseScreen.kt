package com.wedo.schedule.ui.screens.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wedo.schedule.R
import com.wedo.schedule.ui.components.WedoInsetCard
import com.wedo.schedule.ui.components.WedoSectionHeader
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 一条第三方来源记录。项目名与许可证标识保持原文不翻译（通用标识）。
 */
private data class ThirdPartySource(
    val title: String,
    val license: String,
    val usage: String,
)

/**
 * 仍在使用的第三方来源。
 *
 * 规则：只列**当前代码里确实还有其成果**的项目；随对应代码删除而移除。
 */
private val thirdPartySources: List<ThirdPartySource> = listOf(
    ThirdPartySource(
        "FlowCourse (jiaweiyaya)", "GPL-3.0",
        "kbList JSON 字段形态与 jc 多形态的交叉验证依据"
    ),
    ThirdPartySource(
        "zfn_api (openschoolcn)", "MPL-2.0",
        "kbList 接口形态的交叉验证依据"
    ),
    ThirdPartySource(
        "WakeupSchedule_BUPT (dIT8Zv)", "Apache-2.0",
        "教务解析器基类（source 入参 + generateCourseList 契约）的设计参考"
    ),
)

/**
 * 开源许可子页（Batch 2 重构）。
 *
 * 三个区块：许可证正文 + 项目来源 + 第三方来源。
 * 页眉在卡外、inset 卡、行间分隔线；GPL-3.0 归属原文保留。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
    /** 滚动状态由 AppRoot 持有，返回本页时保持原位 */
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = WedoTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.license_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = WedoApple.accentIcon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { WedoSectionHeader(stringResource(R.string.license_gpl_section)) }
            item {
                WedoInsetCard {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.license_gpl_body),
                            style = WedoAppleType.subheadline(),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            item { WedoSectionHeader(stringResource(R.string.license_origin_section)) }
            item {
                WedoInsetCard {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.license_origin_body),
                            style = WedoAppleType.subheadline(),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            item { WedoSectionHeader(stringResource(R.string.license_third_party_section)) }
            item {
                WedoInsetCard {
                    Column(Modifier.padding(14.dp)) {
                        thirdPartySources.forEachIndexed { index, src ->
                            if (index > 0) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 10.dp),
                                    thickness = 0.5.dp,
                                    color = colors.outlineVariant
                                )
                            }
                            Text(
                                text = src.title,
                                style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = src.license,
                                style = WedoAppleType.caption2(),
                                color = WedoApple.accent
                            )
                            Text(
                                text = src.usage,
                                style = WedoAppleType.footnote(),
                                color = colors.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(vertical = 10.dp),
                            thickness = 0.5.dp,
                            color = colors.outlineVariant
                        )
                        Text(
                            text = stringResource(R.string.license_third_party_note),
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
