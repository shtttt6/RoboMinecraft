# RoboMinecraft

RoboMaster 主题的 Minecraft Fabric 模组，将机甲大师的机器人战斗元素融入 Minecraft 世界。支持机器人驾驶、战斗、升级和多种机器人类型。

## 功能

- **可驾驶机器人载具** — 支持多种机器人类型，包含完整的驾驶操控逻辑
- **机器人战斗系统** — 发射弹丸、伤害判定、战斗状态管理
- **驾驶与操控** — 玩家可进入/离开机器人，控制移动和瞄准
- **机器人升级体系** — 随着使用逐步解锁更强能力
- **HUD 界面** — 驾驶状态下显示机器人状态、瞄准辅助信息
- **规则系统** — 可配置的机器人对战规则
- **自定义模型与规格** — 每种机器人拥有独立的属性配置

## 兼容性

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21.10 |
| Fabric Loader | 0.19.2 |
| Fabric API | 0.138.4+1.21.10 |
| Fabric Language Kotlin | 1.13.10 |
| Java | 21 |

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/`。

## 项目结构

```text
src/main/            服务端逻辑
  ├── RobotVehicleEntity   机器人载具实体
  ├── RobotCombatService   战斗系统（弹丸、伤害）
  ├── RobotPilotService    驾驶操控
  ├── RobotProgressionService  升级系统
  ├── RobotRules           对战规则
  ├── RobotTypes           机器人类型定义
  └── RobotSpecs           机器人属性规格

src/client/          客户端逻辑
  ├── RoboMinecraftClient       客户端入口
  ├── RoboMinecraftClientInput  驾驶输入处理
  ├── RoboMinecraftClientHud    驾驶 HUD 渲染
  └── RoboMinecraftClientAimAndRender  瞄准与渲染
```

## 技术栈

Kotlin、Fabric API、Fabric Language Kotlin、Minecraft 1.21.10
