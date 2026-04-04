import { supabase } from './_lib/supabase.js';
import { encrypt, decrypt } from './_lib/encryption.js';
import crypto from 'crypto';
import { sign } from './_lib/jwt.js';

function setSessionCookie(res, token) {
  const cookie = `session=${token}; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=604800`;
  res.setHeader('Set-Cookie', cookie);
}

function clearSessionCookie(res) {
  const cookie = 'session=; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=0';
  res.setHeader('Set-Cookie', cookie);
}

export default async function handler(req, res) {
  const { pathname } = new URL(req.url, `http://${req.headers.host}`);
  const action = pathname.split('/').pop();

  if (req.method === 'OPTIONS') return res.status(200).end();

  if (action === 'register') {
    const { username, password, webhook_url, metadata_url } = req.body;
    if (!username || !password || !webhook_url) return res.status(400).json({ error: 'Missing fields' });
    try {
      const targetId = username.toLowerCase();

      const { data: exists } = await supabase.from('users').select('username').eq('username', targetId).maybeSingle();
      if (exists) return res.status(400).json({ error: 'Username sudah digunakan' });

      const { data: newFile, error: fileErr } = await supabase.from('files').insert([{ structure: [], updated_at: new Date().toISOString() }]).select().single();
      if (fileErr) throw fileErr;

      await supabase.from('metadata').insert([{ identifier: targetId, file_id: newFile.id }]);

      const hashedPassword = crypto.createHash('sha256').update(password).digest('hex');
      const encWebhook = encrypt(webhook_url);
      await supabase.from('users').insert([{ username: targetId, password: hashedPassword, webhook_url: encWebhook }]);

      if (metadata_url) {
        const metaRes = await fetch(metadata_url);
        if (metaRes.ok) {
          // handled separately
        }
      }

      const token = sign({ identifier: targetId, mode: 'cloud' });
      setSessionCookie(res, token);

      return res.status(200).json({ ok: true });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  if (action === 'login') {
    const { username, password } = req.body;
    try {
      const targetId = username.toLowerCase();
      const { data: user } = await supabase.from('users').select('*').eq('username', targetId).maybeSingle();
      if (!user) return res.status(401).json({ error: 'User tidak ditemukan' });

      const hash = crypto.createHash('sha256').update(password).digest('hex');
      if (user.password !== hash) return res.status(401).json({ error: 'Password salah' });

      const webhook = decrypt(user.webhook_url);
      const token = sign({ identifier: targetId, mode: 'cloud' });
      setSessionCookie(res, token);

      return res.status(200).json({ ok: true, username: user.username, webhook_url: webhook });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  if (action === 'webhook') {
    const { webhook_url } = req.body;
    if (!webhook_url) return res.status(400).json({ error: 'Missing webhook_url' });

    const DISCORD_WEBHOOK_REGEX = /^https:\/\/discord(app)?\.com\/api\/webhooks\/\d+\/.+$/;
    if (!DISCORD_WEBHOOK_REGEX.test(webhook_url)) {
      return res.status(400).json({ error: 'Invalid webhook format' });
    }

    try {
      const encoder = new TextEncoder();
      const hashBuffer = crypto.createHash('sha256').update(webhook_url.trim()).digest();
      const identifier = Array.from(hashBuffer).map(b => b.toString(16).padStart(2, '0')).join('');

      const token = sign({ identifier, mode: 'manual' });
      setSessionCookie(res, token);

      return res.status(200).json({ ok: true, token });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  if (action === 'logout') {
    clearSessionCookie(res);
    return res.status(200).json({ ok: true });
  }

  return res.status(404).json({ error: 'Action not found' });
}
