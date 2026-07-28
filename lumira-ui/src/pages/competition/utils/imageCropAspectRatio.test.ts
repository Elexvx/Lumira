import { describe, expect, it } from 'vitest';
import {
  DEFAULT_IMAGE_CROP_ASPECT_RATIO,
  normalizeImageCropAspectRatio,
  resolveImageCropAspect,
} from './imageCropAspectRatio';

describe('image crop aspect ratio', () => {
  it('defaults legacy image fields to a square crop', () => {
    expect(normalizeImageCropAspectRatio('IMAGE')).toBe(DEFAULT_IMAGE_CROP_ASPECT_RATIO);
    expect(resolveImageCropAspect()).toBe(1);
  });

  it('keeps a supported configured ratio', () => {
    expect(normalizeImageCropAspectRatio('image', '3:4')).toBe('3:4');
    expect(resolveImageCropAspect('3:4')).toBe(0.75);
  });

  it('accepts a full-width colon and rejects unsupported values', () => {
    expect(normalizeImageCropAspectRatio('IMAGE', ' 16：9 ')).toBe('16:9');
    expect(normalizeImageCropAspectRatio('IMAGE', '7:5')).toBe(DEFAULT_IMAGE_CROP_ASPECT_RATIO);
  });

  it('removes crop metadata from non-image fields', () => {
    expect(normalizeImageCropAspectRatio('TEXT', '1:1')).toBeUndefined();
  });
});
