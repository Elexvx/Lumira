import { useSyncExternalStore } from 'react';

export type BootstrapPhase = 'idle' | 'health' | 'branding' | 'security' | 'captcha' | 'ready' | 'error';

export interface BootstrapSnapshot {
  phase: BootstrapPhase;
  progress: number;
  title: string;
  description: string;
  retryCount: number;
  retryInMs?: number;
  brandName?: string;
  errorMessage?: string;
  ready: boolean;
  updatedAt: number;
}

const buildInitialSnapshot = (): BootstrapSnapshot => ({
  phase: 'idle',
  progress: 0,
  title: '正在启动系统',
  description: '正在检查后端服务',
  retryCount: 0,
  ready: false,
  updatedAt: Date.now(),
});

let snapshot = buildInitialSnapshot();
const listeners = new Set<() => void>();

const emit = () => {
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

export const getBootstrapSnapshot = () => snapshot;

export const setBootstrapSnapshot = (patch: Partial<BootstrapSnapshot>) => {
  snapshot = {
    ...snapshot,
    ...patch,
    updatedAt: Date.now(),
  };
  emit();
};

export const resetBootstrapSnapshot = () => {
  snapshot = buildInitialSnapshot();
  emit();
};

export const useBootstrapSnapshot = () => useSyncExternalStore(subscribe, getBootstrapSnapshot, getBootstrapSnapshot);

