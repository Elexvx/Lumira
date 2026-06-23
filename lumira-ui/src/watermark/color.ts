const clampAlpha = (value: unknown) => {
  const numeric = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(numeric)) {
    return 1;
  }
  return Math.max(0, Math.min(1, numeric));
};

const toHexPair = (value: string) => {
  const duplicated = value.length === 1 ? `${value}${value}` : value;
  return Number.parseInt(duplicated, 16);
};

export const applyWatermarkOpacity = (color: string | undefined, opacity: number | undefined) => {
  const normalizedColor = (color || '').trim();
  if (!normalizedColor) {
    return normalizedColor;
  }

  const alpha = clampAlpha(opacity);
  const rgbaMatch = normalizedColor.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*[\d.]+)?\s*\)$/i);
  if (rgbaMatch) {
    const [, red, green, blue] = rgbaMatch;
    return `rgba(${Math.round(Number(red))},${Math.round(Number(green))},${Math.round(Number(blue))},${alpha})`;
  }

  const hexMatch = normalizedColor.match(/^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})(?:\s*,\s*[\d.]+%)?$/i);
  if (hexMatch) {
    const hex = hexMatch[1];
    const red = hex.length === 3 ? toHexPair(hex[0]) : Number.parseInt(hex.slice(0, 2), 16);
    const green = hex.length === 3 ? toHexPair(hex[1]) : Number.parseInt(hex.slice(2, 4), 16);
    const blue = hex.length === 3 ? toHexPair(hex[2]) : Number.parseInt(hex.slice(4, 6), 16);
    return `rgba(${red},${green},${blue},${alpha})`;
  }

  return normalizedColor;
};
