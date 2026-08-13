import { history, useParams } from '@umijs/max';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import { getCompetitionWorkspace } from '@/services/competition/api';
import type {
  CompetitionWorkspaceCapability,
  CompetitionWorkspaceModule,
  CompetitionWorkspaceRecord,
} from '@/services/competition/types';
import {
  competitionWorkspacePath,
  normalizeCompetitionUuid,
} from './competitionWorkspaceRoutes';
import { writeLastCompetitionWorkspaceUuid } from './competitionWorkspaceStorage';

interface CompetitionWorkspaceContextValue {
  competitionUuid?: string;
  workspace?: CompetitionWorkspaceRecord;
  loading: boolean;
  error?: Error;
  can: (capability: CompetitionWorkspaceCapability) => boolean;
  canOpen: (module: CompetitionWorkspaceModule) => boolean;
  navigateToModule: (module: CompetitionWorkspaceModule, replace?: boolean) => void;
  refresh: () => void;
}

const CompetitionWorkspaceContext = createContext<CompetitionWorkspaceContextValue | null>(null);

export const CompetitionWorkspaceProvider = ({ children }: PropsWithChildren) => {
  const params = useParams<{ competitionUuid?: string }>();
  const routeUuid = normalizeCompetitionUuid(params.competitionUuid);
  const [workspace, setWorkspace] = useState<CompetitionWorkspaceRecord>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const [refreshToken, setRefreshToken] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const sequence = ++requestSequence.current;
    const controller = new AbortController();
    setWorkspace(undefined);
    setError(undefined);
    setLoading(true);

    if (!routeUuid) {
      setLoading(false);
      setError(new Error('赛事 UUID 格式无效'));
      return () => controller.abort();
    }

    getCompetitionWorkspace(routeUuid, { signal: controller.signal, silent: true })
      .then((nextWorkspace) => {
        if (controller.signal.aborted || sequence !== requestSequence.current) return;
        setWorkspace(nextWorkspace);
        writeLastCompetitionWorkspaceUuid(routeUuid);
      })
      .catch((nextError) => {
        if (controller.signal.aborted || sequence !== requestSequence.current) return;
        setError(nextError instanceof Error ? nextError : new Error('赛事工作空间加载失败'));
      })
      .finally(() => {
        if (!controller.signal.aborted && sequence === requestSequence.current) setLoading(false);
      });

    return () => controller.abort();
  }, [refreshToken, routeUuid]);

  const can = useCallback((capability: CompetitionWorkspaceCapability) =>
    Boolean(workspace?.capabilities?.includes(capability)), [workspace]);
  const canOpen = useCallback((module: CompetitionWorkspaceModule) =>
    Boolean(workspace?.allowedModules?.includes(module)), [workspace]);
  const navigateToModule = useCallback((module: CompetitionWorkspaceModule, replace = false) => {
    if (!routeUuid) return;
    const target = competitionWorkspacePath(routeUuid, module);
    if (replace) history.replace(target);
    else history.push(target);
  }, [routeUuid]);
  const refresh = useCallback(() => setRefreshToken((value) => value + 1), []);

  const value = useMemo<CompetitionWorkspaceContextValue>(() => ({
    competitionUuid: routeUuid,
    workspace,
    loading,
    error,
    can,
    canOpen,
    navigateToModule,
    refresh,
  }), [can, canOpen, error, loading, navigateToModule, refresh, routeUuid, workspace]);

  return <CompetitionWorkspaceContext.Provider value={value}>{children}</CompetitionWorkspaceContext.Provider>;
};

export const useCompetitionWorkspace = () => {
  const context = useContext(CompetitionWorkspaceContext);
  if (!context) throw new Error('useCompetitionWorkspace must be used inside CompetitionWorkspaceProvider');
  return context;
};

export const useOptionalCompetitionWorkspace = () => useContext(CompetitionWorkspaceContext);
