# CustomQuest —— Paper 任务插件

CustomQuest 是基于 **Paper 1.21.x**、**Java 21** 与 **TabooLib 6.2.4** 的任务插件，
提供多目标任务、NPC 分支对话、SQLite 玩家数据、SoulCore HUD/计分板任务追踪、Kether 脚本以及 SoulCore 客户端导航。
MythicMobs 与 Citizens2 为可选联动，PlaceholderAPI 为必需依赖。

当前项目版本为 **1.6.3**。源码以 Paper **1.21.1 API** 编译，并已在 Paper **1.21.11** 上完成启动与任务导航联机测试。

## 功能总览

- ✅ **Citizens2 对接**：按 NPC id 识别 NPC，每个 NPC 对话配置文件（`dialogues/*.yml`）可绑定一个或多个 NPC id
- ✅ **NPC data 分支对话（玩家级数据）**：对话分支支持 `data=` / `data-value=`（或列表形式）与 PAPI 变量条件；
  当 NPC 的 data 值不同时显示不同对话，用于接取任务、推进分支剧情。
  **data 变量按玩家独立存储**：每个玩家在同一 NPC 上有自己的 data 值，不同玩家可处于不同对话分支，互不影响
- ✅ **对话内接取任务指令**：对话选项直接配置 `accept-quest: <任务ID>` 即可接取任务（无需写 Kether），
  并可配置 `accept-data` 在接取成功后自动设置 NPC data 变量；Kether 写法 `quest accept <任务ID> [data <key> <value>]` 同样支持
- ✅ **接取任务不校验前置条件**：任务门控（谁能看到接取选项）完全由对话分支的 data / PAPI 条件控制
- ✅ **三种任务类型，均支持多项目标**
  - `kill_mob` —— 击杀 MythicMobs 怪物（可配置多个怪物目标，各自计数）
  - `submit_item` —— 提交物品（可配置多种物品）
  - `describe` —— 描述任务（无目标，仅展示；只能通过 `/cq quest complete` 指令强制完成），
    用于在任务追踪 HUD/计分板上显示任务标题与任务内容
- ✅ **奖励**：任务完成时执行指令奖励（`commands`，控制台执行、支持 `%player%` 与 PAPI）
  + Kether 完成动作（`kether`）
- ✅ **双通道任务追踪**：SoulCore 客户端使用最多 5 项的任务 HUD；无对应通道或发包失败时自动回退右侧计分板
- ✅ **任务导航**：CustomQuest 下发目标，SoulCore Fabric 客户端渲染原版信标光柱、无地形遮挡圆环与高对比度任务名/距离悬浮标签；不显示固定底部导航 HUD
- ✅ **Kether 脚本动作**：点击聊天选项后、任务完成后均执行 TabooLib Kether 脚本

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 服务端 | 使用 Paper 1.21.1 API 编译；已验证 Paper 1.21.11，其他 1.21.x 需自行实服验证 |
| Java | 21 |
| PlaceholderAPI | 必需；构建与联机测试使用 2.12.3 |
| MythicMobs | 可选；构建使用 5.13.0 |
| Citizens | 可选；构建使用 2.0.36-SNAPSHOT |
| SoulCore Fabric | 可选；任务 HUD 优先使用 `soulcore:quest_tracking_v3`（兼容 v2/v1），任务导航使用 `soulcore:quest_navigation` |
| TabooLib | 6.2.4 维护线；首次启动需下载运行时模块，也可使用离线依赖包 |

## 安装

1. 将 `CustomQuest-1.6.3.jar` 和必需的 PlaceholderAPI 放入服务端 `plugins/` 目录。
2. 按功能选装 MythicMobs 与 Citizens；缺少它们时，击杀任务或 NPC 对话功能不可用。
3. 启动服务器；首次启动会下载 TabooLib、SQLite 运行库并生成默认配置、示例文件和 `data.db`。
4. 按需编辑 `plugins/CustomQuest/` 下的配置后执行 `/cq reload`。
5. 使用客户端任务 HUD/导航的玩家还需安装匹配 Minecraft 版本、支持 `soulcore:quest_tracking_v3`（兼容 v2/v1）/ `soulcore:quest_navigation` 的 SoulCore Fabric Mod；服务端不需要 SoulCore Paper 插件。未安装时任务追踪自动回退计分板。

