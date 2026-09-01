# CustomQuest 配置编辑器

打开 `index.html` 即可使用，无需安装 Node.js、JavaScript 依赖或启动服务器。

## 功能

- 创建 `kill_mob`、`submit_item`、`describe` 三类任务配置
- 增删任务目标，填写显示名、物品自定义名和计分板行
- 配置重复接取、冷却、达成命令、导航位置
- 创建 NPC 对话文件，配置 NPC ID、默认 data、分支条件和对话内容
- 为每个对话选项配置接取任务、提交任务、提交物品覆盖、Kether 和关闭行为
- 实时预览 YAML，支持复制到剪贴板或下载 `.yml`

## 使用

1. 双击打开 `editor/index.html`。
2. 在“任务编辑器”或“NPC 对话编辑器”中填写内容。
3. 点击“复制 YAML”或“下载配置”。
4. 将任务文件放入服务器的 `plugins/CustomQuest/quests/`，对话文件放入 `plugins/CustomQuest/dialogues/`。
5. 在服务器执行 `/cq reload`。

编辑器只生成配置，不会自动连接 Minecraft 服务器，也不会校验 MythicMobs 怪物名、Citizens NPC 是否存在或材料是否适用于当前版本。保存前请确认 ID、NPC 数字 ID、材料名和 Kether 动作拼写正确。
## 读取已有配置

点击“导入 YAML”，选择现有的任务文件或对话文件。编辑器会根据是否存在 `branches` 自动识别类型，并将字段载入对应表单；修改后可继续复制或下载。

导入模块位于 `editor/import.js`，因此使用 `index.html` 时请保持两个文件在同一目录。
