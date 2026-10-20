import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDecisionSnapshot, groupIssues, restoreDecisions, rowKey } from './src/main/resources/static/row-review.mjs';

test('groups review rows by file, sheet, and one-based row number', () => {
  const item = { row: { fileId: 4, sheetName: 'Data', rowNumber: 8 }, issues: [{ code: 'INVALID_DATE' }] };
  assert.equal(groupIssues([item])[0].key, rowKey(item.row));
});

test('restores saved actions and builds only valid complete row decisions', () => {
  const row = { fileId: 4, sheetName: 'Data', rowNumber: 8 };
  const [item] = groupIssues([{ row, decision: { action: 'CORRECT', corrections: { Date: '2026-01-02' } } }]);
  const restored = restoreDecisions([item]);
  assert.equal(restored.get(item.key).action, 'CORRECT');
  assert.deepEqual(buildDecisionSnapshot([item], restored), [{ row, action: 'CORRECT', corrections: { Date: '2026-01-02' } }]);
  assert.deepEqual(buildDecisionSnapshot([item], new Map()), []);
});

test('does not submit unknown actions or blank correction values', () => {
  const row = { fileId: 4, sheetName: 'Data', rowNumber: 8 };
  const [item] = groupIssues([{ row }]);
  const controls = new Map([[item.key, { row, action: 'APPROVE', corrections: { Date: '' } }]]);
  assert.deepEqual(buildDecisionSnapshot([item], controls), []);
  controls.set(item.key, { row, action: 'CORRECT', corrections: { Date: '', Amount: '4' } });
  assert.deepEqual(buildDecisionSnapshot([item], controls), [{ row, action: 'CORRECT', corrections: { Amount: '4' } }]);
});
