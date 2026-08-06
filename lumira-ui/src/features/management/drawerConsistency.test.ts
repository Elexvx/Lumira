import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = fileURLToPath(new URL('../../', import.meta.url));

const collectComponentFiles = (directory: string): string[] => readdirSync(directory, { withFileTypes: true })
  .flatMap((entry) => {
    const path = `${directory}/${entry.name}`;
    if (entry.isDirectory()) {
      return collectComponentFiles(path);
    }
    return entry.name.endsWith('.tsx') && !entry.name.includes('.test.') ? [path] : [];
  });

describe('global drawer consistency', () => {
  it('routes every application drawer through StandardDrawer', () => {
    const violations = collectComponentFiles(srcRoot)
      .filter((path) => !path.endsWith('/features/management/StandardDrawer.tsx'))
      .filter((path) => {
        const source = readFileSync(path, 'utf8');
        const importsDrawerFromAntd = Array.from(source.matchAll(/import\s*{([\s\S]*?)}\s*from\s*['"]antd['"]/g))
          .some((match) => match[1].split(',').some((name) => name.trim() === 'Drawer'));
        return importsDrawerFromAntd || source.includes('<Drawer');
      });

    expect(violations).toEqual([]);
  });

  it('keeps drawer forms single-column at the shared style boundary', () => {
    const globalStyles = readFileSync(`${srcRoot}/global.css`, 'utf8');

    expect(globalStyles).toContain('.saas-standard-drawer .ant-form:not(.ant-form-inline) .ant-form-item');
    expect(globalStyles).toContain('.saas-standard-drawer .ant-form:not(.ant-form-inline) .ant-row:has(.ant-form-item) > .ant-col');
    expect(globalStyles).toContain('flex: 0 0 100% !important;');
  });
});
