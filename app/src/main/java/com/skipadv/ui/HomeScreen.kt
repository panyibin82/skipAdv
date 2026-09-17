package com.skipadv.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.skipadv.rule.GlobalRule
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(viewModel: RulesViewModel = viewModel()) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val enabled = remember(refresh) { isAccessibilityEnabled(context) }
    val rules by viewModel.rules.collectAsState()
    val appCount = rules.map { it.appId }.distinct().size
    var globalOn by remember { mutableStateOf(GlobalRule.enabled) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("skipAdv", style = MaterialTheme.typography.headlineMedium)
        Text(
            "自动识别并点击广告弹窗的\"跳过/关闭\"按钮。本 app 为学习/自用项目。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (enabled) "正在监视前台界面，命中规则即自动操作。" else "前往系统设置开启无障碍服务后，本 app 才能工作。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("去开启/去设置") }
                    OutlinedButton(onClick = { refresh++ }) { Text("刷新") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("全局兜底规则", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "所有应用切换后 10 秒内，自动点击\"跳过\"及 close/skip 关闭按钮",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = globalOn,
                        onCheckedChange = {
                            globalOn = it
                            GlobalRule.setEnabled(context, it)
                        },
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("内置规则：${rules.size} 条", style = MaterialTheme.typography.titleMedium)
                Text(
                    "已加载 $appCount 个应用的规则。仅对这些应用的界面生效，其余 app 不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            "注：无障碍权限可读取屏幕并模拟操作，请仅启用可信规则。",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as android.view.accessibility.AccessibilityManager
    return am.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    ).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}