### 离线安装（服务器无法访问 repo.tabooproject.org 时）

若启动日志出现 `UnknownHostException: repo.tabooproject.org` 或“无法启动 Kotlin 环境”，
说明服务器无法下载 TabooLib 运行时依赖。先在可联网的构建机执行：

```bash
./gradlew collectLibs --console=plain
```

然后保持 Maven 目录结构，将 `offline-libs/` 内的全部内容复制到服务器根目录的 `libraries/`，
再单独安装 CustomQuest、PlaceholderAPI 以及按需使用的 MythicMobs/Citizens 插件。检测到依赖已存在时，TabooLib 会跳过下载。
`offline-libs/` 是生成目录，不会纳入 Git。

## 目录结构

```
plugins/CustomQuest/
├── config.yml            # 全局配置（自动保存间隔 + 任务追踪开关）
├── messages.yml          # 消息文本
├── quests/               # 任务配置（一个任务一个 yml）
│   ├── example_kill.yml
│   └── example_submit.yml
├── dialogues/            # NPC 对话配置（一个 NPC 一个或多个 yml）
│   └── example_npc.yml
├── data.db               # SQLite 玩家任务数据（自动生成，勿手动修改）
└── data/                 # 旧版 YAML 备份（迁移后不再读写）
```

## 任务配置（quests/*.yml）

```yaml
quest-id: example_kill          # 任务 ID（唯一）
name: "&e清剿荒野"              # 任务名称（支持 & 颜色与 PAPI）
description:                    # 接取时显示的描述
  - "&7去清剿荒野上的怪物吧！"

type: kill_mob                  # kill_mob / submit_item

# ---- 全息视图（计分板）标题行（可选，留空 = 用内置默认格式）----
# board-title: "&6&l{name}"     # 变量 {name} {id} {type}，支持 & 颜色与 PAPI

# ---- 多项目标（推荐写法） ----
objectives:
  - mob: SkeletonKing           # kill_mob：MythicMobs 怪物内部名
    amount: 10                  # 击杀数量
    name: "&7骷髅王"            # 自定义显示名（可选，计分板/提示显示用）
    # 该目标在计分板上的显示行（可选，留空 = 用内置默认格式）：
    # board-line: "&c击杀 {target} &e{current}&7/&e{total}"
  - mob: ZombieMinion
    amount: 5
    name: "&2僵尸小兵"
# ---- 单目标简写（与 objectives 二选一） ----
# mob: SkeletonKing
# amount: 10

# submit_item 类型的多项目标写法：
# items:
#   - "DIAMOND:5"
#   - "IRON_INGOT:10"
# 或使用 objectives 列表（支持更多自定义）：
# objectives:
#   - item: "DIAMOND:5"
#     name: "&b大钻石"            # 自定义显示名（可选）
#     item-name: "&b大钻石"       # 可选：只收集显示名为该文字的物品（铁砧改名）
#     board-line: "&b收集 {target} &e{current}&7/&e{total}"  # 可选：计分板行格式

auto-complete: true             # 所有目标达成后自动完成（submit_item 建议 false）
repeatable: false               # 是否可重复
cooldown: 3600                  # 重复接取冷却（秒）

commands:                       # 奖励：指令（控制台执行，支持 %player% / PAPI）
  - "give %player% DIAMOND 3"
kether:                         # 奖励：Kether 完成动作
  - "message '&a任务完成！'"
  - "give-item diamond 2"
```

### 描述任务（describe，展示型）

无任何目标，用于在全息视图（计分板）上展示任务标题与内容；**只能通过指令强制完成**：

```yaml
quest-id: describe_story
name: "&6&l主线剧情"          # 计分板首行（任务标题）
description:                   # 任务内容（计分板逐行展示）
  - "&7第一章 · 初入荒野"
  - "&7找老杰克聊聊吧"
type: describe                 # 描述任务

# 接取（显示）：
#   /cq quest accept <玩家> describe_story   （或对话选项 accept-quest / quest accept）
# 强制完成（从计分板移除）：
#   /cq quest complete <玩家> describe_story
# 注意：Kether 的 quest complete / quest submit 对描述任务无效，仅指令可完成
```

