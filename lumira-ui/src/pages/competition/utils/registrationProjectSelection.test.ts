import { describe, expect, it } from 'vitest';

import { buildRegistrationProjectSelectionValues } from './registrationProjectSelection';

describe('registration project selection', () => {
  it('reuses the basic information of an existing project', () => {
    expect(buildRegistrationProjectSelectionValues({
      id: 42,
      title: 'Existing project',
      imageUrl: '/uploads/project.png',
      description: 'Existing description',
    })).toEqual({
      projectId: 42,
      newProjectTitle: 'Existing project',
      newProjectImageUrl: '/uploads/project.png',
      newProjectDescription: 'Existing description',
    });
  });

  it('clears only the existing-project association when switching to a new project', () => {
    expect(buildRegistrationProjectSelectionValues()).toEqual({ projectId: undefined });
  });
});
