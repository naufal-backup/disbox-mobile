import { supabase } from './_lib/supabase.js';
import { encrypt } from './_lib/encryption.js';

export default async function handler(req, res) {
  const { pathname } = new URL(req.url, `http://${req.headers.host}`);
  const action = pathname.split('/').pop();
  const adminKey = req.query.key;

  if (adminKey !== process.env.PROXY_SECRET) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  if (action === 'setup-db') {
    const sql = `
      CREATE TABLE IF NOT EXISTS users (
        username TEXT PRIMARY KEY,
        password TEXT NOT NULL,
        webhook_url TEXT NOT NULL,
        created_at TIMESTAMPTZ DEFAULT NOW(),
        updated_at TIMESTAMPTZ DEFAULT NOW()
      );
      
      CREATE TABLE IF NOT EXISTS files (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        structure JSONB NOT NULL DEFAULT '[]',
        updated_at TIMESTAMPTZ DEFAULT NOW()
      );

      CREATE TABLE IF NOT EXISTS metadata (
        identifier TEXT PRIMARY KEY,
        file_id UUID REFERENCES files(id),
        content_b64 TEXT,
        updated_at TIMESTAMPTZ DEFAULT NOW()
      );

      ALTER TABLE users DISABLE ROW LEVEL SECURITY;
      ALTER TABLE files DISABLE ROW LEVEL SECURITY;
      ALTER TABLE metadata DISABLE ROW LEVEL SECURITY;
    `;
    return res.status(200).send(`<html><body><h1>SQL Setup (Tidy JSONB Edition)</h1><pre>${sql}</pre></body></html>`);
  }

  if (action === 'migrate-webhooks') {
    const { data: users } = await supabase.from('users').select('username, webhook_url');
    let count = 0;
    for (const u of users) {
      if (!u.webhook_url.includes(':')) {
        await supabase.from('users').update({ webhook_url: encrypt(u.webhook_url) }).eq('username', u.username);
        count++;
      }
    }
    return res.status(200).json({ ok: true, migrated: count });
  }

  return res.status(404).json({ error: 'Action not found' });
}
