# CustomQuest —— Paper 任务插件

CustomQuest 是基于 **Paper 1.21.x**、**Java 21** 与 **TabooLib 6.2.4** 的任务插件，
提供多目标任务、NPC 分支对话、SQLite 玩家数据、计分板任务追踪与 Kether 脚本。
MythicMobs 与 Citizens2 为可选联动，PlaceholderAPI 为必需依赖。

当前项目版本为 **1.7.2**。源码以 Paper **1.21.1 API** 编译，并已在 Paper **1.21.11** 上完成启动验证。

## 功能总览

- ✅ **Citizens2 对接**：按 NPC id 识别 NPC，每个 NPC 对话配置文件（`dialogues/*.yml`）可绑定一个或多个 NPC id
- ✅ **NPC data 分支对话（玩家级数据）**：对话分支支持 `data=` / `data-value=`（或列表形式）与 PAPI 变量条件；
  当 NPC 的 data 值不同时显示不同对话，用于接取任务、推进分支剧情。
  **data 变量按玩家独立存储**：每个玩家在同一 NPC 上有自己的 data 值，不同玩家可处于不同对话分支，互不影响
- ✅ **NPC 任务对话（原版箱子界面）**：服务器直接打开一个箱子承载对话 —— 第 1 格是 NPC 标题与正文，第 3 格起是选项，点击选项格即提交；不依赖任何客户端模组
- ✅ **对话内接取任务指令**：在对话选项的 `then: |`（节点式配置）或 `kether`（传统配置）中使用 `quest accept <任务ID>` 接取任务；
  接取成功后可继续使用 `npc data set <npcId> <value>` 推进 NPC 对话
- ✅ **NeigeItems 物品提交**：提交物品目标支持 `neige-item` / `ni` 配置，并按 NI ID 校验和扣除；未安装 NeigeItems 时普通 Bukkit 物品功能不受影响
- ✅ **NPC 原子提交任务**：对话选项通过 `submit-quest` 实时校验任务条件，并可用 `submit-items` 覆盖该按钮实际扣除清单；物品任务成功时才一次性扣除背包物品，
  随后的选项 Kether 仅在提交成功后执行，任务完成本身不修改 NPC data
- ✅ **条件达成与任务完成分离**：击杀目标或物品目标达成时提示“你已达成任务条件”，并可执行 `condition-commands` 推进 NPC data；
  达成条件本身不会自动完成任务，真正完成必须由显式指令或 NPC 提交动作触发
- ✅ **接取任务不校验前置条件**：任务门控（谁能看到接取选项）完全由对话分支的 data / PAPI 条件控制
- ✅ **三种任务类型，均支持多项目标**
  - `kill_mob` —— 击杀 MythicMobs 怪物（可配置多个怪物目标，各自计数）
  - `submit_item` —— 提交物品（可配置多种物品）
  - `describe` —— 描述任务（无目标，仅展示；只能通过强制完成动作完成），
    用于在任务追踪计分板上显示任务标题与任务内容
- ✅ **任务追踪**：接取任务后实时写入右侧计分板，按「任务标题 + 目标行」完整块展示；检测到其他插件侧边栏时主动让出
- ✅ **Kether 脚本动作**：NPC 对话选项可执行 TabooLib Kether 脚本

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 服务端 | 使用 Paper 1.21.1 API 编译；已验证 Paper 1.21.11，其他 1.21.x 需自行实服验证 |
| Java | 21 |
| PlaceholderAPI | 必需；构建与联机测试使用 2.12.3 |
| MythicMobs | 可选；构建使用 5.13.0 |
| Citizens | 可选；构建使用 2.0.36-SNAPSHOT |
| ChaUI | 完全可选；对话开箱即用，安装后可按容器名称（`dialogue.title`）自行配置页面来替换箱子外观 |
| TabooLib | 6.2.4 维护线；首次启动需下载运行时模块，也可使用离线依赖包 |

