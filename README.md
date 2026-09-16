# skipAdv

复刻"李跳跳"，拥抱 GKD 现代方案的 Android 广告弹窗自动关闭工具。
纯学习/自用项目，不发布、不上市场。

核心链路：**无障碍监听 → GKD 风格选择器匹配 → 自动点击/返回**。
UI 使用 Jetpack Compose。

> ⚠️ 无障碍权限可读取屏幕并模拟操作，是高风险权限，请仅启用自己校验过的可信规则。

## 构建环境（本机已对齐）

- JDK：Android Studio 自带 **JBR 25**（`C:\Program Files\Android\Android Studio\jbr`）——`gradle.properties` 里已通过 `org.gradle.java.home` 指向它
- SDK：`C:\Users\yibin.pan\AppData\Local\Android\Sdk`，compileSdk **37**，minSdk **26**
- Gradle wrapper 9.5.0，AGP 9.3.0，Kotlin 2.2.10，Compose BOM 2026.02.01

## 构建与测试

```bash
# 用 wrapper 构建 debug APK（内部会用 JBR 25）
./gradlew :app:assembleDebug

# 跑选择器/匹配器 JVM 单测
./gradlew :app:testDebugUnitTest

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

## 真机验证步骤

1. `adb install app/build/outputs/apk/debug/app-debug.apk`
2. 打开 app → 首页 → "去开启/去设置"，在系统无障碍设置中启用 `skipAdv 广告跳过服务`
3. 打开一个带"跳过"开屏广告 / 弹窗的 target app（需先在规则里配好它的包名）
4. 观察是否自动点击；`adb logcat -s AdSkipSvc` 查看命中日志

## 如何让某条规则真正生效在你的 app 上

规则只对 `assets/default_rules.json` 里出现的包名生效，其余 app 不受影响（按包名索引，天然安全）。

1. 确认目标 app 包名：`adb shell pm list packages | grep 关键字`
2. 把包名填到 `assets/default_rules.json` 的新 `appId`，补上规则组
3. 保存后在 app 里重进（当前 MVP 启动时读一次规则；改完可重启 app 或重新 install）
4. 用 `adb logcat -s AdSkipSvc` 验证命中

## 选择器语法（GKD 子集）

链式步骤用 `>`（父→子关系），每步可叠加 `[...]` 属性谓词，`@` 标记最终操作节点：

| 写法 | 含义 |
|---|---|
| `[text^="跳过"]` | text 以"跳过"开头 |
| `[text$="跳过"]` | text 以"跳过"结尾 |
| `[text*="跳过"]` | text 包含"跳过" |
| `[text="跳过"]` | text 精确等于 |
| `[text!="跳过"]` | text 不等于 |
| `[text]` | 有 text |
| `[desc=…]` / `[id=…]` | 同理，匹配 content-desc / 资源 id |
| `[clickable]` / `[clickable=true]` | 节点可点击 |
| `TextView[...]` | 类名（按完整类名的简单名后缀匹配） |
| `@LinearLayout[...] > [text="关闭"]` | 匹配子文本"关闭"，但把点击动作作用在父级 LinearLayout 上 |

动作（`action` 字段）：`click`（默认，点击节点）/ `clickNode` / `clickCenter` / `back`（返回键）。

## 架构

```
app/src/main/java/com/skipadv/
├── App.kt                                  # Application，启动时加载内置规则
├── rule/                                   # 引擎（纯 Kotlin，可 JVM 单测）
│   ├── RuleModels.kt                       # AppRule / GroupRule
│   ├── Selector.kt                         # GKD 风格选择器解析
│   ├── Matcher.kt                          # 节点树匹配，返回操作目标
│   └── RuleRepository.kt                   # 从 assets/default_rules.json 加载/索引
├── action/ActionExecutor.kt                # click / clickCenter / back
├── service/AdSkipAccessibilityService.kt   # 无障碍监听-匹配-执行循环（带冷却防抖）
└── ui/                                     # Compose：Home + Rules + MainActivity
```

## 后续版本（当前未做）

远程订阅、规则导入/编辑表单、快照审查、Shizuku 提权、滑动/长按、preKeys 依赖链、正则增强、
规则开关持久化（当前 MVP 为内存态）。

## 参考

- [GKD 官方规则文档](https://gkd.li/)
- [GKD 选择器语法](https://gkd.li/guide/selector)