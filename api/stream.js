import { webcrypto as crypto } from 'node:crypto';
import { Buffer } from 'node:buffer';

/**
 * /api/stream.js
 * 
 * Implements a streaming proxy for Disbox files, allowing seekable video/audio.
 * 
 * Query Params:
 * - webhook:   Full Discord Webhook URL
 * - mime:      Target MIME type
 * - size:      Total file size
 * - chunkSize: Size of each chunk
 * - messages:  JSON array of message IDs
 */

export const config = {
  api: { responseLimit: false },
};

const MAGIC_HEADER = new TextEncoder().encode('DBX_ENC:');
const ALLOWED_ORIGINS = [
  'https://disbox-web-weld.vercel.app',
  'http://localhost:5173',
  'http://localhost:4173',
  'http://localhost',     // Capacitor Android
  'https://localhost',     // Capacitor Android
  'capacitor://localhost', // Capacitor iOS
  ...(process.env.ALLOWED_ORIGINS || '').split(',').map(s => s.trim()).filter(Boolean),
];

async function deriveFileKey(webhookUrl) {
  const encoder = new TextEncoder();
  const data = encoder.encode(webhookUrl.split('?')[0]);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return await crypto.subtle.importKey('raw', hash, { name: 'AES-GCM' }, false, ['decrypt']);
}

async function decryptChunk(data, key) {
  // data is a Buffer/Uint8Array
  if (data.length < MAGIC_HEADER.length + 12 + 16) return data;
  
  // Check magic header using Uint8Array.every
  let hasMagic = true;
  for (let i = 0; i < MAGIC_HEADER.length; i++) {
    if (data[i] !== MAGIC_HEADER[i]) {
      hasMagic = false;
      break;
    }
  }
  if (!hasMagic) return data;

  try {
    const iv = data.slice(MAGIC_HEADER.length, MAGIC_HEADER.length + 12);
    const ciphertextWithTag = data.slice(MAGIC_HEADER.length + 12);
    
    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      key,
      ciphertextWithTag
    );
    return new Uint8Array(decrypted);
  } catch (e) {
    console.error('[stream] Decryption failed:', e.message);
    return data;
  }
}

export default async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

  // Origin check
  const origin = req.headers['origin'] || req.headers['referer'] || '';
  const isAllowed = 
    ALLOWED_ORIGINS.length === 0 ||
    ALLOWED_ORIGINS.some(o => origin.startsWith(o)) || 
    origin.endsWith('.vercel.app') ||
    origin.includes('localhost:');

  if (!isAllowed) {
    return res.status(403).json({ error: 'Forbidden: origin not allowed' });
  }

  const { webhook, mime, size, chunkSize: passedChunkSize, messages } = req.query;
  
  if (!webhook || !size || !messages) {
    return res.status(400).json({ error: 'Missing required parameters' });
  }

  let messageIds;
  try {
    messageIds = JSON.parse(messages);
  } catch (e) {
    return res.status(400).json({ error: 'Invalid messages parameter' });
  }

  const totalSize = parseInt(size, 10);
  const mimeType = mime || 'application/octet-stream';
  const chunkSize = parseInt(passedChunkSize, 10) || Math.ceil(totalSize / messageIds.length) || 7.5 * 1024 * 1024;

  const rangeHeader = req.headers.range;
  let start = 0;
  let end = totalSize - 1;

  if (rangeHeader) {
    const parts = rangeHeader.replace(/bytes=/, '').split('-');
    start = parseInt(parts[0], 10);
    if (isNaN(start)) start = 0;
    end = parts[1] ? parseInt(parts[1], 10) : totalSize - 1;
    if (isNaN(end)) end = totalSize - 1;
  }

  start = Math.max(0, start);
  end = Math.min(totalSize - 1, end);

  if (start > end) {
    res.setHeader('Content-Range', `bytes */${totalSize}`);
    return res.status(416).end();
  }

  const contentLength = end - start + 1;
  const encryptionKey = await deriveFileKey(webhook);

  res.setHeader('Content-Type', mimeType);
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'no-cache');

  if (rangeHeader) {
    res.status(206);
    res.setHeader('Content-Range', `bytes ${start}-${end}/${totalSize}`);
  } else {
    res.status(200);
  }
  res.setHeader('Content-Length', contentLength);

  try {
    let currentOffset = start;
    const webhookBase = webhook.split('?')[0];

    for (let i = 0; i < messageIds.length; i++) {
      const chunkStart = i * chunkSize;
      const chunkEnd = Math.min(chunkStart + chunkSize, totalSize);

      if (chunkEnd <= start) continue;
      if (chunkStart > end) break;

      const item = messageIds[i];
      const msgId = typeof item === 'string' ? item : item.msgId;
      const attachmentIndex = typeof item === 'object' ? (item.index || 0) : 0;

      try {
        const msgRes = await fetch(`${webhookBase}/messages/${msgId}`);
        if (!msgRes.ok) throw new Error(`Discord msg error: ${msgRes.status}`);
        const msg = await msgRes.json();
        const attachmentUrl = msg.attachments?.[attachmentIndex]?.url || msg.attachments?.[0]?.url;
        if (!attachmentUrl) throw new Error('No attachment');

        const fileRes = await fetch(attachmentUrl);
        if (!fileRes.ok) throw new Error(`CDN error: ${fileRes.status}`);
        const encryptedData = new Uint8Array(await fileRes.arrayBuffer());

        const decryptedChunk = await decryptChunk(encryptedData, encryptionKey);

        const sliceStart = Math.max(0, currentOffset - chunkStart);
        const sliceEnd = Math.min(decryptedChunk.length, end - chunkStart + 1);

        if (sliceStart < decryptedChunk.length) {
          const dataToPush = decryptedChunk.slice(sliceStart, sliceEnd);
          if (dataToPush.length > 0) {
            res.write(Buffer.from(dataToPush));
            currentOffset += dataToPush.length;
          }
        }
      } catch (err) {
        console.error(`[stream] Error chunk ${i}:`, err.message);
        break;
      }

      if (currentOffset > end) break;
    }
    res.end();
  } catch (e) {
    console.error('[stream] Fatal:', e.message);
    if (!res.headersSent) res.status(500).end(e.message);
    else res.end();
  }
}
