export const ICP_QUERY_URL = 'https://beian.miit.gov.cn/';
export const POLICE_BEIAN_QUERY_URL = 'https://beian.mps.gov.cn/#/query/webSearch';

export const isPoliceBeianText = (text: string) =>
  /(?:公安备案|公网安备)/u.test(text) || /\d{13,}/u.test(text);

export const resolvePoliceBeianQueryUrl = (text: string) => {
  const recordCode = text.match(/\d{13,}/u)?.[0];
  return recordCode ? `${POLICE_BEIAN_QUERY_URL}?code=${encodeURIComponent(recordCode)}` : POLICE_BEIAN_QUERY_URL;
};
