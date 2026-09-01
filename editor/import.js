(function () {
  function cleanLine(raw) {
    let quote = false, depth = 0;
    for (let i = 0; i < raw.length; i++) {
      const c = raw[i];
      if (c === '"' && raw[i - 1] !== '\\') quote = !quote;
      if (!quote && (c === '[' || c === '{')) depth++;
      if (!quote && (c === ']' || c === '}')) depth--;
      if (c === '#' && !quote && depth === 0 && (i === 0 || /\s/.test(raw[i - 1]))) return raw.slice(0, i).trim();
    }
    return raw.trim();
  }
  function scalar(raw) {
    const value = raw.trim();
    if (!value) return null;
    if (value[0] === '"' && value.at(-1) === '"') {
      try { return JSON.parse(value); } catch { return value.slice(1, -1); }
    }
    if (value[0] === "'" && value.at(-1) === "'") return value.slice(1, -1).replaceAll("''", "'");
    if (value === 'true' || value === 'false') return value === 'true';
    if (value === 'null' || value === '~') return null;
    if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
    if (value.startsWith('[') && value.endsWith(']')) return splitInline(value.slice(1, -1)).map(scalar);
    if (value.startsWith('{') && value.endsWith('}')) {
      const result = {};
      splitInline(value.slice(1, -1)).forEach(pair => {
        const index = pair.indexOf(':');
        if (index > 0) result[pair.slice(0, index).trim()] = scalar(pair.slice(index + 1));
      });
      return result;
    }
    return value;
  }
  function splitInline(text) {
    const result = [];
    let start = 0, quote = false, depth = 0;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (c === '"' && text[i - 1] !== '\\') quote = !quote;
      if (!quote && (c === '[' || c === '{')) depth++;
      if (!quote && (c === ']' || c === '}')) depth--;
      if (c === ',' && !quote && depth === 0) { result.push(text.slice(start, i).trim()); start = i + 1; }
    }
    if (text.slice(start).trim()) result.push(text.slice(start).trim());
    return result;
  }
  function pair(text) {
    let quote = false, depth = 0;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (c === '"' && text[i - 1] !== '\\') quote = !quote;
      if (!quote && (c === '[' || c === '{')) depth++;
      if (!quote && (c === ']' || c === '}')) depth--;
      if (c === ':' && !quote && depth === 0) return [text.slice(0, i).trim().replace(/^['"]|['"]$/g, ''), text.slice(i + 1).trim()];
    }
    return [text.trim(), ''];
  }
  function parseYaml(text) {
    const rows = text.replace(/^\uFEFF/, '').split(/\r?\n/).map(raw => {
      const clean = cleanLine(raw);
      return { indent: clean.length - clean.trimStart().length, text: clean.trim() };
    }).filter(row => row.text);
    function block(position, indent) {
      const list = rows[position]?.indent === indent && rows[position].text.startsWith('-');
      const result = list ? [] : {};
      while (position < rows.length && rows[position].indent === indent && rows[position].text.startsWith('-') === list) {
        const text = rows[position].text;
        if (list) {
          const rest = text.slice(1).trim();
          if (!rest) {
            const child = rows[position + 1] && rows[position + 1].indent > indent ? block(position + 1, rows[position + 1].indent) : [null, position + 1];
            result.push(child[0]); position = child[1]; continue;
          }
          const [key, value] = pair(rest);
          if (rest.includes(':')) {
            const object = {}; object[key] = value ? scalar(value) : null; position++;
            if (rows[position] && rows[position].indent > indent) {
              const child = block(position, rows[position].indent);
              if (child[0] && typeof child[0] === 'object' && !Array.isArray(child[0])) Object.assign(object, child[0]);
              position = child[1];
            }
            result.push(object);
          } else { result.push(scalar(rest)); position++; }
        } else {
          const [key, value] = pair(text);
          if (value === '|' || value === '>') {
            const content = [];
            position++;
            while (rows[position] && rows[position].indent > indent) {
              content.push(rows[position].text);
              position++;
            }
            result[key] = value === '|' ? content.join('\n') : content.join(' ');
          } else if (value) { result[key] = scalar(value); position++; }
          else if (rows[position + 1] && rows[position + 1].indent > indent) { const child = block(position + 1, rows[position + 1].indent); result[key] = child[0]; position = child[1]; }
          else { result[key] = null; position++; }
        }
      }
      return [result, position];
    }
    return rows.length ? block(0, rows[0].indent)[0] : {};
  }
  function textList(value) { return Array.isArray(value) ? value.map(String) : value == null ? [] : [String(value)]; }
  function importedSubmitItem(value) {
    if (typeof value === 'string') return value;
    if (!value || typeof value !== 'object') return '';
    if (value['neige-item'] != null) return `neige-item:${value['neige-item']}:${Number(value.amount) || 1}`;
    if (value.ni != null) return `ni:${value.ni}:${Number(value.amount) || 1}`;
    if (value.item != null) return `${value.item}:${Number(value.amount) || 1}`;
    return JSON.stringify(value);
  }
  function importedObjective(item) {
    const source = item && typeof item === 'object' ? item : { item };
    const common = { amount: Number(source.amount) || 1, display: String(source.name ?? ''), itemName: String(source['item-name'] ?? ''), boardLine: String(source['board-line'] ?? '') };
    if (source['neige-item'] != null || source.ni != null) {
      return { target: String(source['neige-item'] ?? source.ni), kind: 'ni', ...common };
    }
    const parts = String(source.item ?? 'STONE:1').split(':');
    return { target: parts.slice(0, -1).join(':') || parts[0], kind: 'normal', amount: source.amount == null ? Number(parts.at(-1)) || 1 : common.amount, display: common.display, itemName: common.itemName, boardLine: common.boardLine };
  }
  function importQuest(data, file) {
    const q = structuredClone(initial.quest);
    q.file = file.replace(/\.(ya?ml)$/i, ''); q.id = String(data['quest-id'] || q.file); q.name = String(data.name ?? ''); q.type = String(data.type || 'describe'); q.description = textList(data.description);
    let raw = data.objectives;
    if (!Array.isArray(raw)) raw = q.type === 'kill_mob' && data.mob ? [{ mob: data.mob, amount: data.amount }] : q.type === 'submit_item' && data.item ? [{ item: data.item }] : [];
    q.objectives = raw.map(item => { if (q.type === 'kill_mob') return { target: String(item.mob ?? ''), amount: Number(item.amount) || 1, display: String(item.name ?? ''), itemName: '', boardLine: String(item['board-line'] ?? ''), kind: 'normal' }; return importedObjective(item); });
    q.conditionCommands = textList(data['condition-commands']); q.repeatable = Boolean(data.repeatable); q.cooldown = Number(data.cooldown) || 0; q.boardTitle = String(data['board-title'] ?? ''); q.boardLines = textList(data['board-line']); q.navigate = String(data.navigate ?? ''); return q;
  }
  function importedCondition(raw) {
    const data = [], papi = [];
    const text = String(raw ?? '').replace(/^check\s+profile\s+data\s+/i, '').trim();
    text.split(/\s*,\s*/).map(item => item.trim()).filter(Boolean).forEach(item => (item.includes('%') ? papi : data).push(item));
    return { data, papi };
  }
  function importedThen(raw) {
    const kether = [], result = { goto: '', close: false };
    String(raw ?? '').split(/\r?\n/).map(item => item.trim()).filter(Boolean).forEach(item => {
      if (/^goto\s+/i.test(item)) result.goto = item.replace(/^goto\s+/i, '').trim();
      else if (item.toLowerCase() === 'close') result.close = true;
      else kether.push(item);
    });
    result.kether = kether;
    return result;
  }
  function importedOption(option, nodeFormat) {
    const then = importedThen(option.then ?? option.kether);
    return {
      text: String(option.reply ?? option.text ?? ''),
      mode: option['accept-quest'] ? 'accept' : option['submit-quest'] ? 'submit' : 'none',
      quest: String(option['accept-quest'] || option['submit-quest'] || ''),
      submitItems: textList(option['submit-items']).map(importedSubmitItem),
      kether: then.kether,
      goto: then.goto,
      close: nodeFormat ? then.close : option.close !== false
    };
  }
  function importedNode(id, node, conditions) {
    const players = Array.isArray(node.player) ? node.player : [];
    return {
      id,
      defaultBranch: Boolean(node.default),
      data: conditions.data,
      papi: conditions.papi,
      lines: textList(node.npc),
      options: players.map(option => importedOption(option || {}, true))
    };
  }
  function importDialogue(data, file) {
    const d = structuredClone(initial.dialogue);
    d.file = file.replace(/\.(ya?ml)$/i, '');
    const npcValue = data['npc id'] ?? data.npc;
    d.npcIds = Array.isArray(npcValue) ? npcValue.join(',') : String(npcValue ?? '');
    d.title = String(data.title ?? '');
    if (data.when || data['npc id'] != null) {
      const conditions = {};
      (Array.isArray(data.when) ? data.when : []).forEach(entry => {
        if (!entry || typeof entry !== 'object' || !entry.open) return;
        conditions[String(entry.open)] = importedCondition(entry.if);
      });
      const reserved = new Set(['title', 'npc', 'npc id', 'when', 'default-data', 'branches']);
      d.branches = Object.entries(data)
        .filter(([id, node]) => !reserved.has(id) && node && typeof node === 'object' && !Array.isArray(node))
        .map(([id, node]) => importedNode(id, node, conditions[id] || { data: [], papi: [] }));
      return d;
    }
    d.branches = Object.entries(data.branches || {}).map(([id, branch]) => ({
      id,
      defaultBranch: Boolean(branch.default),
      data: textList(branch.data),
      papi: textList(branch.papi),
      lines: textList(branch.lines),
      options: Object.values(branch.options || {}).map(option => importedOption(option, false))
    }));
    return d;
  }
  function importConfig(text, file) {
    const data = parseYaml(text);
    if (data && (data.branches || data['npc id'] != null || data.when)) { state.dialogue = importDialogue(data, file); tab = 'dialogue'; }
    else { state.quest = importQuest(data, file); tab = 'quest'; }
    document.querySelectorAll('.tab').forEach(button => button.classList.toggle('active', button.dataset.tab === tab));
    render(); $('#status').textContent = `已读取 ${file}`;
  }
  $('#import').onclick = () => $('#file').click();
  $('#file').onchange = async event => { const file = event.target.files[0]; if (!file) return; try { importConfig(await file.text(), file.name); } catch (error) { $('#status').textContent = `读取失败：${error.message}`; } event.target.value = ''; };
})();
