# 测试用例目录 (Test Case Catalog)

## SmartInputParserTest (54 cases)

### 模式覆盖
| 模式 | 测试数 | 关键用例 |
|------|--------|---------|
| 1 纯日期头 | 3 | 无值、单位数月日、日期+后续行 |
| 2 日期+时间+数值 | 3 | 带备注、无备注、多空格 |
| 3 日期+紧凑时间 | 1 | HHmm 格式 |
| 4 日期+电表+水表 | 2 | 正序、反序(大值=电) |
| 5 中文时间 | 3 | AM、PM、中文数字 |
| 6 日期+时间+备注 | 1 | 纯备注行 |
| 7 时间+数值 | 2 | 有上下文、无上下文(报错) |
| 7a 时间+电表+水表 | 3 | 带备注、无备注、反序 |
| 8 紧凑时间+数值 | 1 | HHmm 格式 |
| 9 时间+备注 | 1 | 连续多行 |
| 9a 时间+水表标记 | 1 | 水前缀+备注 |
| 9b 时间+燃气标记 | 1 | 气前缀 |
| 10 水表前缀 | 1 | 水+数值 |
| 10b 燃气前缀 | 1 | 气+数值 |
| 11 纯数值 | 5 | 总电、峰电、谷电、水表、峰谷配对 |
| 6b 标记值 | 3 | 全标记、仅电、仅水 |
| 注释行 | 2 | 跳过#行、纯注释 |
| 峰谷提取 | 2 | 从备注提取、清理备注 |

### 极端输入测试
| 用例 | 预期 |
|------|------|
| 乱码输入 | 全部返回 Error |
| 空输入 | 返回空列表 |
| 多空行 | 过滤空行 |
| 超长备注(500字符) | 正常解析 |
| 零值 | 分类为水表 |
| 超大值(999999) | 分类为总电表 |
| 小数值(9310.75) | 精确解析 |
| 无效日期(2.31) | 返回 Error |
| 负数值 | 不匹配正则 |
| 中英混合时间 | 正确转换 |
| Tab字符 | 等同空格 |
| 无效时钟(25.61) | 返回 Error |
| 月份倒挂(14.7) | 返回 Error |
| 值在日期前 | 返回 Error |
| Unicode emoji | 保留原样 |
| 月份边界(12.31, 1.1) | 正确解析 |

### 自适应阈值测试
| 用例 | 预期 |
|------|------|
| 自定义峰电范围 | 9310 从峰电变为总电 |
| 自定义水表上限 | 1500 从总电变为水表 |

---

## CostEngineTest (4 cases)

| 用例 | 输入 | 预期 |
|------|------|------|
| 峰谷分时+阶梯加价 | 300kWh, peak=150, valley=150 | electricTotalCost=140.0 |
| 三档加价生效 | 500kWh, peak=300, valley=200 | electricTotalCost≈277.65 |
| 一档无加价 | 100kWh, peak=60, valley=40 | 无阶梯加价 |
| 水价阶梯 | 25吨, tier1=10, tier2=10, tier3=5 | waterTotalCost=70.0 |

---

## PredictiveAnalyzerTest (20 cases)

### 边界条件
| 用例 | 预期 |
|------|------|
| 空记录 | null |
| 无电表记录 | null |
| 单条电表记录 | null |
| 恰好2条记录 | 正常预测 |

### 数据清洗
| 用例 | 预期 |
|------|------|
| 递减记录被过滤 | 跳过递减点 |
| 全部递减 | null |

### 本月推算
| 用例 | 预期 |
|------|------|
| 本月2+条记录 | 用首尾差值推算 |
| 多条记录取首尾 | 忽略中间点 |
| 零增长 | null |

### 历史回退
| 用例 | 预期 |
|------|------|
| 本月仅1条 | 用最近窗口 |
| 本月无记录 | 用最近5条斜率 |

### 时间边界
| 用例 | 预期 |
|------|------|
| 跨月记录 | 正确分离本月/上月 |
| 月初预测 | daysElapsed=1 |
| 月末预测 | daysRemaining=0 |
| 大跨度(29天) | 正确日均 |

### 天气集成
| 用例 | 预期 |
|------|------|
| 空天气预报 | 等同无预报 |
| 35°C预报 | 增加预测总量 |
| 38°C持续 | 非线性增长(>5%) |

---

## AnomalyDetectorTest (3 cases)

| 用例 | 预期 |
|------|------|
| 读数递减 | 返回警告 |
| 500%日突增 | 返回警告 |
| 正常日增长 | null |

---

## AdaptiveClassifierTest (8 cases)

| 用例 | 预期 |
|------|------|
| 空记录+无缓存 | 返回 DEFAULTS |
| 空记录+有缓存 | 返回缓存值 |
| 纯电记录 | totalElectricMin = avg × 0.85 |
| 电+峰谷 | peakMin/Max = avg ± 15% |
| 纯水记录 | waterMax = max × 1.2 |
| 混合记录 | 电水分别计算 |
| reLearn | 重新计算并缓存 |
| 下限保护 | totalElectricMin ≥ 5000 |

---

## EventImpactAnalyzerTest (9 cases)

| 用例 | 预期 |
|------|------|
| 冰箱事件窗口 | eventDaily=10, nonEvent=5, delta=5 |
| 无标签 | 空列表 |
| 未配对开始 | 开放窗口到末尾 |
| 未配对停止 | 忽略 |
| <3条电表记录 | 空列表 |
| #hashtag 检测 | 提取标签名 |
| 已知电器词 | 匹配电器名 |
| 多事件不同标签 | 返回2个影响 |
| 全空备注 | 空列表 |

---

## WeatherInterpolatorTest (17 cases)

| 用例 | 预期 |
|------|------|
| 空天气 | 空 Map |
| 空目标日期 | 空 Map |
| 全精确匹配 | 原样返回 |
| 两点间线性插值 | 中点值正确 |
| 多缺口插值 | 每天一个值 |
| 目标在已知前 | 用第一个值外推 |
| 目标在已知后 | 用最后一个值外推 |
| 单数据点 | 所有目标用该点 |
| 混合场景 | 精确+插值+外推 |
| 无效温度 | 过滤掉 |
| 全无效 | 空 Map |
| 降水插值 | 线性插值 |
| 降水null | 两端null→null |
| 天气码外推 | 最近邻 |
| 大跨度(1月-12月) | 正确插值 |

---

## 测试覆盖缺口 (Coverage Gaps)

### 当前未覆盖
- [ ] MeterRepository.interpolateGaps
- [ ] MainViewModel.validateAndSave 异常对话框流程

### 已补齐覆盖
- [x] MeterRepository.batchInsert 完整流程（MeterRepositoryTest, 17 cases）
- [x] ChartViewModel 三表切换（ChartViewModelTest, 8 cases）
- [x] DeepSeekRepository API 调用（DeepSeekRepositoryTest, 8 cases）
- [x] ThemeDistRepository CSS 解析（ThemeDistRepositoryTest, 10 cases）
- [x] UserPreferences 计费迁移（UserPreferencesTest, 13 cases）

### 建议新增
- [ ] MeterRepository 批量导入去重测试
- [ ] 日期缺口插值测试
- [ ] CostEngine 零用量边界
- [ ] CostEngine 水电混合计算
- [ ] PredictiveAnalyzer DES 收敛测试（>5 数据点）
