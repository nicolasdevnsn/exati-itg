#!/usr/bin/env node
'use strict';

/*
 * Minimal, dependency-free CIM proxy — WILDCARD demo.
 *
 * Mirrors the Java CimProxyController: forwards ANY request under
 *   /api/v1/cim/**   ->   {CIM_BASE_URL}/**
 * preserving method, path, query string, body and (non-hop-by-hop) headers
 * including Authorization. Upstream status/body/headers are returned unchanged;
 * 502 if the gateway is unreachable. It is contract-agnostic — no route or
 * business identifier is baked in, so you test the forwarding, not the data.
 *
 * Run (PowerShell):
 *   # 1) End-to-end demo with a built-in mock CIM that echoes any request:
 *   $env:PORT = "3111"; node scripts/cim-proxy-demo.js mock
 *
 *   # 2) Against a real CIM gateway:
 *   $env:CIM_BASE_URL = "http://<cim-host>:18084/ami/cim"; $env:PORT = "3111"; node scripts/cim-proxy-demo.js
 *
 * Then hit ANY path you like, e.g.:
 *   Invoke-RestMethod "http://localhost:3111/api/v1/cim/V1/reload" -Method Post
 *   curl "http://localhost:3111/api/v1/cim/anything?foo=bar" -H "Authorization: Bearer test"
 */

const http = require('http');
const https = require('https');
const { URL } = require('url');

const PROXY_PORT = process.env.PORT || 3000;
const CIM_BASE_URL = process.env.CIM_BASE_URL || 'http://localhost:18084/ami/cim';
const PREFIX = '/api/v1/cim';

// Hop-by-hop / connection headers we must not forward.
const REQUEST_SKIP = new Set([
  'host', 'content-length', 'connection', 'keep-alive', 'transfer-encoding',
  'te', 'trailer', 'upgrade', 'proxy-authenticate', 'proxy-authorization', 'accept-encoding',
]);
const RESPONSE_SKIP = new Set(['content-length', 'connection', 'keep-alive', 'transfer-encoding']);

function forward(targetUrl, method, reqHeaders, bodyBuffer, res) {
  const u = new URL(targetUrl);
  const lib = u.protocol === 'https:' ? https : http;

  const outHeaders = {};
  for (const [k, v] of Object.entries(reqHeaders)) {
    if (!REQUEST_SKIP.has(k.toLowerCase())) outHeaders[k] = v;
  }

  const upstream = lib.request(u, { method, headers: outHeaders }, (up) => {
    const chunks = [];
    up.on('data', (c) => chunks.push(c));
    up.on('end', () => {
      const body = Buffer.concat(chunks);
      const respHeaders = {};
      for (const [k, v] of Object.entries(up.headers)) {
        if (!RESPONSE_SKIP.has(k.toLowerCase())) respHeaders[k] = v;
      }
      res.writeHead(up.statusCode, respHeaders);
      res.end(body);
      console.log(`  -> ${up.statusCode}  ${method} ${targetUrl}`);
    });
  });

  upstream.on('error', (err) => {
    console.error(`  !! upstream error: ${err.message}`);
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 502, detail: 'CIM gateway is unreachable.', error: err.message }));
  });

  if (bodyBuffer && bodyBuffer.length > 0) upstream.end(bodyBuffer);
  else upstream.end();
}

const proxy = http.createServer((req, res) => {
  const parsed = new URL(req.url, 'http://localhost');
  console.log(`${req.method} ${req.url}`);

  if (!parsed.pathname.startsWith(PREFIX)) {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 404, detail: `Not proxied. Prefix all calls with ${PREFIX}/...` }));
    return;
  }

  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const rest = parsed.pathname.slice(PREFIX.length); // '' or '/V1/reload' etc.
    const target = CIM_BASE_URL + rest + parsed.search;
    forward(target, req.method, req.headers, Buffer.concat(chunks), res);
  });
});

proxy.listen(PROXY_PORT, () => {
  console.log(`CIM proxy (wildcard) listening on http://localhost:${PROXY_PORT}`);
  console.log(`  ${PREFIX}/**  ->  ${CIM_BASE_URL}/**`);
});

// Optional built-in mock CIM upstream that echoes ANY request (no data needed).
if (process.argv.includes('mock')) {
  const base = new URL(CIM_BASE_URL);
  const mock = http.createServer((req, res) => {
    const p = new URL(req.url, 'http://localhost');
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString();
      let body = raw;
      try { body = raw ? JSON.parse(raw) : null; } catch { /* keep raw string */ }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        mock: true,
        method: req.method,
        path: p.pathname,
        query: Object.fromEntries(p.searchParams),
        receivedAuth: req.headers.authorization || null,
        body,
      }));
    });
  });
  mock.listen(base.port || 80, () => {
    console.log(`Mock CIM upstream on ${base.origin} (echoes any request under ${base.pathname})`);
  });
}
