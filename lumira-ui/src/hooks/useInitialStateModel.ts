import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { useModel } from '@umijs/max';
import type { AppInitialState } from '@/app';
import { getAppInitialState } from '@/app.bootstrap';

export interface InitialStateModel {
  initialState?: AppInitialState;
  setInitialState: Dispatch<SetStateAction<AppInitialState | undefined>>;
}

export const useInitialStateModel = () => {
  const [fallbackInitialState, setFallbackInitialState] = useState<AppInitialState | undefined>(undefined);
  let runtimeModel: InitialStateModel | null = null;

  try {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    runtimeModel = useModel('@@initialState' as never) as unknown as InitialStateModel;
  } catch {
    runtimeModel = null;
  }

  useEffect(() => {
    if (runtimeModel || fallbackInitialState !== undefined) {
      return;
    }

    let disposed = false;
    void getAppInitialState()
      .then((state) => {
        if (!disposed) {
          setFallbackInitialState(state);
        }
      })
      .catch(() => {
        if (!disposed) {
          setFallbackInitialState(undefined);
        }
      });

    return () => {
      disposed = true;
    };
  }, [fallbackInitialState, runtimeModel]);

  const setInitialState = useCallback<Dispatch<SetStateAction<AppInitialState | undefined>>>((updater) => {
    setFallbackInitialState((previous) =>
      typeof updater === 'function' ? (updater as (prev: AppInitialState | undefined) => AppInitialState | undefined)(previous) : updater,
    );
  }, []);

  return runtimeModel || { initialState: fallbackInitialState, setInitialState };
};
