const databaseRuntimeServices = new Set([
  'lumira-server-blue',
  'lumira-server-green',
  'lumira-async',
  'lumira-job-executor',
]);

export function deploymentRequiresDatabasePreparation(serviceNames) {
  if (!Array.isArray(serviceNames) || serviceNames.length === 0) {
    return true;
  }
  return serviceNames.some((serviceName) => databaseRuntimeServices.has(serviceName));
}
