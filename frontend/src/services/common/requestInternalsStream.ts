export const readEventStream = async (
  body: ReadableStream<Uint8Array>,
  onEvent?: (event: { event: string; data: string }) => void,
) => {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || '';
    events.forEach((eventBlock) => emitStreamEvent(eventBlock, onEvent));
  }

  if (buffer.trim()) {
    emitStreamEvent(buffer, onEvent);
  }
};

const emitStreamEvent = (eventBlock: string, onEvent?: (event: { event: string; data: string }) => void) => {
  let event = 'message';
  const dataLines: string[] = [];
  eventBlock.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim() || 'message';
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  });
  if (dataLines.length) {
    onEvent?.({ event, data: dataLines.join('\n') });
  }
};
