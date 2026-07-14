import http from 'node:http';

const commit = process.env.GIT_COMMIT || 'unknown';
const server = http.createServer((request, response) => {
  const reportedCommit = process.env.FAIL_PUBLIC_VERSION === 'true' && request.headers['x-e2e-public'] === 'true'
    ? 'invalid-public-build'
    : commit;
  response.setHeader('content-type', 'application/json');
  if (request.url === '/actuator/health/readiness') {
    response.statusCode = 401;
    response.end(JSON.stringify({ status: 'UNAUTHORIZED' }));
    return;
  }
  if (request.url === '/actuator/health' || request.url === '/api/health') {
    response.end(JSON.stringify({ status: 'UP', commit: reportedCommit }));
    return;
  }
  if (request.url === '/api/v2/runtime/version' || request.url === '/api/version' || request.url === '/probe') {
    response.end(JSON.stringify({ commitId: reportedCommit, version: process.env.APP_VERSION || 'test' }));
    return;
  }
  response.statusCode = 404;
  response.end(JSON.stringify({ error: 'not found' }));
});

server.listen(8080, '0.0.0.0');
process.on('SIGTERM', () => server.close(() => process.exit(0)));
