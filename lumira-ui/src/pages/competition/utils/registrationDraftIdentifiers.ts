const pad = (value: number, length: number) => String(value).padStart(length, '0');

const formatLocalTimestamp = (timestamp: number) => {
  const date = new Date(timestamp);
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1, 2),
    pad(date.getDate(), 2),
    pad(date.getHours(), 2),
    pad(date.getMinutes(), 2),
    pad(date.getSeconds(), 2),
    pad(date.getMilliseconds(), 3),
  ].join('');
};

const stableSuffix = (value: string) => {
  let hash = 2_166_136_261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16_777_619);
  }
  return (hash >>> 0).toString(36).toUpperCase().padStart(6, '0').slice(-6);
};

export type RegistrationDraftIdentifiers = {
  registrationNo: string;
  participantNo: string;
};

export const buildRegistrationDraftIdentifiers = (
  timestamp = Date.now(),
  seed: string | number = 'registration-draft',
): RegistrationDraftIdentifiers => {
  const normalizedTimestamp = Number.isFinite(timestamp) && timestamp > 0 ? Math.trunc(timestamp) : Date.now();
  const stamp = formatLocalTimestamp(normalizedTimestamp);
  const suffix = stableSuffix(`${normalizedTimestamp}:${seed}`);
  return {
    registrationNo: `REG-${stamp}-${suffix}`,
    participantNo: `PART-${stamp}-${suffix}`,
  };
};
