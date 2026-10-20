const templates = [
  {
    id: 'monthly_sales_summary',
    label: '月度销售汇总 / Monthly sales summary',
    description: '按月份和地区汇总销售额。 / Summarize sales by month and region.',
    workflowType: 'SUMMARY',
    prompt: '按月份和地区汇总销售额，并检查日期与金额字段。 / Summarize sales by month and region, and check the date and amount fields.',
  },
  {
    id: 'order_reconciliation',
    label: '两表订单对账 / Two-workbook order reconciliation',
    description: '按订单编号匹配两份工作簿，并比较金额。 / Match two workbooks by order number and compare amounts.',
    workflowType: 'RECONCILE',
    prompt: '按订单编号匹配两份工作簿，并比较订单金额。 / Match the workbooks by order number and compare order amounts.',
  },
  {
    id: 'multi_file_dedupe_merge',
    label: '多文件去重合并 / Multi-file dedupe merge',
    description: '合并多份订单表，按订单编号去重并跳过空行。 / Merge order files, deduplicate by order number, and skip blank rows.',
    workflowType: 'MERGE_CLEAN',
    prompt: '合并订单工作簿，按订单编号去重并保留可用数据。 / Merge the order workbooks, deduplicate by order number, and retain available data.',
  },
];

const aliases = {
  amount: ['amount', 'sales amount', 'total amount', '订单金额', '销售金额', '金额', '合计金额'],
  date: ['date', 'order date', '订单日期', '下单日期', '日期', '交易日期'],
  dimension: ['region', '地区', '区域'],
  orderKey: ['order no', 'order number', 'order id', '订单号', '订单编号', '订单ID'],
};

function normalize(value) {
  return String(value ?? '').normalize('NFKC').trim().toLowerCase().replace(/[\s\p{P}\p{S}_]+/gu, '');
}

export function normalizeHeader(value) {
  return normalize(value);
}

function aliasGroups(header) {
  const normalized = normalize(header);
  return Object.entries(aliases)
    .filter(([, values]) => values.some((value) => normalize(value) === normalized))
    .map(([group]) => group);
}

export function suggestUniqueHeaderMatch(previousHeader, currentHeaders) {
  const exact = currentHeaders.filter((header) => normalize(header) === normalize(previousHeader));
  if (exact.length === 1) return exact[0];
  if (exact.length > 1) return null;
  const groups = aliasGroups(previousHeader);
  if (groups.length !== 1) return null;
  const matches = currentHeaders.filter((header) => aliasGroups(header).includes(groups[0]));
  return matches.length === 1 ? matches[0] : null;
}

export function restoreWorkflowHeaderMappings(options, workflowType, schemas) {
  const restored = { ...(options || {}) };
  const remap = (header, schemaIndex) => {
    if (typeof header !== 'string' || !schemas[schemaIndex]) return header;
    return suggestUniqueHeaderMatch(header, schemas[schemaIndex].headers || []) || header;
  };
  const remapMapValues = (mapping, schemaIndex) => Object.fromEntries(
    Object.entries(mapping || {}).map(([key, value]) => [key, remap(value, schemaIndex)]),
  );
  if (workflowType === 'SUMMARY') {
    restored.measureColumn = remap(restored.measureColumn, 0);
    restored.dateColumn = remap(restored.dateColumn, 0);
    restored.dimensionColumns = (restored.dimensionColumns || []).map((header) => remap(header, 0));
  } else if (workflowType === 'RECONCILE') {
    restored.leftKeyMappings = remapMapValues(restored.leftKeyMappings, 0);
    restored.leftValueMappings = remapMapValues(restored.leftValueMappings, 0);
    restored.rightKeyMappings = remapMapValues(restored.rightKeyMappings, 1);
    restored.rightValueMappings = remapMapValues(restored.rightValueMappings, 1);
  } else if (workflowType === 'MERGE_CLEAN' && options?.schemaSnapshot?.version === 1) {
    const remappedAliases = {};
    const oldInputs = Array.isArray(options.schemaSnapshot.inputs) ? options.schemaSnapshot.inputs : [];
    for (const [target, oldHeaders] of Object.entries(options.headerAliases || {})) {
      const sources = [];
      for (const oldHeader of Array.isArray(oldHeaders) ? oldHeaders : []) {
        for (const schema of schemas) {
          const oldInput = oldInputs.find((input) => input.inputIndex === schemas.indexOf(schema));
          const oldSheet = oldInput?.sheets?.find((sheet) => sheet.headers?.includes(oldHeader));
          if (!oldSheet) continue;
          const matched = suggestUniqueHeaderMatch(oldHeader, schema.headers || []);
          if (matched && !sources.includes(matched)) sources.push(matched);
        }
      }
      if (sources.length) remappedAliases[target] = sources;
    }
    restored.headerAliases = remappedAliases;
  }
  return restored;
}

function uniqueHeader(headers, candidates) {
  const accepted = new Set(candidates.map(normalize));
  const matches = headers.filter((header) => accepted.has(normalize(header)));
  return matches.length === 1 ? matches[0] : null;
}

function uniqueAcrossSchemas(schemas, candidates) {
  if (!schemas.length) return null;
  const matches = schemas.map((schema) => uniqueHeader(schema.headers || [], candidates));
  return matches.every(Boolean) ? matches : null;
}

export function getWorkflowTemplates() {
  return templates.map((template) => ({ ...template }));
}

export function getWorkflowTemplate(id) {
  return templates.find((template) => template.id === id) || null;
}

