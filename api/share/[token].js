/**
 * /api/share/[token].js
 *
 * Token format: base64url(publicMeta) . base64url(iv+ciphertext) . base64url(HMAC)
 *   publicMeta  = { name, size, perm, exp }       ← visible, untuk UI
 *   ciphertext  = AES-GCM({ fid, mids, wurl, iat }) ← enkripsi, webhook URL aman
 *   HMAC        = sign(publicMeta + "." + ciphertext) ← kedua bagian terlindungi
 *
 * Keamanan:
 * 1. Webhook URL terenkripsi → tidak bisa dibaca dari token
 * 2. HMAC → token tidak bisa dimanipulasi (nama file, expiry, dll)
 * 3. Rate limit → max 30 req/menit per IP
 * 4. Expiry check
 */

export const config = {
  api: { responseLimit: false },
};

const SHARE_SECRET = process.env.SHARE_SECRET || '';

// ─── Rate limiter ─────────────────────────────────────────────────────────────
const rateMap = new Map();
const RATE_LIMIT  = 60; // max requests per window per IP
const RATE_WINDOW = 60_000;

function checkRate(ip) {
  const now   = Date.now();
  const entry = rateMap.get(ip);
  if (!entry || now > entry.resetAt) { rateMap.set(ip, { count: 1, resetAt: now + RATE_WINDOW }); return true; }
  if (entry.count >= RATE_LIMIT) return false;
  entry.count++;
  return true;
}
setInterval(() => { const now = Date.now(); for (const [ip, e] of rateMap) if (now > e.resetAt) rateMap.delete(ip); }, 5 * 60_000);

// ─── Crypto helpers ───────────────────────────────────────────────────────────
function strToBytes(str) { return new TextEncoder().encode(str); }

async function deriveAesKey(secret, usage) {
  const raw = await crypto.subtle.digest('SHA-256', strToBytes(secret));
  return crypto.subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, usage);
}

async function deriveHmacKey(secret, usage) {
  return crypto.subtle.importKey('raw', strToBytes(secret), { name: 'HMAC', hash: 'SHA-256' }, false, usage);
}

function b64urlDecode(str) {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const padded  = base64 + '='.repeat((4 - base64.length % 4) % 4);
  return Buffer.from(padded, 'base64');
}

// ─── Verify & decode 3-part token ────────────────────────────────────────────
async function verifyAndDecryptToken(token) {
  const parts = token.split('.');

  // ── Format lama: 2-part (payload.sig) ─────────────────────────────────────
  // payload = base64url({ fid, mids, wurl, name, size, perm, exp, iat })
  // Tidak terenkripsi — support backward compatibility
  if (parts.length === 2) {
    const [payloadB64, sigB64] = parts;

    // Verify HMAC jika SHARE_SECRET ada
    if (SHARE_SECRET) {
      const hmacKey  = await deriveHmacKey(SHARE_SECRET, ['verify']);
      const sigBytes = b64urlDecode(sigB64);
      const valid    = await crypto.subtle.verify('HMAC', hmacKey, sigBytes, strToBytes(payloadB64));
      if (!valid) throw new Error('Invalid token signature');
    }

    const payload = JSON.parse(b64urlDecode(payloadB64).toString('utf8'));
    if (payload.exp && Date.now() > payload.exp) throw new Error('Token expired');
    return payload;
  }

  // ── Format baru: 3-part (meta.enc.sig) ────────────────────────────────────
  // meta = base64url({ name, size, perm, exp }) — public
  // enc  = base64url(iv + AES-GCM({ fid, mids, wurl, iat })) — encrypted
  if (parts.length === 3) {
    if (!SHARE_SECRET) throw new Error('SHARE_SECRET not configured');

    const [metaB64, encB64, sigB64] = parts;

    // 1. Verify HMAC
    const hmacKey  = await deriveHmacKey(SHARE_SECRET, ['verify']);
    const sigBytes = b64urlDecode(sigB64);
    const msgData  = strToBytes(`${metaB64}.${encB64}`);
    const valid    = await crypto.subtle.verify('HMAC', hmacKey, sigBytes, msgData);
    if (!valid) throw new Error('Invalid token signature');

    // 2. Decode public meta
    const publicMeta = JSON.parse(b64urlDecode(metaB64).toString('utf8'));
    if (publicMeta.exp && Date.now() > publicMeta.exp) throw new Error('Token expired');

    // 3. Decrypt private payload
    const encBytes   = b64urlDecode(encB64);
    const iv         = encBytes.slice(0, 12);
    const ciphertext = encBytes.slice(12);
    const aesKey     = await deriveAesKey(SHARE_SECRET, ['decrypt']);
    const decrypted  = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, ciphertext);
    const privatePayload = JSON.parse(new TextDecoder().decode(decrypted));

    return { ...publicMeta, ...privatePayload };
  }

  throw new Error('Invalid token format');
}

