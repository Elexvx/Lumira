#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const contractsRoot = path.join(repoRoot, 'docs', 'events', 'contracts');
const requiredEnvelopeFields = [
  'eventId', 'eventType', 'sourceModule', 'producer', 'aggregateId',
  'schemaVersion', 'occurredAt', 'payload',
];

export function canonicalContract(source) {
  return source
    .replace(/\r\n?/gu, '\n')
    .split('\n')
    .filter((line) => !/^schemaDigest:\s*/u.test(line))
    .map((line) => line.replace(/\s+$/u, ''))
    .join('\n')
    .replace(/\n+$/u, '')
    .concat('\n');
}

export function contractDigest(source) {
  return `sha256:${createHash('sha256').update(canonicalContract(source), 'utf8').digest('hex')}`;
}

export function parseContract(relativePath, source) {
  const filename = relativePath.replaceAll('\\', '/');
  const identity = /^([a-z0-9-]+)\/([A-Za-z0-9_-]+)\.v(\d+)\.yaml$/u.exec(filename);
  if (!identity) throw new Error(`${filename}: filename must be <domain>/<EVENT>.v<version>.yaml`);
  const eventType = scalar(source, 'eventType', filename);
  const schemaVersion = positiveInteger(scalar(source, 'schemaVersion', filename), `${filename}: schemaVersion`);
  const sourceModule = scalar(source, 'sourceModule', filename);
  const producer = scalar(source, 'producer', filename);
  const schemaDigest = scalar(source, 'schemaDigest', filename);
  const mode = nestedScalar(source, 'mode', filename);
  const requiredFields = list(source, 'requiredFields', filename);
  const payloadTypes = map(source, 'payloadTypes', filename);
  if (eventType !== identity[2]) throw new Error(`${filename}: eventType does not match filename`);
  if (schemaVersion !== Number(identity[3])) throw new Error(`${filename}: schemaVersion does not match filename`);
  if (!/^[A-Za-z][A-Za-z0-9_.-]{0,127}$/u.test(eventType)) throw new Error(`${filename}: eventType is invalid`);
  if (!/^[a-z][a-z0-9-]{0,63}$/u.test(sourceModule)) throw new Error(`${filename}: sourceModule is invalid`);
  if (!/^[A-Za-z][A-Za-z0-9_.-]{0,127}$/u.test(producer)) throw new Error(`${filename}: producer is invalid`);
  if (mode !== 'backward') throw new Error(`${filename}: compatibility.mode must be backward`);
  if (requiredFields.length !== new Set(requiredFields).size) throw new Error(`${filename}: requiredFields contains duplicates`);
  for (const field of requiredEnvelopeFields) {
    if (!requiredFields.includes(field)) throw new Error(`${filename}: requiredFields is missing ${field}`);
    if (!payloadTypes[field]) throw new Error(`${filename}: payloadTypes is missing ${field}`);
  }
  if (!/^sha256:[0-9a-f]{64}$/u.test(schemaDigest)) throw new Error(`${filename}: schemaDigest is invalid`);
  if (schemaDigest !== contractDigest(source)) throw new Error(`${filename}: schemaDigest does not match canonical contract`);
  return {
    relativePath: filename,
    domain: identity[1],
    eventType,
    schemaVersion,
    sourceModule,
    producer,
    schemaDigest,
    requiredFields,
    payloadTypes,
  };
}

export function validateContractSources(entries) {
  const contracts = entries.map(({ relativePath, source }) => parseContract(relativePath, source));
  const identities = new Set();
  for (const contract of contracts) {
    const identity = `${contract.eventType}\u0000${contract.schemaVersion}`;
    if (identities.has(identity)) throw new Error(`${contract.relativePath}: duplicate event contract version`);
    identities.add(identity);
  }
  const byEventType = new Map();
  for (const contract of contracts) {
    const versions = byEventType.get(contract.eventType) || [];
    versions.push(contract);
    byEventType.set(contract.eventType, versions);
  }
  for (const [eventType, versions] of byEventType) {
    versions.sort((left, right) => left.schemaVersion - right.schemaVersion);
    for (let index = 1; index < versions.length; index += 1) {
      const previous = versions[index - 1];
      const current = versions[index];
      if (current.schemaVersion !== previous.schemaVersion + 1) {
        throw new Error(`${current.relativePath}: schema versions for ${eventType} must be contiguous`);
      }
      for (const field of previous.requiredFields) {
        if (!current.requiredFields.includes(field)) {
          throw new Error(`${current.relativePath}: backward-compatible contract removed required field ${field}`);
        }
        if (current.payloadTypes[field] !== previous.payloadTypes[field]) {
          throw new Error(`${current.relativePath}: backward-compatible contract changed type of ${field}`);
        }
      }
    }
  }
  return contracts.sort((left, right) => left.relativePath.localeCompare(right.relativePath));
}

export function readContractFiles(root = contractsRoot) {
  const entries = [];
  for (const domain of readdirSync(root, { withFileTypes: true }).filter((entry) => entry.isDirectory()).sort((a, b) => a.name.localeCompare(b.name))) {
    const domainRoot = path.join(root, domain.name);
    for (const file of readdirSync(domainRoot).filter((name) => name.endsWith('.yaml')).sort()) {
      entries.push({
        relativePath: `${domain.name}/${file}`,
        source: readFileSync(path.join(domainRoot, file), 'utf8'),
      });
    }
  }
  return entries;
}

function scalar(source, name, filename) {
  const match = new RegExp(`^${name}:\\s*(.+)$`, 'mu').exec(source);
  if (!match || !match[1].trim()) throw new Error(`${filename}: ${name} is required`);
  return unquote(match[1].trim());
}

function nestedScalar(source, name, filename) {
  const match = new RegExp(`^  ${name}:\\s*(.+)$`, 'mu').exec(source);
  if (!match || !match[1].trim()) throw new Error(`${filename}: compatibility.${name} is required`);
  return unquote(match[1].trim());
}

function list(source, name, filename) {
  const match = new RegExp(`^  ${name}:\\s*$([\\s\\S]*?)(?=^  [A-Za-z][A-Za-z0-9_-]*:|^\\S|(?![\\s\\S]))`, 'mu').exec(source);
  if (!match) throw new Error(`${filename}: compatibility.${name} is required`);
  return [...match[1].matchAll(/^    -\s+(.+)$/gmu)].map((item) => unquote(item[1].trim()));
}

function map(source, name, filename) {
  const match = new RegExp(`^  ${name}:\\s*$([\\s\\S]*?)(?=^  [A-Za-z][A-Za-z0-9_-]*:|^\\S|(?![\\s\\S]))`, 'mu').exec(source);
  if (!match) throw new Error(`${filename}: compatibility.${name} is required`);
  return Object.fromEntries([...match[1].matchAll(/^    ([A-Za-z][A-Za-z0-9_.-]*):\s+(.+)$/gmu)]
    .map((item) => [item[1], unquote(item[2].trim())]));
}

function positiveInteger(value, name) {
  if (!/^\d+$/u.test(value) || Number(value) <= 0) throw new Error(`${name} must be a positive integer`);
  return Number(value);
}

function unquote(value) {
  return value.replace(/^(['"])(.*)\1$/u, '$2');
}

function main() {
  const contracts = validateContractSources(readContractFiles());
  console.log(`Event contract governance passed: ${contracts.length} contract version(s) verified.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
