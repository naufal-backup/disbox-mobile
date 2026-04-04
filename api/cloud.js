import { supabase } from './_lib/supabase.js';
import { encrypt, decrypt } from './_lib/encryption.js';
import { handleCors } from './_lib/cors.js';
import { requireAuth, ensureIdentifierAccess } from './_lib/auth.js';

export default async function handler(req, res) {
  const corsHandled = handleCors(req, res);
  if (corsHandled) return;

  const urlObj = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const action = urlObj.pathname.split('/').pop();

  const unauthorized = requireAuth(req, res);
  if (unauthorized) return unauthorized;

  if (action === 'config') {
    const { identifier, username } = req.query;
    const targetId = (identifier || username || '').toLowerCase();
    if (!targetId) return res.status(400).json({ error: 'Missing identifier' });

    const forbidden = ensureIdentifierAccess(req, res, targetId);
    if (forbidden) return forbidden;

    try {
      const { data: meta } = await supabase.from('metadata').select('*').eq('identifier', targetId).maybeSingle();
      const { data: user } = await supabase.from('users').select('webhook_url').eq('username', targetId).maybeSingle();
      return res.status(200).json({
        ok: true,
        webhook_url: user ? decrypt(user.webhook_url) : null,
        metadata_b64: meta?.content_b64 || null,
        updated_at: meta?.updated_at || null
      });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  if (action === 'sync') {
    const { identifier, webhook_url, metadata_b64, username } = req.body;
    const targetId = (identifier || username || '').toLowerCase();
    if (!targetId) return res.status(400).json({ error: 'Missing identifier' });

    const forbidden = ensureIdentifierAccess(req, res, targetId);
    if (forbidden) return forbidden;

    try {
      if (metadata_b64) {
        await supabase.from('metadata').upsert({ identifier: targetId, content_b64: metadata_b64, updated_at: new Date().toISOString() }, { onConflict: 'identifier' });
      }
      if (username && webhook_url) {
        await supabase.from('users').update({ webhook_url: encrypt(webhook_url) }).eq('username', targetId);
      }
      return res.status(200).json({ ok: true });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  return res.status(404).json({ error: 'Action not found' });
}
