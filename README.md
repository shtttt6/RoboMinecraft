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

## 快速开始

1. 进入世界后先用 `/robomc on` 开启机器人模式。
2. 按 `K` 打开机器人选择界面，选择英雄、步兵或无人机。
3. 按 `P` 打开当前机器人的配置界面。
4. 空手时使用鼠标左键射击，按住鼠标右键启用自动瞄准。
5. 需要购弹时按 `I` 或 `O` 打开对应购弹界面。

如果你想直接测试普通英雄机器人，也可以用命令：

```text
/robomc hero regular ranged
```

## 按键指南

| 按键 | 默认键位 | 作用 | 生效条件 |
|------|----------|------|----------|
| 打开机器人配置 | `P` | 打开当前机器人配置界面 | 任意时刻，未打开其他界面 |
| 打开机器人选择 | `K` | 打开机器人类型与机动模式选择界面 | 任意时刻，未打开其他界面 |
| 购买步兵弹药 | `O` | 打开步兵弹药购买界面 | 当前机器人是 `Infantry` |
| 购买英雄弹药 | `I` | 打开英雄弹药购买界面 | 当前机器人是 `Hero` |
| 切换飞行模式 | `G` | 开关无人机飞行模式 | 当前机器人是 `Aerial` |
| 上升 | `Space` | 无人机上升 | 当前机器人是 `Aerial` 且飞行模式已开启 |
| 下降 | `Left Alt` | 无人机下降 | 当前机器人是 `Aerial` 且飞行模式已开启 |
| 射击 | 鼠标左键 | 发射当前机器人弹丸 | 机器人模式开启、主手为空、当前弹药大于 0 |
| 自动瞄准 | 鼠标右键 | 在瞄准框内自动吸附目标 | 机器人模式开启、主手为空 |
| 移动 | `W A S D` | 控制机器人移动 | 与正常移动一致 |
| 跳跃 | `Space` | 常规跳跃 | 非无人机，且机器人配置允许跳跃时更明显 |

补充说明：

- 机器人模式激活后，`Shift` 下蹲会被客户端抑制，避免与机器人驾驶冲突。
- `P` 只修改当前机器人配置，`K` 用于切换机器人类别和普通/轮腿机动模式。
- 无人机弹药不能像英雄和步兵那样在比赛中通过购弹界面补充。

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
