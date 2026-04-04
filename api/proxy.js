import { verify as verifyJwt } from './_lib/jwt.js';

/**
 * Vercel Serverless Function — Discord API & CDN Proxy
 */

export const config = {
  api: { responseLimit: false },
};

const ALLOWED_HOSTS   = ['cdn.discordapp.com', 'media.discordapp.net', 'discord.com', 'discordapp.com'];
const SLICE_SIZE      = 4 * 1024 * 1024; // 4MB

const ALLOWED_ORIGINS = [
  'https://disbox-web-weld.vercel.app',
  'http://localhost:5173',
  'http://localhost:4173',
  ...(process.env.ALLOWED_ORIGINS || '').split(',').map(s => s.trim()).filter(Boolean),
];

const PROXY_SECRET = process.env.PROXY_SECRET || '';

async function verifySignature(url, sig) {
  if (!PROXY_SECRET || !sig) return false;
  try {
    const encoder   = new TextEncoder();
    const cryptoKey = await crypto.subtle.importKey(
      'raw', encoder.encode(PROXY_SECRET),
      { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']
    );
    const sigBytes = Uint8Array.from(atob(sig.replace(/-/g,'+').replace(/_/g,'/')), c => c.charCodeAt(0));
    return await crypto.subtle.verify('HMAC', cryptoKey, sigBytes, encoder.encode(url));
  } catch { return false; }
}

function hasValidSession(req) {
  let token = null;
  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.slice(7);
  }
  if (!token) {
    const cookies = req.headers.cookie || '';
    const sessionMatch = cookies.match(/session=([^;]+)/);
    if (sessionMatch) token = sessionMatch[1];
  }
  if (!token) return false;
  try {
    return !!verifyJwt(token);
  } catch {
    return false;
  }
}

export default async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

  const { url, start, end, sig } = req.query;
  if (!url) return res.status(400).json({ error: 'Missing url parameter' });

  const targetUrl = Array.isArray(url) ? url[0] : url;
  let target;
  try {
    target = new URL(targetUrl);
  } catch (e) {
    return res.status(400).json({ error: 'Invalid target URL' });
  }

  if (!ALLOWED_HOSTS.includes(target.hostname)) {
    return res.status(403).json({ error: 'Forbidden host: ' + target.hostname });
  }

  let isSignatureValid = false;
  if (sig) {
    isSignatureValid = await verifySignature(targetUrl, sig);
  }

  const isSessionValid = hasValidSession(req);

  if (!isSignatureValid && !isSessionValid) {
    return res.status(401).json({ error: 'Unauthorized: Valid session or signature required' });
  }

  const origin = req.headers['origin'] || req.headers['referer'] || '';
  const isOriginAllowed = 
    ALLOWED_ORIGINS.some(o => origin.startsWith(o)) || 
    origin.endsWith('.vercel.app') ||
    origin.includes('localhost:');

  if (isSessionValid && !isOriginAllowed && ALLOWED_ORIGINS.length > 0) {
    return res.status(403).json({ error: 'Forbidden origin' });
  }

  if (start !== undefined || end !== undefined) {
    const s = parseInt(start, 10);
    const e = parseInt(end, 10);
    if (isNaN(s) || isNaN(e) || s < 0 || e < s || (e - s) > SLICE_SIZE + 1024) {
      return res.status(400).json({ error: 'Invalid range parameters' });
    }
  }

  try {
    const headers = {
      'User-Agent': 'Disbox-Web/3.6.0 (Vercel; Cloud-Storage)'
    };
    if (start !== undefined && end !== undefined) headers['Range'] = `bytes=${start}-${end}`;

    const upstream = await fetch(target.toString(), {
      method: 'GET',
      headers: headers
    });

    if (!upstream.ok && upstream.status >= 400) {
      return res.status(upstream.status).json({ error: `Upstream error: ${upstream.status}` });
    }

    const contentType = upstream.headers.get('content-type') || 'application/octet-stream';
    const contentRange  = upstream.headers.get('content-range');
    const contentLength = upstream.headers.get('content-length');

    res.setHeader('Access-Control-Allow-Origin', isOriginAllowed ? (origin.split('/').slice(0, 3).join('/')) : ALLOWED_ORIGINS[0]);
    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'private, max-age=3600');
    res.setHeader('X-Slice-Size', SLICE_SIZE);
    if (contentRange)  res.setHeader('Content-Range', contentRange);
    if (contentLength) res.setHeader('Content-Length', contentLength);
    
    const totalMatch = typeof contentRange === 'string' ? contentRange.match(/\/(\d+)$/) : null;
    if (totalMatch) res.setHeader('X-Total-Size', totalMatch[1]);

    const arrayBuffer = await upstream.arrayBuffer();
    res.status(upstream.status).send(Buffer.from(arrayBuffer));
  } catch (e) {
    res.status(500).json({ error: 'Proxy fetch failed: ' + e.message });
  }
}