## 安装

1. 将 `CustomQuest-1.7.2.jar` 和必需的 PlaceholderAPI 放入服务端 `plugins/` 目录。
2. 按功能选装 MythicMobs 与 Citizens；缺少它们时，击杀任务或 NPC 对话功能不可用。
3. 启动服务器；首次启动会下载 TabooLib、SQLite 运行库并生成默认配置、示例文件和 `data.db`。
4. 按需编辑 `plugins/CustomQuest/` 下的配置后执行 `/cq reload`。
5. NPC 对话开箱即用，使用原版箱子界面，不需要任何客户端模组；若想自定义对话外观，可另外安装 ChaUI，并按容器名称（`config.yml` 的 `dialogue.title`）配置覆盖页面。

> 内置的 `quests/example_*.yml` 示例任务和 `dialogues/example_npc.yml` 示例对话只会在首次安装时生成一次，手动删除后不会因重启或 `/cq reload` 而恢复。`config.yml` 与 `messages.yml` 保持原有行为，缺失时仍会自动重新生成。

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
├── .examples-initialized # 内置示例已初始化标记（请勿手动删除）
├── config.yml            # 全局配置（自动保存间隔 + 任务追踪开关）
├── messages.yml          # 消息文本
├── quests/               # 任务配置（一个任务一个 yml）
│   ├── example_kill.yml
│   ├── example_submit.yml
│   ├── example_craft_item.yml
│   ├── example_craft_count.yml
│   └── example_describe.yml
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

type: kill_mob                  # kill_mob / submit_item / craft_item / craft_count / describe

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
# NeigeItems 物品：使用 NI 配置中的物品 ID
#   - neige-item: example_sword
#     amount: 2
#     name: "&b示例剑"
#     board-line: "&b收集 {target} &e{current}&7/&e{total}"  # 可选：计分板行格式

# EpicCraftingsPlus 合成任务：
# 1) craft_item：按 EpicCraftingsPlus 配置中的 crafting 名称匹配，每个成功合成事件计 1 次
#    crafting: EpicSword
#    amount: 3
# 2) craft_count：不限制合成名称，每个成功合成事件计 1 次
#    amount: 10
# 也支持 objectives 写法：
# objectives:
#   - crafting: EpicSword
#     amount: 3
#     name: "&b史诗之剑"
# 达成条件时执行一次。message 为聊天提示；也可执行控制台指令修改 NPC data。
# 可用变量：%player% / %quest% / %quest_id% / %quest_name% 以及 PAPI。
condition-commands:
  - "message &a你已达成任务条件：&f%quest_name%"
  - "cq data set %player% 5 kill_ready"

repeatable: false               # 是否可重复
cooldown: 3600                  # 重复接取冷却（秒）
```

`condition-commands` 每次接取最多执行一次。未配置 `message` 动作时使用默认提示“你已达成任务条件：任务名”；配置 `message` 后会替换默认提示：

- `message <文本>`：向玩家发送支持 `&` 颜色和 PAPI 的聊天消息
- 其他内容：作为控制台指令执行
- 支持 `%player%`、`%quest%`/`%quest_id%`、`%quest_name%`
- 击杀任务在目标达成时执行；物品任务在背包满足目标时执行，NPC 提交前也会再次复核

旧任务文件中的 `auto-complete`、任务级 `commands` 和任务级 `kether` 已删除并会被忽略；完成任务只更新任务状态，不执行这些旧动作。

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
#   /cq quest accept <玩家> describe_story   （或对话选项 then 中的 quest accept）
# 强制完成（从计分板移除）：
#   /cq quest complete <玩家> describe_story
# 注意：描述任务不能使用 quest submit；可使用 Kether quest complete 或 /cq quest complete 强制完成
```

> **说明：接取任务不校验前置条件。** 任务门控（谁能看到并点击接取选项）请使用
> NPC 对话分支的 data / PAPI 条件；接取后可在选项的 `then: |` 中使用 `npc data set <npcId> <value>` 推进剧情。

