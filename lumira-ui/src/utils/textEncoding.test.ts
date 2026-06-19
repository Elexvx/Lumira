import { describe, expect, it } from 'vitest';
import { repairMojibakePayload, repairMojibakeText } from './textEncoding';

const mojibake = (value: string) => new TextDecoder('windows-1252').decode(new TextEncoder().encode(value));

describe('textEncoding', () => {
  it('repairs UTF-8 text that was decoded as Windows-1252', () => {
    expect(repairMojibakeText(mojibake('平台管理员'))).toBe('平台管理员');
    expect(repairMojibakeText(mojibake('普通用户'))).toBe('普通用户');
  });

  it('keeps normal text unchanged', () => {
    expect(repairMojibakeText('平台管理员')).toBe('平台管理员');
    expect(repairMojibakeText('admin@example.com')).toBe('admin@example.com');
    expect(repairMojibakeText('test')).toBe('test');
  });

  it('repairs nested API payloads without mutating clean fields', () => {
    const payload = {
      code: '00000',
      message: 'success',
      data: {
        records: [
          { id: 2001, roleName: mojibake('平台管理员'), roleCode: 'ADMIN' },
          { id: 2003, roleName: mojibake('普通用户'), roleCode: 'commonuser' },
        ],
      },
    };

    expect(repairMojibakePayload(payload)).toEqual({
      code: '00000',
      message: 'success',
      data: {
        records: [
          { id: 2001, roleName: '平台管理员', roleCode: 'ADMIN' },
          { id: 2003, roleName: '普通用户', roleCode: 'commonuser' },
        ],
      },
    });
  });
});