export function compareSchemaSnapshots(previous, currentSchemas) {
  if (previous?.version !== 1 || !Array.isArray(previous.inputs) || !Array.isArray(currentSchemas)) return [];
  const changes = currentSchemas.map((current, inputIndex) => {
    const previousInput = previous.inputs.find((input) => input.inputIndex === inputIndex);
    if (!previousInput || !Array.isArray(previousInput.sheets)) return {
      inputIndex, sheetChanges: [], addedHeaders: [...(current.headers || [])], missingHeaders: [], possibleRenames: [], ambiguousRenames: [],
    };
    let previousSheet = previousInput.sheets.find((sheet) => sheet.name === current.name);
    const sheetChanges = [];
    if (!previousSheet && previousInput.sheets.length === 1) {
      previousSheet = previousInput.sheets[0];
      if (previousSheet.name !== current.name) sheetChanges.push({ from: previousSheet.name, to: current.name });
    }
    if (!previousSheet) return {
      inputIndex, sheetChanges: previousInput.sheets.map((sheet) => ({ from: sheet.name, to: null })),
      addedHeaders: [...(current.headers || [])], missingHeaders: previousInput.sheets.flatMap((sheet) => sheet.headers || []),
      possibleRenames: [], ambiguousRenames: [],
    };
    const oldRemaining = [...(previousSheet.headers || [])];
    const newRemaining = [...(current.headers || [])];
    for (const oldHeader of [...oldRemaining]) {
      const match = newRemaining.findIndex((newHeader) => normalize(newHeader) === normalize(oldHeader));
      if (match >= 0) {
        oldRemaining.splice(oldRemaining.indexOf(oldHeader), 1);
        newRemaining.splice(match, 1);
      }
    }
    const possibleRenames = [];
    const ambiguousRenames = [];
    const candidatesByOld = oldRemaining.map((header) => ({
      header,
      candidates: newRemaining.filter((added) => aliasGroups(header).some((group) => aliasGroups(added).includes(group))),
    }));
    for (const old of candidatesByOld) {
      if (!old.candidates.length) continue;
      const reverseCount = (candidate) => candidatesByOld.filter((item) => item.candidates.includes(candidate)).length;
      if (old.candidates.length === 1 && reverseCount(old.candidates[0]) === 1) {
        possibleRenames.push({ from: old.header, to: old.candidates[0] });
      } else {
        ambiguousRenames.push({ from: old.header, candidates: old.candidates });
      }
    }
    const renamedFrom = new Set(possibleRenames.map((rename) => rename.from));
    const renamedTo = new Set(possibleRenames.map((rename) => rename.to));
    return {
      inputIndex,
      sheetChanges,
      addedHeaders: newRemaining.filter((header) => !renamedTo.has(header)),
      missingHeaders: oldRemaining.filter((header) => !renamedFrom.has(header)),
      possibleRenames,
      ambiguousRenames,
    };
  });
  for (const oldInput of previous.inputs) {
    if (Number.isInteger(oldInput.inputIndex) && oldInput.inputIndex >= currentSchemas.length) {
      changes.push({ inputIndex: oldInput.inputIndex, inputRemoved: true, sheetChanges: [],
        addedHeaders: [], missingHeaders: (oldInput.sheets || []).flatMap((sheet) => sheet.headers || []),
        possibleRenames: [], ambiguousRenames: [] });
    }
  }
  return changes;
}

export function suggestWorkflowTemplateFields(templateId, schemas) {
  if (!Array.isArray(schemas) || !schemas.length) return {};
  if (templateId === 'monthly_sales_summary') {
    const suggestions = { aggregation: 'SUM' };
    const amount = uniqueHeader(schemas[0].headers || [], aliases.amount);
    const dimension = uniqueHeader(schemas[0].headers || [], aliases.dimension);
    const date = uniqueHeader(schemas[0].headers || [], aliases.date);
    if (amount) suggestions.measureColumn = amount;
    if (dimension) suggestions.dimensionColumn = dimension;
    if (date) Object.assign(suggestions, { period: 'MONTH', dateColumn: date, outputFormat: 'date' });
    return suggestions;
  }
  if (templateId === 'order_reconciliation') {
    if (schemas.length !== 2) return {};
    const keys = schemas.map((schema) => uniqueHeader(schema.headers || [], aliases.orderKey));
    const amounts = schemas.map((schema) => uniqueHeader(schema.headers || [], aliases.amount));
    const suggestions = { tolerance: 0 };
    if (keys.every(Boolean)) Object.assign(suggestions, { leftKey: keys[0], rightKey: keys[1] });
    if (amounts.every(Boolean)) Object.assign(suggestions, { leftAmount: amounts[0], rightAmount: amounts[1] });
    return suggestions;
  }
  if (templateId === 'multi_file_dedupe_merge') {
    const headerTargets = [];
    const keyHeaders = uniqueAcrossSchemas(schemas, aliases.orderKey);
    const amountHeaders = uniqueAcrossSchemas(schemas, aliases.amount);
    const dateHeaders = uniqueAcrossSchemas(schemas, aliases.date);
    const addCategory = (matches, target) => {
      if (matches) matches.forEach((header, source) => headerTargets.push({ source, header, target }));
    };
    addCategory(keyHeaders, 'Order ID');
    addCategory(amountHeaders, 'Amount');
    addCategory(dateHeaders, 'Date');
    headerTargets.sort((left, right) => left.source - right.source
      || schemas[left.source].headers.indexOf(left.header) - schemas[right.source].headers.indexOf(right.header));
    return {
      headerTargets,
      keyFields: keyHeaders ? ['Order ID'] : [],
      amountFields: amountHeaders ? ['Amount'] : [],
      dateFields: dateHeaders ? ['Date'] : [],
      skipBlank: true,
    };
  }
  return {};
}