## 任务追踪（右侧计分板）

`scoreboard.enabled` 是任务追踪总开关。任务以「任务标题 + 目标行」的完整块写入玩家右侧计分板；
检测到其他插件正在使用侧边栏时会主动让出，不反复抢占。`title` 与 `gap-lines` 分别控制标题与任务间空行：

```yaml
scoreboard:
  enabled: true            # 任务追踪总开关
  title: "&6&l任务追踪"    # 计分板标题（支持 & 颜色）
  gap-lines: 1             # 任务间空行（0 = 无空格）
```

计分板的每项任务显示内容可在**任务文件里**完全自定义。

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
| `{targetN}` | 第 N 个目标标识（击杀 = MythicMobs 怪物内部名；提交 = 材料名；`craft_item` = EpicCraftingsPlus 合成名称；`craft_count` = `*`），N 从 1 开始 |
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
| `{target}` | 目标标识（击杀 = MythicMobs 怪物内部名；提交 = 材料名；`craft_item` = EpicCraftingsPlus 合成名称；`craft_count` = `*`） |
| `{display}` | 目标显示名（目标里自定义的 `name`，未填则为标识） |
| `{mob}` | 击杀目标的显示名 |
| `{item}` | 收集目标的显示名 |
| `{current}` | 当前进度（该目标的当前数量） |
| `{total}` | 总需求（该目标的需求数量） |
| `{amount}` | 目标需求数量（同 `{total}`） |
| `{index}` | 目标序号（从 1 开始） |
| `{line}` | 描述任务的单行内容（仅描述任务内容行可用） |

例如针对合成目标可写：`board-line: "&7合成 {display} &e{current}&7/&e{total}"`。`craft_item` 与 `craft_count` 和其他计数目标一样支持上述变量。

- 玩家接取任务后实时更新右侧计分板，显示任务标题与各目标进度
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
- **描述任务**：计分板显示任务标题 + 完整 description，强制完成后移除
- 关闭开关后自动清空本插件为玩家设置的任务计分板（`/cq reload` 即时生效）

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
  all_done:                   # PAPI 完成分支必须放在 data 分支之前
    papi:
      - "%customquest_done_example_submit% == true"
    lines: ["&f感谢你的帮助！"]
  kill_done_offer:            # 击杀完成后提供物品任务
    papi:
      - "%customquest_done_example_kill% == true"
      - "%customquest_done_example_submit% == false"
      - "%customquest_has_example_submit% == false"
    lines: ["&f还需要一些矿物，你愿意帮我收集吗？"]
    options:
      accept:
        text: "&a&l[接受收集任务]"
        kether:
          - "quest accept example_submit"
          - "npc data set 5 item_collecting"
  initial:
    data: stage               # NPC data 条件（写法一：key + data-value）
    data-value: none
    # 或列表写法（写法二）：data: ["stage==none", "level>=1"]
    papi:                     # PAPI 条件（可选）
      - "%player_level% >= 3"
    lines:                    # 对话内容
      - "&f你好，旅行者！"
    options:                  # 最多向客户端显示 6 个可点击选项
      accept:                 # 稳定选项 ID：1-64 UTF-8 bytes，不能包含控制字符
        text: "&a&l[接受任务] &a清剿荒野"
        # ---- 接取任务 ----
        kether:
          - "quest accept example_kill"        # 不校验前置条件
          - "npc data set 5 kill_doing"       # 接取成功后设置 NPC data 变量
      reject:
        text: "&7我暂时没空"
        kether:
          - "npc data set 5 none"
  kill_doing:
    data: stage
    data-value: kill_doing
    lines: ["&f怪物清剿得如何了？"]
  kill_ready:                  # condition-commands 已把该玩家的 NPC data 改为 ready
    data: stage
    data-value: kill_ready
    lines: ["&a你已经达成任务条件。"]
    options:
      submit:
        text: "&a&l[提交击杀任务]"
        submit-quest: example_kill           # 再次校验击杀进度，成功才完成
  item_collecting:
    data: stage
    data-value: item_collecting
    lines: ["&f把要求的物品放在主背包后交给我。"]
    options:
      submit:
        text: "&a&l[提交物品]"
        submit-quest: example_submit         # 实时检查主背包，成功后原子扣物并完成
        # 可选：覆盖该按钮实际检测/扣除的清单；不写则沿用任务 objectives/items
        submit-items:
          - item: DIAMOND
            amount: 5
            name: "&b大钻石"                # 物品不足提示中的显示名（可选）
            item-name: "&b大钻石"           # 只匹配该自定义名称（可选）
          - "IRON_INGOT:10"                 # 无自定义名时可用简写
