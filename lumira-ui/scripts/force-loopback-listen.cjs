'use strict';

// UtooPack 1.4.3 starts its outer HTTP proxy with server.listen(port), which
// ignores the host Umi resolved from --host. When this preload is explicitly
// enabled by dev-server.mjs, add the requested loopback host to otherwise
// hostless Node server listeners. Explicit host and pipe listeners are kept.
const net = require('node:net');

const requestedHost = String(process.env.UMI_DEV_HOST || process.env.HOST || '').toLowerCase();
const loopbackHosts = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);

if (loopbackHosts.has(requestedHost)) {
  const originalListen = net.Server.prototype.listen;
  net.Server.prototype.listen = function listenOnRequestedLoopback(...args) {
    if (typeof args[0] === 'number' && typeof args[1] !== 'string') {
      args.splice(1, 0, requestedHost);
    } else if (args[0] && typeof args[0] === 'object' && !args[0].path && !args[0].host) {
      args[0] = { ...args[0], host: requestedHost };
    }
    return originalListen.apply(this, args);
  };
}
