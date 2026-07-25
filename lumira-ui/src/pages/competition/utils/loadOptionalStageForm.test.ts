import { describe, expect, it, vi } from 'vitest';
import type { CompetitionStageFormRecord, CompetitionStageRecord } from '@/services/competition/types';
import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { loadOptionalPreliminaryStageForm } from './loadOptionalStageForm';

const stage = (id: number, stageCode: string): CompetitionStageRecord => ({
  id,
  competitionId: 7,
  stageCode,
  stageName: stageCode,
  status: 'ENABLED',
  sort: id,
});

const form: CompetitionStageFormRecord = {
  id: 21,
  competitionId: 7,
  stageId: 12,
  formName: '初赛材料',
  formSchemaJson: '{}',
  version: 1,
  status: 'ENABLED',
};

describe('loadOptionalPreliminaryStageForm', () => {
  it('优先加载初赛阶段表单', async () => {
    const listStages = vi.fn().mockResolvedValue([stage(11, 'FINAL'), stage(12, 'PRELIMINARY')]);
    const getStageForm = vi.fn().mockResolvedValue(form);

    await expect(loadOptionalPreliminaryStageForm(7, listStages, getStageForm)).resolves.toEqual(form);
    expect(getStageForm).toHaveBeenCalledWith(12);
  });

  it('没有初赛时使用第一个阶段', async () => {
    const listStages = vi.fn().mockResolvedValue([stage(11, 'FINAL')]);
    const getStageForm = vi.fn().mockResolvedValue({ ...form, stageId: 11 });

    await loadOptionalPreliminaryStageForm(7, listStages, getStageForm);
    expect(getStageForm).toHaveBeenCalledWith(11);
  });

  it('没有阶段时返回 undefined', async () => {
    await expect(loadOptionalPreliminaryStageForm(7, vi.fn().mockResolvedValue([]), vi.fn())).resolves.toBeUndefined();
  });

  it('阶段列表加载失败时向上抛出真实错误', async () => {
    await expect(
      loadOptionalPreliminaryStageForm(7, vi.fn().mockRejectedValue(new Error('403')), vi.fn()),
    ).rejects.toThrow('403');
  });

  it('阶段表单不存在时降级为 undefined', async () => {
    await expect(
      loadOptionalPreliminaryStageForm(
        7,
        vi.fn().mockResolvedValue([stage(12, 'PRELIMINARY')]),
        vi.fn().mockRejectedValue(new ApiRequestError(
          ErrorCode.NOT_FOUND,
          'missing form',
          { httpStatus: 404 },
        )),
      ),
    ).resolves.toBeUndefined();
  });

  it('阶段表单服务异常时向上抛出真实错误', async () => {
    await expect(
      loadOptionalPreliminaryStageForm(
        7,
        vi.fn().mockResolvedValue([stage(12, 'PRELIMINARY')]),
        vi.fn().mockRejectedValue(new Error('500')),
      ),
    ).rejects.toThrow('500');
  });
});
