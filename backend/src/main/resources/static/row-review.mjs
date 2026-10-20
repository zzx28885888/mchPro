export function rowKey(row) {
  return `${row.fileId}\u0000${row.sheetName}\u0000${row.rowNumber}`;
}

export function groupIssues(items) {
  return items.map((item) => ({ ...item, key: rowKey(item.row), issues: [...(item.issues || [])] }));
}

export function restoreDecisions(items) {
  return new Map(items.filter((item) => item.decision).map((item) => [item.key, {
    row: item.row,
    action: item.decision.action,
    corrections: { ...(item.decision.corrections || {}) },
  }]));
}

export function buildDecisionSnapshot(items, controls) {
  const decisions = [];
  for (const item of items) {
    const control = controls.get(item.key);
    if (!control || !['CORRECT', 'KEEP', 'SKIP'].includes(control.action)) continue;
    const corrections = Object.fromEntries(Object.entries(control.corrections || {}).filter(([, value]) => value !== ''));
    decisions.push({ row: item.row, action: control.action, corrections });
  }
  return decisions;
}
