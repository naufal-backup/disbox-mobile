/**
 * /api/discord/discover.js
 *
 * Mendeteksi pesan metadata terbaru di channel menggunakan User access_token.
 * Menghindari ketergantungan pada Webhook Name (ID).
 */

export default async function handler(req, res) {
  const { channel_id, access_token } = req.query;

  if (!channel_id) {
    return res.status(400).json({ ok: false, error: 'Missing channel_id' });
  }

  const cors = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  };

  if (req.method === 'OPTIONS') {
    return res.status(204).set(cors).end();
  }

  try {
    const BOT_TOKEN = process.env.DISCORD_BOT_TOKEN;
    const authHeader = access_token ? `Bearer ${access_token}` : (BOT_TOKEN ? `Bot ${BOT_TOKEN}` : null);

    if (!authHeader) {
      console.warn('[discover] No authorization available (User or Bot).');
      return res.status(200).json({ ok: true, found: false, message: 'No authorization available to search channel' });
    }

    // 1. Fetch 50 pesan terbaru di channel tersebut
    const messagesRes = await fetch(`https://discord.com/api/v10/channels/${channel_id}/messages?limit=50`, {
      headers: { 'Authorization': authHeader },
    });

    if (!messagesRes.ok) {
      // Jika pakai user token dan 401, coba fallback ke Bot Token jika ada
      if (messagesRes.status === 401 && access_token && BOT_TOKEN) {
        console.log('[discover] User token failed, trying fallback to Bot Token...');
        const botRes = await fetch(`https://discord.com/api/v10/channels/${channel_id}/messages?limit=50`, {
          headers: { 'Authorization': `Bot ${BOT_TOKEN}` },
        });
        if (botRes.ok) {
          const messages = await botRes.json();
          return processMessages(res, messages);
        }
      }
      
      const err = await messagesRes.json();
      console.error('[discover] Failed to fetch messages:', err);
      return res.status(messagesRes.status).json({ ok: false, error: err.message || 'Failed to fetch messages' });
    }

    const messages = await messagesRes.json();
    return processMessages(res, messages);

  } catch (e) {
    console.error('[discover] Fatal:', e.message);
    return res.status(500).json({ ok: false, error: e.message });
  }
}

function processMessages(res, messages) {
  console.log(`[discover] Scanning ${messages.length} messages...`);
  
  // 2. Cari pesan terbaru yang mengandung metadata.json secara fleksibel
  const metadataMessage = messages.find(m => 
    m.attachments?.some(a => a.filename.toLowerCase().includes('metadata.json'))
  );

  if (!metadataMessage) {
    console.log('[discover] No metadata file found in history.');
    return res.status(200).json({ ok: true, found: false, message: 'No metadata file found' });
  }

  const attachment = metadataMessage.attachments.find(a => a.filename.toLowerCase().includes('metadata.json'));
  console.log(`[discover] Found file: ${attachment.filename} in message ${metadataMessage.id}`);

  return res.status(200).json({
    ok: true,
    found: true,
    message_id: metadataMessage.id,
    metadata_url: attachment.url,
    filename: attachment.filename,
    timestamp: metadataMessage.timestamp,
  });
}
