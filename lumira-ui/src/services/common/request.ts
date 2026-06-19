import { executeEventStream } from './requestCoreStream';
import { executeFileRequest } from './requestCoreFile';
import { executeRequest } from './requestCoreRequest';

export type { RequestOptions } from './requestInternalsTypes';

export const request = executeRequest;
export const requestEventStream = executeEventStream;
export const requestFile = executeFileRequest;
