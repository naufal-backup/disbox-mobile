const ALLOWED_ORIGINS = (() => {
  const defaultOrigins = [
    'https://disbox-web-weld.vercel.app',
    'http://localhost:5173',
    'http://localhost:4173',
    'http://localhost:3000',
    'http://localhost',      // Capacitor Android
    'https://localhost',     // Capacitor Android (alternative)
    'capacitor://localhost', // Capacitor iOS
    'null' // Allow Electron's file:// protocol
  ];
  const custom = process.env.ALLOWED_ORIGINS?.split(',').map(s => s.trim()).filter(Boolean) || [];
  return [...defaultOrigins, ...custom];
})();

/**
 * Handle CORS for API routes
 * @param {Object} req - Vercel request
 * @param {Object} res - Vercel response
 * @returns {boolean} true if OPTIONS short-circuited, false otherwise
 */
export function handleCors(req, res) {
  const origin = req.headers['origin'] || '';
  const isAllowed = ALLOWED_ORIGINS.length === 0 || ALLOWED_ORIGINS.includes(origin);

  // Always set CORS headers for allowed origin
  if (isAllowed) {
    res.setHeader('Access-Control-Allow-Origin', origin);
  } else {
    // For non-allowed, don't set Allow-Origin (browser will block)
    // But still allow preflight to complete with 400/401 if needed
    res.setHeader('Access-Control-Allow-Origin', 'null');
  }

  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'Content-Type, Authorization, X-CSRF-Token, X-Requested-With'
  );
  res.setHeader('Access-Control-Max-Age', '86400'); // 24 hours

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return true;
  }

  return false;
}
