# CustomQuest 配置编辑器

打开 `index.html` 即可使用，无需安装 Node.js、JavaScript 依赖或启动服务器。

## 功能

- 创建 `kill_mob`、`submit_item`、`describe` 三类任务配置
- 增删任务目标，填写显示名、物品自定义名和计分板行
- 配置重复接取、冷却、达成命令、导航位置，以及接受任务后自动开启导航
- 创建节点式 NPC 对话文件，配置 `npc id`、`when` 条件和多个对话节点
- 为每个对话按钮配置提交任务快捷动作、提交物品覆盖、Kether、跳转节点和关闭行为；接取任务通过 `then` 中的内置 `quest accept <任务ID>` 指令完成；按钮无需填写 `id`
- NPC 变量不自动初始化，未设置的变量按 `null` 判断；条件支持 NPC data 和 PAPI
- 实时预览 YAML，支持复制到剪贴板或下载 `.yml`；文件名支持中文
- YAML 顶层配置自动使用空行分隔，便于阅读 `title`、`npc id`、`when` 和各个对话节点
- 自动缓存当前任务和 NPC 对话草稿，刷新页面或切换编辑器后会自动恢复

## 使用

1. 双击打开 `editor/index.html`。
2. 在“任务编辑器”或“NPC 对话编辑器”中填写内容。
3. 点击“复制 YAML”或“下载配置”。
4. 将任务文件放入服务器的 `plugins/CustomQuest/quests/`，对话文件放入 `plugins/CustomQuest/dialogues/`。
5. 在服务器执行 `/cq reload`。

编辑器会将草稿保存在当前浏览器的本地缓存中，不会上传到服务器。点击“重置”会恢复默认内容并清除缓存。

如果任务配置了有效的导航位置，可勾选“接受任务后自动开启导航”，导出：

```yaml
navigate: "world,100,64,200"
navigate-on-accept: true
```

编辑器只生成配置，不会自动连接 Minecraft 服务器，也不会校验 MythicMobs 怪物名、Citizens NPC 是否存在或材料是否适用于当前版本。保存前请确认任务 ID、NPC 数字 ID、节点 ID、材料名和 Kether 动作拼写正确。NPC 变量使用 NPC ID 作为唯一变量标识，不需要填写 key。

## NPC 对话格式

对话编辑器生成的新格式示例：

```yaml
title: "&8[&6任务发布官&8] &fNPC"
npc id: '5'
when:
  - if: "check profile data 5== null"
    open: suxing1
  - if: "check profile data 5== 1 , %player_level% >= 10"
    open: suxing2

suxing1:
  npc:
    - '你好，旅行者！'
  format: generic
  player:
    - reply: '&f你有什么任务'
      then: |
        npc data set 5 1
        goto suxing2
```

`when` 按配置顺序检测。节点式配置只会自动打开 `when` 中明确引用且条件满足的节点；没有匹配时不打开对话。按钮的“点击后跳转节点”会生成 `goto <节点 ID>`；也可以直接在 `then: |` 中填写 `goto <节点 ID>`。旧版 `npc`、`branches` 格式仍可导入和加载，但不会自动打开无条件分支。

按钮不需要配置 `id`，插件会按照每个节点中的按钮顺序自动生成内部编号；旧配置中已有的 `id` 仍然可以继续读取。

按钮的 `then: |` 支持 YAML 多行格式，每一行都会作为 Kether 动作执行，例如：

```yaml
then: |
  command inline "lp user {{ sender }} permission set 战士" as console
  command inline "class forceprofess {{ sender }} 战士" as console
```

`then: |` 中也支持插件内置的接取任务动作：

```yaml
then: |
  quest accept example_kill
```

编辑器在 `then` 区域提供“插入接取任务指令”按钮，会根据“任务 ID”自动填入任务 ID。
接取任务请直接在 `then` 中写入 `quest accept <任务ID>`，也可以使用编辑器提供的插入按钮。

编辑器不再提供“接取成功后设置数据（key=value）”字段。需要推进 NPC 对话变量时，请直接在按钮的 `then: |` 中填写 `npc data set`，例如：

```yaml
then: |
  npc data set 5 1
  goto suxing2
```
## 读取已有配置

点击“导入 YAML”，选择现有的任务文件或对话文件。编辑器会根据 `branches`、`npc id` 或 `when` 自动识别类型，并将字段载入对应表单；修改后可继续复制或下载。

导入模块位于 `editor/import.js`，因此使用 `index.html` 时请保持两个文件在同一目录。