// ─── Decrypt Discord file chunk ───────────────────────────────────────────────
const MAGIC = Buffer.from('DBX_ENC:');

async function deriveFileKey(webhookUrl) {
  const hash = await crypto.subtle.digest('SHA-256', strToBytes(webhookUrl));
  return crypto.subtle.importKey('raw', hash, { name: 'AES-GCM' }, false, ['decrypt']);
}

async function decryptChunk(data, key) {
  const uint8 = new Uint8Array(data);
  let isEncrypted = true;
  for (let i = 0; i < MAGIC.length; i++) { if (uint8[i] !== MAGIC[i]) { isEncrypted = false; break; } }
  if (!isEncrypted) return new Uint8Array(data);
  const iv         = uint8.slice(MAGIC.length, MAGIC.length + 12);
  const ciphertext = uint8.slice(MAGIC.length + 12);
  const decrypted  = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ciphertext);
  return new Uint8Array(decrypted);
}

async function fetchChunk(webhookUrl, msgId, fileKey) {
  const msgRes = await fetch(`${webhookUrl}/messages/${msgId}`);
  if (!msgRes.ok) throw new Error(`Discord fetch failed: ${msgRes.status}`);
  const msg           = await msgRes.json();
  const attachmentUrl = msg.attachments?.[0]?.url;
  if (!attachmentUrl) throw new Error('No attachment found');
  const fileRes = await fetch(attachmentUrl);
  if (!fileRes.ok) throw new Error(`CDN fetch failed: ${fileRes.status}`);
  return decryptChunk(await fileRes.arrayBuffer(), fileKey);
}

function getMimeType(name) {
  const ext = name?.split('.').pop()?.toLowerCase();
  const map = {
    pdf: 'application/pdf',
    mp4: 'video/mp4', mov: 'video/quicktime', avi: 'video/x-msvideo',
    webm: 'video/webm', mkv: 'video/x-matroska',
    mp3: 'audio/mpeg', wav: 'audio/wav', flac: 'audio/flac',
    jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png',
    gif: 'image/gif', webp: 'image/webp', svg: 'image/svg+xml',
    txt: 'text/plain', html: 'text/html', css: 'text/css',
    json: 'application/json', js: 'text/javascript',
    zip: 'application/zip',
  };
  return map[ext] || 'application/octet-stream';
}

// ─── Handler ──────────────────────────────────────────────────────────────────
export default async function handler(req, res) {
  const ip = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.headers['x-real-ip'] || 'unknown';
  if (!checkRate(ip)) { res.setHeader('Retry-After', '60'); return res.status(429).json({ error: 'Too many requests' }); }

  const { token } = req.query;
  if (!token) return res.status(400).json({ error: 'Missing token' });

  let payload;
  try {
    payload = await verifyAndDecryptToken(Array.isArray(token) ? token[0] : token);
  } catch (e) {
    return res.status(e.message === 'Token expired' ? 410 : 401).json({ error: e.message });
  }

  const { mids, wurl, name, perm } = payload;
  if (!mids?.length || !wurl) return res.status(400).json({ error: 'Invalid token payload' });

  try {
    const fileKey = await deriveFileKey(wurl);
    const chunks  = await Promise.all(mids.map(msgId => fetchChunk(wurl, msgId, fileKey)));
    const total   = chunks.reduce((sum, c) => sum + c.byteLength, 0);
    const merged  = new Uint8Array(total);
    let offset = 0;
    for (const c of chunks) { merged.set(c, offset); offset += c.byteLength; }

    res.setHeader('Content-Type', getMimeType(name || 'file'));
    res.setHeader('Content-Length', merged.byteLength);
    res.setHeader('Content-Disposition', `${perm === 'view' ? 'inline' : 'attachment'}; filename="${encodeURIComponent(name || 'file')}"`);
    res.setHeader('Cache-Control', 'private, no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    return res.status(200).send(Buffer.from(merged));
  } catch (e) {
    console.error('[share] Error:', e.message);
    return res.status(500).json({ error: 'Failed to serve file' });
  }
}