```

- 分支匹配顺序：传统 `branches` 和节点式 `when` 格式都只自动打开条件满足的分支；全部失败时不打开任何对话，`goto <节点 ID>` 仍可从按钮中强制跳转
- 节点式配置中的 `goto <节点 ID>` 是对话跳转动作；它不会作为 Kether 顶层脚本单独解析，`then: |` 中的其他动作仍按顺序执行
- NPC 对话由服务器打开的原版箱子承载：第 1 格是 NPC 标题与正文（物品名与 Lore），第 3 格起是选项；原版物品操作会被拦截，不能取走占位物品
- 每次打开对话都会创建绑定玩家的 60 秒一次性会话；选项以服务端当前快照为准，不能通过 NPC、分支或序号伪造
- 选项提交后会再次校验分支条件、NPC 存活状态、世界和 6 格交互距离；重放、过期或非本次展示的选项不会执行
- 接取任务请在选项的 `then: |`（节点式配置）或 `kether`（传统配置）中使用 `quest accept <任务ID>`；该动作不校验前置条件，后续动作按顺序执行
- `submit-quest` 会调用非强制提交：击杀任务复查进度，物品任务复查并一次性扣除主背包物品；
  只有成功后才完成任务并继续执行该选项的 Kether，不会修改 NPC data
- `submit-items` 必须与 `submit-quest` 同时使用，且仅支持 `submit_item` 任务。配置后会覆盖该按钮本次实际扣除清单，
  但任务原 `objectives/items` 仍负责进度、计分板显示与达成条件；未配置时保持旧行为。两份清单相同时也只扣一次
- `submit-items` 支持严格简写 `"MATERIAL:数量"`，或映射写法 `item` + 可选 `amount`、`name`、`item-name`；
  空列表、空气、未知材料、零数、负数和非数字会使对应对话文件拒绝加载
- 任一物品不足时不会扣除或完成任务；原目标与覆盖清单的缺口按多重集合合并，同规格保留更大实际缺口后全部提示
- 选项 Kether 执行时注入变量：`@NpcId`、`@BranchId`、`@Option`（兼容旧序号）、`@OptionId`、`@Player`
- 单次对话最多发送 8 行、6 个选项，总载荷上限 8192 bytes；标题、行、按钮与悬浮文字会保留合法颜色样式并进行严格 UTF-8/长度控制
- **NPC data 为玩家级数据**：每个玩家在该 NPC 上有独立的 data 值（持久化保存于 SQLite `data.db`），
  不同玩家处于不同对话分支；管理指令需指定玩家：`/cq data set <玩家> 5 none`

## 自定义 Kether 动作

在 NPC 对话选项的 Kether 脚本中可用：

| 动作 | 说明 |
| --- | --- |
| `quest accept <任务ID>` | 接取任务（不校验前置条件） |
| `quest abandon <任务ID>` | 放弃任务 |
| `quest submit <任务ID>` | 提交进度（足够则完成） |
| `quest complete <任务ID>` | 强制完成；提交物品任务仍需交齐物品，描述任务可直接完成 |
| `quest progress <任务ID>` | 返回当前进度（数值） |
| `quest has <任务ID>` / `quest done <任务ID>` | 是否已接取 / 已完成（布尔） |
| `dialogue open [npcId]` | 打开 NPC 对话（缺省用当前 `@NpcId`） |
| `npc data set <npcId> <value>` | 设置当前玩家在该 NPC 上的变量值（持久化）；值为 `null` 时删除变量并恢复为默认的未设置状态 |
| `npc data get <npcId>` | 获取当前玩家在该 NPC 上的变量值 |
| `npc data remove <npcId>` | 删除当前玩家在该 NPC 上的变量 |

同时支持 TabooLib Kether 全部内置动作（`command`、`message`、`give-item`、`if/else` 等）。`then: |` 可以直接写多行 Kether，例如：

```yaml
then: |
  command inline "lp user {{ sender }} permission set 战士" as console
  command inline "class forceprofess {{ sender }} 战士" as console
