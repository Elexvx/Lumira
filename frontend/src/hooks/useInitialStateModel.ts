import type { Dispatch, SetStateAction } from 'react';
import { useModel } from '@umijs/max';
import type { AppInitialState } from '@/app';

export interface InitialStateModel {
  initialState?: AppInitialState;
  setInitialState: Dispatch<SetStateAction<AppInitialState | undefined>>;
}

export const useInitialStateModel = () =>
  useModel('@@initialState' as never) as unknown as InitialStateModel;
