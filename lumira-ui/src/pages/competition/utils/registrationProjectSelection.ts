export type RegistrationProjectBasics = {
  id: number;
  title: string;
  imageUrl?: string | null;
  description?: string | null;
};

export type RegistrationProjectSelectionValues = {
  projectId?: number;
  newProjectTitle?: string;
  newProjectImageUrl?: string;
  newProjectDescription?: string;
};

export const buildRegistrationProjectSelectionValues = (
  project?: RegistrationProjectBasics,
): RegistrationProjectSelectionValues => project
  ? {
      projectId: project.id,
      newProjectTitle: project.title,
      newProjectImageUrl: project.imageUrl || undefined,
      newProjectDescription: project.description || undefined,
    }
  : { projectId: undefined };
