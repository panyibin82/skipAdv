package com.skipadv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RulesScreen(viewModel: RulesViewModel = viewModel()) {
    val rules by viewModel.rules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("规则", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showAdd = true }) { Text("+ 添加规则") }
        }

        if (rules.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("暂无规则。", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = { "${it.appId}#${it.key}#${it.customStoreId}" }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggle(rule.id) },
                        onDelete = if (rule.isCustom) {
                            { viewModel.deleteCustomRule(rule.customStoreId) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddRuleDialog(
            viewModel = viewModel,
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddRuleDialog(viewModel: RulesViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    Column {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("⚡ 批量：全部应用") },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("单个应用") },
            )
        }
        if (mode == 0) BatchRuleBody(viewModel, onDismiss) else SingleRuleBody(viewModel, onDismiss)
    }
}

/**
 * Batch mode: type a keyword, tap save — a "click anything containing [keyword]"
 * rule is written for EVERY installed app at once.
 */
@Composable
private fun BatchRuleBody(viewModel: RulesViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.apps.collectAsState()
    var keyword by remember { mutableStateOf("跳过") }
    var action by remember { mutableStateOf("click") }
    var message by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("批量添加规则", style = MaterialTheme.typography.titleMedium)
                Text(
                    "输入关键词，一键为全部 ${apps.size} 个已安装应用创建规则。命中含该关键词的按钮时自动执行动作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; message = null },
                    label = { Text("关键词（如：跳过）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = action == "click",
                        onClick = { action = "click" },
                        label = { Text("点击") },
                    )
                    FilterChip(
                        selected = action == "clickCenter",
                        onClick = { action = "clickCenter" },
                        label = { Text("手势点击") },
                    )
                    FilterChip(
                        selected = action == "back",
                        onClick = { action = "back" },
                        label = { Text("按返回") },
                    )
                }

                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val added = viewModel.addKeywordRuleToAll(keyword, action)
                            message = if (added > 0) "已为 $added 个应用添加规则 ✓" else "所有应用都已有这条规则"
                        },
                    ) { Text("一键应用到全部") }
                    if (keyword.isNotBlank()) {
                        val existing = viewModel.countKeywordRules(keyword)
                        if (existing > 0) {
                            TextButton(onClick = {
                                val removed = viewModel.removeKeywordRuleFromAll(keyword)
                                message = "已移除 $removed 条规则"
                            }) { Text("撤销($existing)") }
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun SingleRuleBody(viewModel: RulesViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var keyword by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("click") }
    var rawMode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val filtered = if (query.isBlank()) apps else apps.filter {
        it.name.contains(query, true) || it.packageName.contains(query, true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("添加规则（单个应用）", style = MaterialTheme.typography.titleMedium)

                if (selectedApp == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索应用") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            TextButton(onClick = { selectedApp = app }) {
                                Column {
                                    Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val app = selectedApp
                    Text(
                        "应用：${app?.name ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        app?.packageName ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = {
                            Text(if (rawMode) "选择器（GKD 语法）" else "按钮包含的文字（如：跳过）")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = action == "click",
                            onClick = { action = "click" },
                            label = { Text("点击") },
                        )
                        FilterChip(
                            selected = action == "clickCenter",
                            onClick = { action = "clickCenter" },
                            label = { Text("手势点击") },
                        )
                        FilterChip(
                            selected = action == "back",
                            onClick = { action = "back" },
                            label = { Text("按返回") },
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = rawMode, onCheckedChange = { rawMode = it })
                        Spacer(Modifier.padding(4.dp))
                        Text("高级：自定义选择器语法", style = MaterialTheme.typography.bodySmall)
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val app = selectedApp ?: return@Button
                                val name = app.name
                                val pkg = app.packageName
                                val ok = if (rawMode) {
                                    viewModel.addRawRule(pkg, name, keyword, action)
                                } else {
                                    viewModel.addKeywordRule(pkg, name, keyword, action)
                                }
                                if (ok) onDismiss() else {
                                    error = if (rawMode) "选择器语法有误，请检查" else "请填写关键词"
                                }
                            },
                        ) { Text("保存") }
                        TextButton(onClick = { selectedApp = null; keyword = ""; error = null }) {
                            Text("重选应用")
                        }
                        TextButton(onClick = onDismiss) { Text("取消") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleUi, onToggle: () -> Unit, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (rule.isCustom) "[自定义] " else "") + rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(rule.appId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    rule.matches,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "动作：${rule.action}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(4.dp))
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }
        if (onDelete != null) {
            Row(Modifier.padding(end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