```

## 指令

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/cq help` | 所有人 | 查看帮助 |
| `/cq list` | 当前实际所有人 | 列出所有任务；权限检查存在已知缺口 |
| `/cq reload` | customquest.admin | 重载任务、对话与全局配置 |
| `/cq quest accept <玩家> <任务ID>` | customquest.admin | 强制接取任务 |
| `/cq quest abandon <玩家> <任务ID>` | customquest.admin | 放弃任务 |
| `/cq quest complete <玩家> <任务ID>` | customquest.admin | 强制完成；提交物品任务仍需交齐物品 |
| `/cq data set <玩家> <npcId> <value>` | customquest.admin | 设置指定玩家在该 NPC 上的变量值 |
| `/cq data get <玩家> <npcId>` | customquest.admin | 查看指定玩家在该 NPC 上的变量值 |
| `/cq data remove <玩家> <npcId>` | customquest.admin | 删除指定玩家在该 NPC 上的变量 |

## PAPI 变量

- `%customquest_progress_<任务ID>%` —— 当前进度（多目标合计）
- `%customquest_has_<任务ID>%` —— 是否已接取
- `%customquest_done_<任务ID>%` —— 是否已完成
- `%customquest_state_<任务ID>%` —— 状态（none / accepted / done）
- `%customquest_accepted_count%` / `%customquest_done_count%` —— 接取数 / 完成数

## 已知限制

- 当前实现按普通 Paper 调度模型开发；虽然生成的插件描述可能带有 `folia-supported`，但尚未完成 Folia 线程模型适配与验证。
- SQLite 玩家数据的载入、退出保存和定时保存运行在主线程；大量同时在线玩家需要额外进行性能压测。
- `/cq list` 当前会先发送无权限提示，但没有真正阻止非管理员继续列出任务；在修复前不要将任务列表视为私密信息。
- 计分板回退只在玩家仍使用原生主计分板且没有其他侧边栏时启用；检测到其他插件计分板后会主动让出，不反复抢占。
- 交互式任务对话需要 Citizens NPC 已生成且玩家选择按钮时仍在同一世界、距离 NPC 不超过 6 格。

## 构建

```bash
./gradlew test build --console=plain
# 产物：build/libs/CustomQuest-1.7.2.jar
```

- 需要 JDK 21
- Gradle Wrapper 固定使用 Gradle 8.14.3
- 本仓库已内置 `libs/PlaceholderAPI-2.12.3.jar` 与 `libs/CitizensAPI-2.0.36-SNAPSHOT.jar` 作为离线编译依赖；二者均不会打入插件 JAR
- TabooLib `6.2.4-c90a237` 通过 TabooLib Gradle 插件内置/重定向，服务端无需单独安装 TabooLib 插件

## 网页文档

仓库内的 [wiki.html](wiki.html) 可直接用浏览器打开；它是静态镜像，若内容与当前源码或 README 不一致，以源码和 README 为准。
