---
name: energyflow-security
description: 安全检查——API Key 管理、输入校验、数据隐私、敏感信息泄露
---

# EnergyFlow — 安全检查清单

**用途**: 涉及 API Key / 网络请求 / 用户数据 / 外部输入时使用。

## API Key 管理

### DeepSeek API Key
- ✅ 存储在 DataStore `energy_flow_preferences` 中
- ✅ 用户在设置页手动输入，默认空
- ✅ 未配置时 `analyze()` 返回 null，静默降级
- ⚠️ 不要在代码中硬编码 API Key
- ⚠️ 不要在日志中打印 API Key

### 天气 API
- ✅ Open-Meteo 免费，无需 Key
- ✅ 无需特殊安全处理

### 检查清单
- [ ] 新外部 API 需要 Key 吗？
- [ ] Key 存储在 DataStore（不硬编码、不存 SharedPreferences明文）
- [ ] Key 可选（未配置时优雅降级，不崩溃）
- [ ] 错误响应不泄露 Key（不在异常消息里带 URL+Key）

## 输入校验

### SmartInputParser 安全检查
- ✅ 正则优先：大部分输入不触发网络
- ✅ AI 降级：仅解析失败后才调 DeepSeek API
- ⚠️ 用户输入直接传给 AI，没有注入检测

### 潜在的注入风险
```kotlin
// SmartInputParser 把用户输入直接发给 DeepSeek
// 这本身没问题（AI API 不是 SQL 数据库）
// 但要注意：用户可能在备注中写敏感信息
```

### UI 输入安全检查
- [ ] 数值输入校验（非负数、合理范围）
- [ ] 文本输入长度限制（防止超长字符串 OOM）
- [ ] 日期输入校验（有效日期范围）

## 数据隐私

### 本地存储
- ✅ 能耗数据存储在本地 Room 数据库（不联网）
- ✅ 无云端同步、无分析平台上报
- ⚠️ 导出功能将数据写入文件，用户自行管理

### 网络传输
- ✅ 天气查询: 只传坐标和时间范围，不含个人信息
- ✅ 主题分发: 只 GET 请求，不传任何用户数据
- ⚠️ AI 分析: 发送能耗数据和备注到 DeepSeek API（第三方）
  - 备注可能包含个人信息
  - 用户已知风险（设置页配置 Key 时已明确）

### 检查清单
- [ ] 新功能是否收集用户数据？
- [ ] 收集的数据存在哪里？（本地 vs 云端）
- [ ] 数据传输是否加密？（HTTPS — Ktor 默认）
- [ ] 用户是否能删除数据？（已支持清空数据库）

## WebView / External Content
- ❌ 当前无 WebView / 无第三方内容加载
- 如果未来添加：检查 JavaScript 注入、URL 白名单

## 权限
| 权限 | 用途 | 风险 |
|------|------|------|
| INTERNET | 天气/AI/主题 API | 低 |
| CAMERA | OCR 扫表 | 中 — 用户控制 |
| POST_NOTIFICATIONS | 桌面小部件更新 | 低 |

## 安全提交检查清单
```bash
# 检查是否有硬编码的 API Key / Token
grep -rn "sk-[a-zA-Z0-9]\{20,\}" app/src/ shared/src/ --include="*.kt"
grep -rn "api[_-]?key" app/src/ shared/src/ --include="*.kt" -i

# 检查是否有硬编码的密码
grep -rn "password\s*=" app/src/ shared/src/ --include="*.kt" -i
```

## 相关 Skills
- 预检: `energyflow-quick-scan` — 提交前扫描+安全检查
- 提交: `energyflow-commit` — 安全相关修复用 fix type
