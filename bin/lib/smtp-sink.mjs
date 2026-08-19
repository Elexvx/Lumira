#!/usr/bin/env node

import http from 'node:http';
import net from 'node:net';

const smtpHost = process.env.SMTP_SINK_HOST || '127.0.0.1';
const smtpPort = Number(process.env.SMTP_SINK_PORT || 2525);
const httpHost = process.env.SMTP_SINK_HTTP_HOST || '127.0.0.1';
const httpPort = Number(process.env.SMTP_SINK_HTTP_PORT || 2526);
const messages = [];

for (const [name, value] of [['SMTP_SINK_PORT', smtpPort], ['SMTP_SINK_HTTP_PORT', httpPort]]) {
  if (!Number.isInteger(value) || value <= 0 || value > 65_535) {
    throw new Error(`Invalid ${name}: ${value}`);
  }
}

function send(socket, line) {
  socket.write(`${line}\r\n`);
}

const smtpServer = net.createServer((socket) => {
  socket.setEncoding('utf8');
  let buffer = '';
  let inData = false;
  let data = '';
  send(socket, '220 localhost Lumira E2E SMTP sink');

  socket.on('data', (chunk) => {
    buffer += chunk;
    while (buffer.includes('\n')) {
      const newline = buffer.indexOf('\n');
      const line = buffer.slice(0, newline).replace(/\r$/, '');
      buffer = buffer.slice(newline + 1);
      if (inData) {
        if (line === '.') {
          messages.push({ receivedAt: new Date().toISOString(), raw: data });
          data = '';
          inData = false;
          send(socket, '250 2.0.0 accepted');
        } else {
          data += `${line.startsWith('..') ? line.slice(1) : line}\r\n`;
        }
        continue;
      }

      const command = line.toUpperCase();
      if (command.startsWith('EHLO') || command.startsWith('HELO')) {
        socket.write('250-localhost\r\n250 8BITMIME\r\n');
      } else if (command.startsWith('MAIL FROM:') || command.startsWith('RCPT TO:') || command === 'RSET') {
        send(socket, '250 2.1.0 OK');
      } else if (command === 'DATA') {
        inData = true;
        send(socket, '354 End data with <CR><LF>.<CR><LF>');
      } else if (command === 'NOOP') {
        send(socket, '250 2.0.0 OK');
      } else if (command === 'QUIT') {
        send(socket, '221 2.0.0 Bye');
        socket.end();
      } else {
        send(socket, '250 2.0.0 OK');
      }
    }
  });
});

const httpServer = http.createServer((request, response) => {
  response.setHeader('content-type', 'application/json; charset=utf-8');
  if (request.method === 'GET' && request.url === '/health') {
    response.end(JSON.stringify({ status: 'UP', messageCount: messages.length }));
    return;
  }
  if (request.method === 'GET' && request.url === '/messages') {
    response.end(JSON.stringify(messages));
    return;
  }
  if (request.method === 'POST' && request.url === '/reset') {
    messages.length = 0;
    response.end(JSON.stringify({ ok: true }));
    return;
  }
  response.statusCode = 404;
  response.end(JSON.stringify({ error: 'not found' }));
});

smtpServer.listen(smtpPort, smtpHost, () => {
  console.log(`SMTP sink listening on ${smtpHost}:${smtpPort}`);
});
httpServer.listen(httpPort, httpHost, () => {
  console.log(`SMTP capture API listening on ${httpHost}:${httpPort}`);
});

function shutdown() {
  smtpServer.close();
  httpServer.close();
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
