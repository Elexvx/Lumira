import assert from 'node:assert/strict';

const run = async () => {
  Object.assign(globalThis, {
    window: {
      clearTimeout,
      location: { origin: 'http://localhost:8000' },
      setTimeout,
    },
  });

  const { ErrorCode } = await import('../src/enums/errorCode');
  const { request } = await import('../src/services/common/request');
  const { isApiResponse } = await import('../src/services/common/requestInternalsResponse');
  const { ApiRequestError } = await import('../src/services/common/requestInternalsTypes');

  assert.equal(
    isApiResponse({
      code: 'B0001',
      message: '知识库名称已存在',
      userMessage: '知识库名称已存在',
      requestId: 'req-duplicate',
    }),
    true,
    'error ApiResponse without data should still be recognized so backend messages are shown',
  );

  assert.equal(
    isApiResponse({
      code: '0',
      message: 'success',
      data: { id: 1 },
    }),
    true,
    'success ApiResponse with data should still be recognized',
  );

  assert.equal(isApiResponse({ message: 'missing code' }), false, 'payload without code is not an ApiResponse');

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: ErrorCode.BIZ_ERROR,
        message: '知识库名称已存在',
        userMessage: '知识库名称已存在',
        requestId: 'req-duplicate',
      }),
      {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      },
    );

  try {
    await assert.rejects(
      () =>
        request('/ai/knowledge-bases', {
          method: 'POST',
          data: { name: 'test', status: 'ENABLED', visibilityScope: 'TENANT' },
          skipAuth: true,
          silent: true,
        }),
      (error: unknown) => {
        assert.equal(error instanceof ApiRequestError, true, 'duplicate response should become ApiRequestError');
        const apiError = error as InstanceType<typeof ApiRequestError>;
        assert.equal(apiError.code, ErrorCode.BIZ_ERROR);
        assert.equal(apiError.message, '知识库名称已存在');
        assert.equal(apiError.userMessage, '知识库名称已存在');
        assert.equal(apiError.httpStatus, 409);
        return true;
      },
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  console.log('api-response-shape-smoke: ok');
};

void run();
