import test from 'node:test';
import assert from 'node:assert/strict';
import { compareSchemaSnapshots, getWorkflowTemplate, getWorkflowTemplates, normalizeHeader, restoreWorkflowHeaderMappings, suggestUniqueHeaderMatch, suggestWorkflowTemplateFields } from './src/main/resources/static/workflow-templates.mjs';

test('lists the three approved starter templates', () => {
  assert.deepEqual(getWorkflowTemplates().map(({ id }) => id), [
    'monthly_sales_summary',
    'order_reconciliation',
    'multi_file_dedupe_merge',
  ]);
  assert.equal(getWorkflowTemplate('order_reconciliation').workflowType, 'RECONCILE');
});

test('suggests uniquely matched summary fields', () => {
  assert.deepEqual(suggestWorkflowTemplateFields('monthly_sales_summary', [
    { headers: ['Order Date', 'Sales Amount', 'Region'] },
  ]), {
    measureColumn: 'Sales Amount',
    dimensionColumn: 'Region',
    aggregation: 'SUM',
    period: 'MONTH',
    dateColumn: 'Order Date',
    outputFormat: 'date',
  });
});

test('does not suggest ambiguous summary fields', () => {
  assert.deepEqual(suggestWorkflowTemplateFields('monthly_sales_summary', [
    { headers: ['Amount', 'Sales Amount', 'Date', 'Order Date'] },
  ]), {
    aggregation: 'SUM',
  });
});

test('suggests reconciliation fields only when each side has a unique match', () => {
  assert.deepEqual(suggestWorkflowTemplateFields('order_reconciliation', [
    { headers: ['Order No', 'Total Amount'] },
    { headers: ['订单编号', '金额'] },
  ]), {
    leftKey: 'Order No',
    rightKey: '订单编号',
    leftAmount: 'Total Amount',
    rightAmount: '金额',
    tolerance: 0,
  });
});

test('suggests canonical merge mappings across all workbooks', () => {
  assert.deepEqual(suggestWorkflowTemplateFields('multi_file_dedupe_merge', [
    { headers: ['订单号', '销售金额', '下单日期'] },
    { headers: ['Order No', 'Amount', 'Order Date'] },
  ]), {
    headerTargets: [
      { source: 0, header: '订单号', target: 'Order ID' },
      { source: 0, header: '销售金额', target: 'Amount' },
      { source: 0, header: '下单日期', target: 'Date' },
      { source: 1, header: 'Order No', target: 'Order ID' },
      { source: 1, header: 'Amount', target: 'Amount' },
      { source: 1, header: 'Order Date', target: 'Date' },
    ],
    keyFields: ['Order ID'],
    amountFields: ['Amount'],
    dateFields: ['Date'],
    skipBlank: true,
  });
});

test('leaves ambiguous merge categories unmapped', () => {
  assert.deepEqual(suggestWorkflowTemplateFields('multi_file_dedupe_merge', [
    { headers: ['Order No', 'Order ID', 'Amount'] },
    { headers: ['Order No', 'Amount'] },
  ]), {
    headerTargets: [
      { source: 0, header: 'Amount', target: 'Amount' },
      { source: 1, header: 'Amount', target: 'Amount' },
    ],
    keyFields: [],
    amountFields: ['Amount'],
    dateFields: [],
    skipBlank: true,
  });
});

test('normalizes full-width text and common separators without changing returned header names', () => {
  assert.equal(normalizeHeader('  Ｏｒｄｅｒ＿Ｎｏ． '), 'orderno');
  assert.deepEqual(suggestWorkflowTemplateFields('monthly_sales_summary', [
    { headers: ['Ｓａｌｅｓ－Ａｍｏｕｎｔ', 'Ｒｅｇｉｏｎ'] },
  ]), {
    measureColumn: 'Ｓａｌｅｓ－Ａｍｏｕｎｔ',
    dimensionColumn: 'Ｒｅｇｉｏｎ',
    aggregation: 'SUM',
  });
});

test('reports additions, missing columns, and an alias-backed rename', () => {
  const previous = { version: 1, inputs: [{ inputIndex: 0, sheets: [{ name: 'Sheet1', headers: ['订单编号', '销售金额'] }] }] };
  const changes = compareSchemaSnapshots(previous, [{ name: 'Sheet1', headers: ['Order No', '新状态'] }]);

  assert.deepEqual(changes[0], {
    inputIndex: 0,
    sheetChanges: [],
    addedHeaders: ['新状态'],
    missingHeaders: ['销售金额'],
    possibleRenames: [{ from: '订单编号', to: 'Order No' }],
    ambiguousRenames: [],
  });
});

test('keeps duplicate alias candidates ambiguous and reports worksheet renames', () => {
  const previous = { version: 1, inputs: [{ inputIndex: 0, sheets: [{ name: 'Old', headers: ['订单号'] }] }] };
  const changes = compareSchemaSnapshots(previous, [{ name: 'New', headers: ['Order No', 'Order ID'] }]);

  assert.deepEqual(changes[0].sheetChanges, [{ from: 'Old', to: 'New' }]);
  assert.deepEqual(changes[0].possibleRenames, []);
  assert.deepEqual(changes[0].ambiguousRenames, [{ from: '订单号', candidates: ['Order No', 'Order ID'] }]);
});

test('returns no cross-run changes when an old snapshot is absent or unsupported', () => {
  assert.deepEqual(compareSchemaSnapshots(null, [{ name: 'Sheet1', headers: ['a'] }]), []);
  assert.deepEqual(compareSchemaSnapshots({ version: 9, inputs: [] }, [{ name: 'Sheet1', headers: ['a'] }]), []);
});

test('restores historical fields only through unique normalized or alias matches', () => {
  assert.equal(suggestUniqueHeaderMatch('Sales Amount', ['Sales-Amount']), 'Sales-Amount');
  assert.equal(suggestUniqueHeaderMatch('订单编号', ['Order No']), 'Order No');
  assert.equal(suggestUniqueHeaderMatch('订单编号', ['Order No', 'Order ID']), null);
  assert.deepEqual(restoreWorkflowHeaderMappings({ measureColumn: 'Sales Amount', dimensionColumns: ['Region'] }, 'SUMMARY', [
    { headers: ['Sales-Amount', 'Region'] },
  ]), {
    measureColumn: 'Sales-Amount',
    dimensionColumns: ['Region'],
    dateColumn: undefined,
  });
});

test('restores merge source aliases from the matching historical input only', () => {
  const restored = restoreWorkflowHeaderMappings({
    headerAliases: { 'Order ID': ['订单编号'] },
    schemaSnapshot: { version: 1, inputs: [{ inputIndex: 0, sheets: [{ name: 'Sheet1', headers: ['订单编号'] }] }] },
  }, 'MERGE_CLEAN', [{ name: 'Sheet1', headers: ['Order No'] }]);

  assert.deepEqual(restored.headerAliases, { 'Order ID': ['Order No'] });
});
