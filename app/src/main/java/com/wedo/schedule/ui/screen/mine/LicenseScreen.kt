package com.wedo.schedule.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wedo.schedule.R
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoAppleType

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
 * 这里曾挂着 40+ 条教务协议适配致谢，那些解析器已在重构中删除，条目也一并撤下。
 */
private val thirdPartySources: List<ThirdPartySource> = listOf(
    ThirdPartySource(
        "shiguang_warehouse (XingHeYuZhuan)", "MIT",
        "新版正方课表的网格视图与列表视图（#kbgrid_table_0 / #kblist_table）解析移植自 zhengfang_01.js"
    ),
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
 * 开源许可子页。
 *
 * 三个区块：许可证正文 + 项目来源 + 第三方来源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val colors = WedoTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.license_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LicenseCard {
                    SectionTitle(stringResource(R.string.license_gpl_section))
                    Text(
                        text = stringResource(R.string.license_gpl_body),
                        style = WedoAppleType.subheadline(),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                LicenseCard {
                    SectionTitle(stringResource(R.string.license_origin_section))
                    Text(
                        text = stringResource(R.string.license_origin_body),
                        style = WedoAppleType.subheadline(),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                LicenseCard {
                    SectionTitle(stringResource(R.string.license_third_party_section))
                    thirdPartySources.forEachIndexed { index, src ->
                        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = src.title,
                            style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = src.license,
                            style = WedoAppleType.caption2(),
                            color = colors.primary
                        )
                        Text(
                            text = src.usage,
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = WedoAppleType.body().copy(fontWeight = FontWeight.SemiBold),
        color = WedoTheme.colors.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun LicenseCard(content: @Composable () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}
