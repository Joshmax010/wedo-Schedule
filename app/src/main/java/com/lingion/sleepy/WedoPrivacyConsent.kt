package com.lingion.sleepy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WedoPrivacyConsent(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    BackHandler(onBack = onReject)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("隐私与安全说明") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("wedo课程表是吉林建筑大学非官方开源应用。")
                Text("登录在学校官方网页中完成；应用不读取或保存密码。")
                Text("解析后的课表只保存在本机，不上传到项目维护者服务器，也不进入系统云备份。")
                Text("应用不会绕过验证码、证书错误或学校访问控制。")
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("退出") }
        },
    )
}