> **说明：接取任务不校验前置条件。** 任务门控（谁能看到并点击接取选项）请使用
> NPC 对话分支的 data / PAPI 条件；接取后可用 `accept-data` 或 `data` 参数设置 NPC data 变量推进剧情。

## 任务追踪（SoulCore HUD / 计分板回退）

`scoreboard.enabled` 继续作为任务追踪总开关。新版客户端优先监听带标题颜色和导航按钮状态的
`soulcore:quest_tracking_v3`；已有 v2 客户端继续使用颜色快照，更旧客户端使用 `soulcore:quest_tracking` v1；
无通道或快照发送失败时使用原生计分板。`title` 与 `gap-lines` 仅影响回退计分板：

```yaml
scoreboard:
  enabled: true            # HUD 与回退计分板的总开关
  title: "&6&l任务追踪"    # 回退计分板标题（支持 & 颜色）
  gap-lines: 1             # 回退计分板任务间空行（0 = 无空格）

quest-book:
  gap-lines: 1             # 任务书中每个任务之间的空行数（0 = 无空格）
  per-page: 3              # 任务书每页显示的任务数
  no-quest: "&c您当前没有接取任务..."  # 无任务时第一行显示（顶格，可自行加空格）
```

回退计分板的每项任务显示内容可在**任务文件里**完全自定义。

SoulCore HUD 使用紧凑、纯文本快照：导航中的任务优先，其余按接取时间与任务 ID 稳定排序，最多显示 5 项。
标题会应用 `board-title` 与 PAPI；任务级 `board-line` 或目标自己的 `board-line` 会发送格式化后的纯文本。
未自定义的击杀/提交目标以“击败/收集 + 显示名 + 实时进度”发送，描述任务使用 description；每个任务最多 2 行。
所有 HUD 文本都会剥离颜色、控制字符并限制为单行与 256 UTF-8 bytes。v2 会另行提取任务 `name`
在首个可见字符前实际生效的 `&0`–`&f` 颜色，用于客户端任务标题和目标圆点；未写颜色时客户端回退任务类型色。
`board-title` 只决定显示文字，不覆盖任务 `name` 的强调色；旧 v1 客户端仍按任务类型上色。

v3 快照会在 v2 颜色与稳定任务 ID 基础上标记任务是否配置了导航目标。SoulCore 只为存在目标的任务显示“导航”按钮；
没有目标时完全不显示按钮。玩家点击后，客户端通过 `soulcore:quest_navigation_request` 请求切换导航，
按钮在正在导航时显示“导航中”，再次点击会请求取消；服务端仍会重新校验任务已接取、目标存在且世界有效。

### 完全自定义显示（每个任务单独写）

在 `quests/*.yml` 里写：

- **`board-title`**：本任务标题行（留空 = 用内置默认 `{name}`）
- **`board-line`**：本任务的显示行列表（**任意多行**，写多少显示多少，不再自动生成目标行）

```yaml
# quests/example_kill.yml
board-title: "&6&l{name}"
board-line:
  - "&7目标1：击杀 &f{display1} &e{current1}&7/&e{total1}"
  - "&7目标2：击杀 &f{display2} &e{current2}&7/&e{total2}"
  - "&7总进度：&e{current}&7/&e{total}"
```

`board-line` 每一行都可使用以下变量（均支持 `&` 颜色与 PAPI）：

| 变量 | 说明 |
| --- | --- |
| `{name}` / `{id}` / `{type}` | 任务名称 / ID / 类型显示名 |
| `{current}` / `{total}` | 任务总进度 / 总需求（所有目标合计） |
| `{targetN}` | 第 N 个目标标识（击杀 = MythicMobs 怪物内部名；提交 = 材料名），N 从 1 开始 |
| `{displayN}` / `{mobN}` / `{itemN}` | 第 N 个目标显示名 |
| `{currentN}` / `{totalN}` / `{amountN}` | 第 N 个目标进度 / 需求 / 需求（同 `{totalN}`） |

