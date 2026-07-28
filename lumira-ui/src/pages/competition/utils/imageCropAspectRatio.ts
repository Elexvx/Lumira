export const DEFAULT_IMAGE_CROP_ASPECT_RATIO = '1:1';

export const IMAGE_CROP_ASPECT_RATIO_OPTIONS = [
  { label: '1:1（默认，适合头像）', value: '1:1' },
  { label: '4:3（横向）', value: '4:3' },
  { label: '3:4（竖向）', value: '3:4' },
  { label: '3:2（横向）', value: '3:2' },
  { label: '2:3（竖向）', value: '2:3' },
  { label: '16:9（横屏）', value: '16:9' },
  { label: '9:16（竖屏）', value: '9:16' },
] as const;

const SUPPORTED_IMAGE_CROP_ASPECT_RATIOS = new Set(
  IMAGE_CROP_ASPECT_RATIO_OPTIONS.map((option) => option.value),
);

export const normalizeImageCropAspectRatio = (
  fieldType?: string | null,
  cropAspectRatio?: string | null,
) => {
  if (fieldType?.toUpperCase() !== 'IMAGE') {
    return undefined;
  }
  const normalized = cropAspectRatio?.trim().replace('：', ':');
  return normalized && SUPPORTED_IMAGE_CROP_ASPECT_RATIOS.has(
    normalized as (typeof IMAGE_CROP_ASPECT_RATIO_OPTIONS)[number]['value'],
  )
    ? normalized
    : DEFAULT_IMAGE_CROP_ASPECT_RATIO;
};

export const resolveImageCropAspect = (cropAspectRatio?: string | null) => {
  const normalized = normalizeImageCropAspectRatio('IMAGE', cropAspectRatio)
    || DEFAULT_IMAGE_CROP_ASPECT_RATIO;
  const [width, height] = normalized.split(':').map(Number);
  return width / height;
};
