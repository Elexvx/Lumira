const MOJIBAKE_MARKER_PATTERN = /[ÃÂÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]/;
const CJK_PATTERN = /[\u3400-\u9fff]/g;
const WINDOWS_1252_REVERSE_MAP: Record<number, number> = {
  0x20ac: 0x80,
  0x201a: 0x82,
  0x0192: 0x83,
  0x201e: 0x84,
  0x2026: 0x85,
  0x2020: 0x86,
  0x2021: 0x87,
  0x02c6: 0x88,
  0x2030: 0x89,
  0x0160: 0x8a,
  0x2039: 0x8b,
  0x0152: 0x8c,
  0x017d: 0x8e,
  0x2018: 0x91,
  0x2019: 0x92,
  0x201c: 0x93,
  0x201d: 0x94,
  0x2022: 0x95,
  0x2013: 0x96,
  0x2014: 0x97,
  0x02dc: 0x98,
  0x2122: 0x99,
  0x0161: 0x9a,
  0x203a: 0x9b,
  0x0153: 0x9c,
  0x017e: 0x9e,
  0x0178: 0x9f,
};

const countCjk = (value: string) => value.match(CJK_PATTERN)?.length ?? 0;

export const repairMojibakeText = (value: string) => {
  if (!MOJIBAKE_MARKER_PATTERN.test(value)) {
    return value;
  }
  const bytes: number[] = [];
  for (const char of value) {
    const code = char.charCodeAt(0);
    const byte = code <= 0xff ? code : WINDOWS_1252_REVERSE_MAP[code];
    if (byte === undefined) {
      return value;
    }
    bytes.push(byte);
  }
  const repaired = new TextDecoder('utf-8', { fatal: false }).decode(Uint8Array.from(bytes));
  return countCjk(repaired) > countCjk(value) ? repaired : value;
};

export const repairOptionalMojibakeText = <T extends string | null | undefined>(value: T): T | string => {
  return typeof value === 'string' ? repairMojibakeText(value) : value;
};

export const repairMojibakePayload = <T>(value: T): T => {
  if (typeof value === 'string') {
    return repairMojibakeText(value) as T;
  }
  if (!value || typeof value !== 'object') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => repairMojibakePayload(item)) as T;
  }

  const source = value as Record<string, unknown>;
  let changed = false;
  const repaired: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(source)) {
    const nextValue = repairMojibakePayload(item);
    repaired[key] = nextValue;
    changed ||= nextValue !== item;
  }
  return changed ? repaired as T : value;
};
