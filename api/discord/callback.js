import { list } from '@vercel/blob';

/**
 * /api/discord/callback.js
 */

const DISCORD_CLIENT_ID = '1484882393226678292';

const ALLOWED_ORIGINS = [
  'https://disbox-web-weld.vercel.app',
  'http://localhost:5173',
  'http://localhost:4173',
  'http://localhost:3000',
];

export default async function handler(req, res) {
  const origin = req.headers['origin'] || req.headers['referer'] || '';
  const isAllowed = ALLOWED_ORIGINS.some(o => origin.startsWith(o)) || origin.endsWith('.vercel.app') || origin.includes('localhost:');

  const cors = {
    'Access-Control-Allow-Origin': isAllowed ? origin : ALLOWED_ORIGINS[0],
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  };

  Object.entries(cors).forEach(([k, v]) => res.setHeader(k, v));
  if (req.method === 'OPTIONS') return res.status(204).end();

  const { code } = req.query;
  if (!code) return res.status(400).json({ ok: false, error: 'Missing code' });

  const CLIENT_SECRET = process.env.DISCORD_CLIENT_SECRET;
  if (!CLIENT_SECRET) return res.status(500).json({ ok: false, error: 'DISCORD_CLIENT_SECRET not set' });

  const host = req.headers['host'];
  const protocol = host.includes('localhost') ? 'http' : 'https';
  const REDIRECT_URI = process.env.DISCORD_REDIRECT_URI || `${protocol}://${host}/discord/callback`;

  try {
    const tokenRes = await fetch('https://discord.com/api/v10/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id:     DISCORD_CLIENT_ID,
        client_secret: CLIENT_SECRET,
        grant_type:    'authorization_code',
        code,
        redirect_uri:  REDIRECT_URI,
      }),
    });

    const data = await tokenRes.json();
    if (!tokenRes.ok) return res.status(400).json({ ok: false, error: data.error_description || 'Token exchange failed' });

    // ─── CLOUD SYNC via Blob (SDK fix) ───
    let cloud_config = null;
    let user_id      = null;
    
    try {
      const userRes = await fetch('https://discord.com/api/v10/users/@me', {
        headers: { 'Authorization': `Bearer ${data.access_token}` }
      });
      if (userRes.ok) {
        const userData = await userRes.json();
        user_id = userData.id;
        
        // Gunakan SDK list() untuk mencari file configs/USER_ID.json
        const { blobs } = await list({
          prefix: `configs/${user_id}.json`,
          token: process.env.BLOB_READ_WRITE_TOKEN
        });

        if (blobs.length > 0) {
          const cfgRes = await fetch(blobs[0].url);
          if (cfgRes.ok) cloud_config = await cfgRes.json();
        }
      }
    } catch (e) {
      console.error('[cloud] SDK Read failed:', e.message);
    }

    return res.status(200).json({
      ok:           true,
      access_token: data.access_token,
      token_type:   data.token_type,
      scope:        data.scope,
      expires_in:   data.expires_in,
      webhook:      data.webhook,
      user_id:      user_id,
      cloud_config: cloud_config,
    });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e.message });
  }
}
