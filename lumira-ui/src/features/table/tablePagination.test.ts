import { describe, expect, it } from 'vitest';
import { normalizeTablePagination } from './tablePagination';

describe('normalizeTablePagination', () => {
  it('preserves disabled pagination', () => {
    expect(normalizeTablePagination(false, false)).toBe(false);
  });

  it('keeps desktop pagination uncontrolled while honoring the requested page size', () => {
    expect(normalizeTablePagination({ pageSize: 20, showSizeChanger: true }, false)).toEqual({
      defaultPageSize: 20,
      showSizeChanger: true,
    });
  });

  it('uses compact pagination on mobile', () => {
    expect(normalizeTablePagination({ pageSize: 20, showSizeChanger: true }, true)).toEqual({
      defaultPageSize: 20,
      showSizeChanger: false,
      simple: true,
    });
  });

  it('preserves controlled pagination state', () => {
    expect(normalizeTablePagination({ current: 3, pageSize: 50 }, false)).toEqual({
      current: 3,
      pageSize: 50,
      defaultPageSize: 50,
    });
  });
});