例如 `{target1}` `{display1}` `{current1}` `{total1}` 表示第 1 个目标；`{target2}` 表示第 2 个目标，依此类推。

### 不写 board-line 时的自动模式

若不写任务级 `board-line`，则按「标题 + 每个目标一行」自动生成（使用内置默认格式），此时可用以下方式覆盖单行：

- **任务标题行**：`board-title`（留空 = 内置默认 `{name}`）
- **每个目标的进度行**：在 `objectives` 每个目标里写 `board-line`（留空 = 内置默认格式）

```yaml
# quests/example_kill.yml（自动模式下的单行覆盖）
board-title: "&6&l{name}"

objectives:
  - mob: SkeletonKing
    amount: 10
    name: "&7骷髅王"
    board-line: "&c击杀 {target} &e{current}&7/&e{total}"   # 可选：本目标行
  - mob: ZombieMinion
    amount: 5
```

自动模式下目标行可用的变量：

| 变量 | 说明 |
| --- | --- |
| `{name}` / `{id}` / `{type}` | 任务名称 / ID / 类型显示名 |
| `{target}` | 目标标识（击杀 = MythicMobs 怪物内部名；提交 = 材料名） |
| `{display}` | 目标显示名（目标里自定义的 `name`，未填则为标识） |
| `{mob}` | 击杀目标的显示名 |
| `{item}` | 收集目标的显示名 |
| `{current}` | 当前进度（该目标的当前数量） |
| `{total}` | 总需求（该目标的需求数量） |
| `{amount}` | 目标需求数量（同 `{total}`） |
| `{index}` | 目标序号（从 1 开始） |
| `{line}` | 描述任务的单行内容（仅描述任务内容行可用） |

例如针对某个目标自定义显示：`board-line: "&c击杀 {target} &e{current}&7/&e{total}"`。

- 玩家接取任务后，支持通道的客户端实时更新 HUD；其他客户端在右侧计分板显示任务标题与各目标进度
- **多项目标逐条显示**：多目标任务的**每个目标**都单独显示一行进度：

```
&6&l任务追踪
&e清剿荒野
 已击杀 SkeletonKing 3/10
 已击杀 ZombieMinion 2/5
```

- 回退计分板最多选取 5 个任务，并按「完整块」展示（标题 + 全部目标行）；受侧边栏 15 行限制，放不下的任务整体跳过并以「…还有 N 个任务」提示，
  **不会只显示部分目标**
- 击杀 MythicMobs 怪物后**即时刷新**；提交物品类进度随背包数量自动刷新
- **描述任务**：HUD 最多显示两行描述；回退计分板显示任务标题 + 完整 description，完成（指令强制）后移除
- 关闭开关后自动清空 SoulCore HUD 与本插件设置的计分板（`/cq reload` 即时生效）

## 任务书与导航

### 任务书（/quest）

玩家执行 `/quest` 打开任务书，展示当前所有已接任务；
每页显示 `per-page` 个任务，可翻页；任务之间按 `quest-book.gap-lines` 空行分隔。

- 没有接取任务时，书的第一行显示 `quest-book.no-quest` 配置的文本（默认 `&c您当前没有接取任务...`，顶格显示，可自行加空格调整）
- 每个任务标题后带一个 **「导航」** 按钮（点击即导航；已导航该任务时按钮变为红色 **「取消导航」**）

### 导航系统

在任务书里点击「导航」后：

- 需要客户端安装支持 `soulcore:quest_navigation` 通道的 SoulCore Fabric Mod；未安装时不会建立导航
- Mod 在目标位置渲染原版信标材质光柱、圆环和随距离缩放的任务名/距离悬浮标签
- 靠近目标（5 米内）后提示到达并自动结束导航
- **同一时间只能导航一个任务**：导航第二个任务会自动取消第一个；再次点击按钮（显示为「取消导航」）取消导航
- 全部视觉效果仅存在于客户端，不修改服务端方块、不生成实体；超过 Mod 世界标记范围时不显示导航视觉

