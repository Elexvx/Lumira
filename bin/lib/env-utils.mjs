import { readFileSync, existsSync } from 'node:fs';
import { randomBytes } from 'node:crypto';

export function parseEnvFile(filePath) {
  if (!existsSync(filePath)) {
    return {};
  }
  return Object.fromEntries(
    readFileSync(filePath, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const index = line.indexOf('=');
        return [line.slice(0, index).trim(), line.slice(index + 1).trim().replace(/^['"]|['"]$/g, '')];
      })
  );
}

export function setEnvValue(content, key, value) {
  const line = `${key}=${value}`;
  if (new RegExp(`^${key}=`, 'm').test(content)) {
    return content.replace(new RegExp(`^${key}=.*$`, 'm'), line);
  }
  return `${content.trimEnd()}\n${line}\n`;
}

export function randomSecret(prefix) {
  return `${prefix}-${randomBytes(24).toString('hex')}`;
}

export function randomBase64Secret(byteLength = 48) {
  return randomBytes(byteLength).toString('base64');
}

export const defaultCapacityProfiles = {
  tiny: {
    label: '4C4G / small server',
    javaOpts: '-XX:MaxRAMPercentage=58 -XX:InitialRAMPercentage=18 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=96m -Xss512k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom',
    redisMaxmemory: '256mb',
    dockerLogMaxSize: '50m',
    dockerLogMaxFile: '2',
    hikariMaxPoolSize: '4',
    tomcatThreadsMax: '80',
    serviceLimits: {
      SYSTEM_SERVICE_MEM_LIMIT: '768m',
      GATEWAY_SERVICE_MEM_LIMIT: '512m',
      AUTH_SERVICE_MEM_LIMIT: '384m',
      FILE_SERVICE_MEM_LIMIT: '384m',
      MESSAGE_SERVICE_MEM_LIMIT: '384m',
      PLUGIN_SERVICE_MEM_LIMIT: '384m',
      LOCALIZATION_SERVICE_MEM_LIMIT: '320m',
      JOB_EXECUTOR_MEM_LIMIT: '320m',
      XXL_JOB_ADMIN_MEM_LIMIT: '384m',
      API_PROXY_MEM_LIMIT: '128m',
    },
    gatewayQps: {
      SAAS_TRAFFIC_GATEWAY_AUTH_SERVICE_QPS: '120',
      SAAS_TRAFFIC_GATEWAY_FILE_SERVICE_QPS: '80',
      SAAS_TRAFFIC_GATEWAY_MESSAGE_SERVICE_QPS: '80',
      SAAS_TRAFFIC_GATEWAY_PLUGIN_SERVICE_QPS: '50',
      SAAS_TRAFFIC_GATEWAY_LOCALIZATION_SERVICE_QPS: '80',
      SAAS_TRAFFIC_GATEWAY_SYSTEM_SERVICE_QPS: '160',
    },
    smokeConcurrency: 16,
  },
  standard: {
    label: '8G+ / standard server',
    javaOpts: '-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=20 -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -Xss768k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom',
    redisMaxmemory: '512mb',
    dockerLogMaxSize: '100m',
    dockerLogMaxFile: '3',
    hikariMaxPoolSize: '8',
    tomcatThreadsMax: '160',
    serviceLimits: {
      SYSTEM_SERVICE_MEM_LIMIT: '1280m',
      GATEWAY_SERVICE_MEM_LIMIT: '768m',
      AUTH_SERVICE_MEM_LIMIT: '512m',
      FILE_SERVICE_MEM_LIMIT: '512m',
      MESSAGE_SERVICE_MEM_LIMIT: '512m',
      PLUGIN_SERVICE_MEM_LIMIT: '512m',
      LOCALIZATION_SERVICE_MEM_LIMIT: '384m',
      JOB_EXECUTOR_MEM_LIMIT: '384m',
      XXL_JOB_ADMIN_MEM_LIMIT: '512m',
      API_PROXY_MEM_LIMIT: '128m',
    },
    gatewayQps: {
      SAAS_TRAFFIC_GATEWAY_AUTH_SERVICE_QPS: '240',
      SAAS_TRAFFIC_GATEWAY_FILE_SERVICE_QPS: '160',
      SAAS_TRAFFIC_GATEWAY_MESSAGE_SERVICE_QPS: '160',
      SAAS_TRAFFIC_GATEWAY_PLUGIN_SERVICE_QPS: '100',
      SAAS_TRAFFIC_GATEWAY_LOCALIZATION_SERVICE_QPS: '160',
      SAAS_TRAFFIC_GATEWAY_SYSTEM_SERVICE_QPS: '320',
    },
    smokeConcurrency: 32,
  },
};
