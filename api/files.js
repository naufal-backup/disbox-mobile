import { supabase } from './_lib/supabase.js';
import { handleCors } from './_lib/cors.js';
import { requireAuth, ensureIdentifierAccess } from './_lib/auth.js';

export default async function handler(req, res) {
  // Handle CORS (preflight and headers)
  const corsHandled = handleCors(req, res);
  if (corsHandled) return;

  const urlObj = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const action = urlObj.pathname.split('/').pop();

  // Auth middleware
  const unauthorized = requireAuth(req, res);
  if (unauthorized) return unauthorized;

  // Unified File Operations (JSONB Array Storage)
  if (action === 'check') {
    const { identifier } = req.query;
    if (!identifier) return res.status(400).json({ error: 'Missing identifier' });

    const forbidden = ensureIdentifierAccess(req, res, identifier);
    if (forbidden) return forbidden;

    try {
      const { data: meta } = await supabase
        .from('metadata')
        .select('files(updated_at, structure)')
        .eq('identifier', identifier.toLowerCase())
        .maybeSingle();

      if (!meta || !meta.files) return res.status(200).json({ ok: true, changed: false });

      return res.status(200).json({
        ok: true,
        updated_at: meta.files.updated_at,
        count: meta.files.structure?.length || 0
      });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  if (action === 'list') {
    const { identifier } = req.query;
    if (!identifier) return res.status(400).json({ error: 'Missing identifier' });

    const forbidden = ensureIdentifierAccess(req, res, identifier);
    if (forbidden) return forbidden;

    try {
      const { data: meta } = await supabase
        .from('metadata')
        .select('file_id, files(structure)')
        .eq('identifier', identifier.toLowerCase())
        .maybeSingle();

      const structure = meta?.files?.structure || {};
      
      return res.status(200).json({
        ok: true,
        files: Array.isArray(structure) ? structure : (structure.files || []),
        pinHash: structure.pinHash || null,
        shareLinks: structure.shareLinks || [],
        settings: structure.settings || {}
      });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  if (action === 'sync-all') {
    const { identifier, files, pinHash, shareLinks, settings } = req.body;
    if (!identifier || !files) return res.status(400).json({ error: 'Missing data' });

    const forbidden = ensureIdentifierAccess(req, res, identifier);
    if (forbidden) return forbidden;

    try {
      const targetId = identifier.toLowerCase();
      
      // Ensure booleans are true/false in JSON
      const normalizedFiles = files.map(f => ({
        ...f,
        isLocked: !!f.isLocked,
        isStarred: !!f.isStarred
      }));

      const structure = {
        files: normalizedFiles,
        pinHash: pinHash || null,
        shareLinks: shareLinks || [],
        settings: settings || {}
      };

      let { data: meta } = await supabase.from('metadata').select('file_id').eq('identifier', targetId).maybeSingle();

      let fileId;
      let updatedAt = new Date().toISOString();
      
      if (!meta || !meta.file_id) {
        // Create new file record
        const { data: newFile, error: insErr } = await supabase.from('files').insert([{ structure, updated_at: updatedAt }]).select().single();
        if (insErr) throw insErr;
        
        // Upsert metadata mapping
        await supabase.from('metadata').upsert({ identifier: targetId, file_id: newFile.id, updated_at: updatedAt }, { onConflict: 'identifier' });
        
        fileId = newFile.id;
        if (newFile.updated_at) updatedAt = newFile.updated_at;
      } else {
        fileId = meta.file_id;
        const { data: updatedFile, error: updErr } = await supabase.from('files').update({ structure, updated_at: updatedAt }).eq('id', fileId).select().single();
        if (updErr) throw updErr;
        
        if (updatedFile?.updated_at) updatedAt = updatedFile.updated_at;
        
        // Update metadata timestamp
        await supabase.from('metadata').update({ updated_at: updatedAt }).eq('identifier', targetId);
      }

      return res.status(200).json({ ok: true, updated_at: updatedAt, count: files.length });
    } catch (e) { return res.status(500).json({ error: e.message }); }
  }

  return res.status(404).json({ error: 'Action not found' });
}