导航位置在任务文件里配置：

```yaml
# quests/example_kill.yml
navigate: "world,100,64,200"   # 世界名,x,y,z
```

也可用指令配置（写回任务文件，导航位置设为执行者当前位置）：

```bash
/cq quest nav set <任务ID>
/cq quest nav remove <任务ID>
```

> `nav set/remove` 使用 Bukkit YAML 重新保存整个任务文件，原有注释可能丢失；重要配置请先备份。

## NPC 对话配置（dialogues/*.yml）

```yaml
npc: 5                        # 绑定 NPC id（可写成列表 [5, 6]）
title: "&8[&6任务发布官&8] &f老杰克"

# 首次对话初始化（可选，推荐）：玩家第一次点击该 NPC 时，
# 若某个 data 变量不存在则自动写入默认值（玩家级），
# 让 data: stage / data-value: none 这类初始分支条件第一次就能生效
default-data:
  stage: none

branches:                     # 从上到下找第一个「条件全部满足」的分支显示
  initial:
    data: stage               # NPC data 条件（写法一：key + data-value）
    data-value: none
    # 或列表写法（写法二）：data: ["stage==none", "level>=1"]
    papi:                     # PAPI 条件（可选）
      - "%player_level% >= 3"
    lines:                    # 对话内容
      - "&f你好，旅行者！"
    options:                  # 可点击选项
      accept:
        text: "&a&l[接受任务] &a清剿荒野"
        hover: "&7点击接受任务"              # 悬浮提示（可选）
        # ---- 接取任务快捷指令（推荐）----
        accept-quest: example_kill           # 点击后接取任务（不校验前置条件）
        accept-data: "stage=doing"           # 接取成功后设置 NPC data 变量（可列表）
        # 等价 Kether 写法：
        # kether:
        #   - "quest accept example_kill data stage doing"
      reject:
        text: "&7我暂时没空"
        kether:
          - "npc data set stage none"
  doing:
    data: stage
    data-value: doing
    lines: ["&f怪物清剿得如何了？"]
    options:
      submit:
        text: "&a&l[提交任务] &a我完成了！"
        kether:
          - "quest submit example_kill"
          - "npc data set stage done"
  fallback:
    default: true             # 兜底分支（所有分支都不满足时显示）
    lines: ["&f……"]
    options:
      leave:
        text: "&7离开"
        kether: []
```

- 分支匹配顺序：先匹配带条件的分支，全部失败后回退到 `default: true` 或无条件的分支
- 点击选项通过内部指令 `/cq click <npcId> <分支id> <选项序号>` 回调，执行时会再次校验分支条件
- `accept-quest` 接取成功后才会设置 `accept-data` 中的变量（`key=value`，可写列表）；
  若选项还配置了 `kether`，会在接取任务之后执行
- 选项 Kether 执行时注入变量：`@NpcId`、`@BranchId`、`@Option`、`@Player`
- **NPC data 为玩家级数据**：每个玩家在该 NPC 上有独立的 data 值（持久化保存于 SQLite `data.db`），
  不同玩家处于不同对话分支；管理指令需指定玩家：`/cq data set <玩家> 5 stage none`

## 自定义 Kether 动作

在任意 Kether 脚本位置（对话选项、任务 `kether` 奖励）均可用：

| 动作 | 说明 |
| --- | --- |
| `quest accept <任务ID> [data <key> <value>]` | 接取任务（不校验前置条件）；可选在成功后设置当前 NPC 的 data 变量 |
| `quest abandon <任务ID>` | 放弃任务 |
| `quest submit <任务ID>` | 提交进度（足够则完成并发奖） |
| `quest complete <任务ID>` | 强制完成并发奖（描述任务无效，仅可用指令完成） |
| `quest progress <任务ID>` | 返回当前进度（数值） |
| `quest has <任务ID>` / `quest done <任务ID>` | 是否已接取 / 已完成（布尔） |
| `dialogue open [npcId]` | 打开 NPC 对话（缺省用当前 `@NpcId`） |
| `npc data set <key> <value>` | 设置当前玩家在该 NPC 上的 data 变量（持久化） |
| `npc data get <key>` | 获取当前玩家在该 NPC 上的 data 变量 |
| `npc data remove <key>` | 删除当前玩家在该 NPC 上的 data 变量 |

