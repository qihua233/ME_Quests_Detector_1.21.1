# 更新日志

## 0.4.2

### 修复
- **右键离线检测器无任何提示**:此前 `DetectorBlock.useItemOn` 在 `POWERED=false` 时直接返回 SUCCESS,不发送动作栏文案。现在补齐使用已有的 `ae2-ftbquests-detector.detector.uncharged`("设备离线" / "Device Offline")提示,与无主队伍、队伍不存在、网络冲突等其它失败状态保持一致(没装 Jade 的玩家也能感知)。

### 说明
- 复查确认:Jade 显示"设备离线"或"网络冲突"时,检测器的所有任务推进入口(`detectTask` / `performFullDetectionInternal` / `tick`)均已早退,核心工作链确实是切断的。

## 0.4.1

### 新增
- **同一 ME 网络冲突检测**:仿照 AE2 ME 控制器,同一 ME 网络中只允许存在一个 ME 任务检测器。当出现多个检测器时,所有冲突节点会自动停止工作并提示玩家。
  - 冲突状态会展示在 Jade tooltip 中
  - 右键冲突方块会在动作栏显示"§c错误:同一 ME 网络中只允许存在一个 ME 任务检测器"

### 优化
- **任务前置 / 完成 / 锁定状态过滤**:新增二级缓存 `activeTasksByKey`,仅保存当前已解锁且未完成的任务。
  - 高频检测路径不再每 tick 对所有任务重复调用 `canStartTasks`(该方法会沿 FTB Quests 依赖图向上递归)
  - 已完成任务直接从扫描循环中剔除
  - 缓存失效由 `TeamData.markDirty` 通过 Mixin 推送,懒重建,失效成本极低
- **POWERED 方块状态**:仅在状态实际改变时调用 `level.setBlock(...)`,避免无意义的客户端区块重建
- **TeamDataMixin**:不再为每个 `TeamData` 实例增加 `@Unique` 字段;并通知该队伍下的全部检测器,不再只通知第一个
- **DetectorEntityList.copyForTeam**:减少一次中间 List 分配
- 移除 `markCacheDirty()` 中无效的 `grid.getTickManager().wakeDevice(...)` 调用

### 修复
- 修复配置界面中"队伍名称显示模式"枚举值未本地化的问题(实现 `TranslatableEnum` 接口)
- 修复处于网络冲突状态的检测器右键时未正确显示冲突提示(冲突判断已前置到 `POWERED` 检查之前)

### 杂项
- 删除已弃用的 `src/main/resources/META-INF/mods.toml`(NeoForge 1.21 改用 `neoforge.mods.toml`)
- 版本号:0.4.0 → 0.4.1