同时支持 TabooLib Kether 全部内置动作（`command`、`message`、`give-item`、`if/else` 等）。

## 指令

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/cq help` | 所有人 | 查看帮助 |
| `/cq list` | customquest.admin | 列出所有任务 |
| `/cq reload` | customquest.admin | 重载任务、对话与全局配置 |
| `/quest` | 所有人 | 打开任务书（查看已接任务、点击导航、翻页） |
| `/cq quest accept <玩家> <任务ID>` | customquest.admin | 强制接取任务 |
| `/cq quest abandon <玩家> <任务ID>` | customquest.admin | 放弃任务 |
| `/cq quest complete <玩家> <任务ID>` | customquest.admin | 强制完成任务并发奖（描述任务仅能通过此指令完成） |
| `/cq quest nav set <任务ID>` | customquest.admin | 设置任务导航位置为你的当前位置（写回任务文件） |
| `/cq quest nav remove <任务ID>` | customquest.admin | 移除任务导航位置 |
| `/cq data set <玩家> <npcId> <key> <value>` | customquest.admin | 设置指定玩家在该 NPC 上的 data 变量 |
| `/cq data get <玩家> <npcId> <key>` | customquest.admin | 查看指定玩家在该 NPC 上的 data 变量 |
| `/cq data remove <玩家> <npcId> <key>` | customquest.admin | 删除指定玩家在该 NPC 上的 data 变量 |

## PAPI 变量

- `%customquest_progress_<任务ID>%` —— 当前进度（多目标合计）
- `%customquest_has_<任务ID>%` —— 是否已接取
- `%customquest_done_<任务ID>%` —— 是否已完成
- `%customquest_state_<任务ID>%` —— 状态（none / accepted / done）
- `%customquest_accepted_count%` / `%customquest_done_count%` —— 接取数 / 完成数

## 已知限制

- 当前实现按普通 Paper 调度模型开发；虽然生成的插件描述可能带有 `folia-supported`，但尚未完成 Folia 线程模型适配与验证。
- 任务计分板会替换玩家当前 Scoreboard，关闭或清理时不会恢复其他插件之前设置的计分板。
- SQLite 玩家数据的载入、退出保存和定时保存运行在主线程；大量同时在线玩家需要额外进行性能压测。
- 导航只支持静态坐标、同世界单目标，不提供动态 NPC/实体追踪或路径规划。
- “改进透明度/Fabulous”下，SoulCore 标签隔着水或玻璃时可能受到最终透明合成的轻微染色。
- `/cq list` 当前会先发送无权限提示，但没有真正阻止非管理员继续列出任务；在修复前不要将任务列表视为私密信息。
- 计分板回退只在玩家仍使用原生主计分板且没有其他侧边栏时启用；检测到其他插件计分板后会主动让出，不反复抢占。
- 服务端每 5 秒向 SoulCore HUD 重发一次完整快照；客户端超过约 13 秒没有收到心跳会自动清空，覆盖热禁用和代理后端切换的残留场景。
- 当前已验证 CustomQuest + PlaceholderAPI + SoulCore 的 Paper 1.21.11 导航链路；MythicMobs、Citizens 与大规模 SQLite 场景仍需单独集成测试。

## 构建

```bash
./gradlew test build --console=plain
# 产物：build/libs/CustomQuest-1.6.3.jar
```

- 需要 JDK 21
- Gradle Wrapper 固定使用 Gradle 8.14.3
- 本仓库已内置 `libs/PlaceholderAPI-2.12.3.jar` 作为编译依赖（也可改用仓库坐标）
- TabooLib `6.2.4-c90a237` 通过 TabooLib Gradle 插件内置/重定向，服务端无需单独安装 TabooLib 插件

## 网页文档

同目录的 `wiki.html` 可直接用浏览器打开；它是静态镜像，若内容与当前源码或 README 不一致，以源码和 README 为准。
