import { verify } from './jwt.js';

/**
 * Middleware: require authentication
 * Accepts either:
 * - Authorization: Bearer <token>
 * - Cookie: session=<token>
 * If valid, attaches req.user and returns null (continue).
 * If invalid, sends 401 response and returns the response object.
 */
export function requireAuth(req, res) {
  let token = null;

  // 1. Authorization header (Bearer token)
  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.slice(7);
  }

  // 2. Fallback to cookie
  if (!token) {
    const cookies = req.headers.cookie || '';
    const sessionMatch = cookies.match(/session=([^;]+)/);
    if (sessionMatch) token = sessionMatch[1];
  }

  if (!token) {
    res.status(401).json({ error: 'Unauthorized' });
    return res;
  }

  const payload = verify(token);
  if (!payload) {
    res.status(401).json({ error: 'Invalid or expired session' });
    return res;
  }

  req.user = payload;
  return null;
}

/**
 * Ensure the requested identifier belongs to the authenticated user.
 * For cloud mode: identifier === user.identifier (username)
 * For manual mode: identifier === user.identifier (hashed webhook)
 * @param {Object} req - Vercel request with req.user set
 * @param {Object} res - Vercel response
 * @param {string} identifier - requested identifier
 * @returns {Object|null} res on error, null on success
 */
export function ensureIdentifierAccess(req, res, identifier) {
  if (!req.user) {
    // Should not happen if requireAuth called first
    res.status(401).json({ error: 'Unauthorized' });
    return res;
  }

  // Normalize: both to lowercase
  const normalizedRequested = (identifier || '').toLowerCase();
  const normalizedUser = (req.user.identifier || '').toLowerCase();

  if (normalizedRequested !== normalizedUser) {
    res.status(403).json({ error: 'Forbidden' });
    return res;
  }

  return null;
}

/**
 * Higher-order wrapper for route handlers that require auth + ownership.
 * Usage: export default withAuth(handler, ['query', 'body']);
 * @param {Function} handler - original handler(req, res)
 * @param {Array<string>} paramSources - ['query', 'body'] to specify where to find identifier
 * @returns {Function} wrapped handler
 */
export function withAuth(handler, paramSources = ['query']) {
  return async (req, res) => {
    const unauthorized = requireAuth(req, res);
    if (unauthorized) return unauthorized;

    // Extract identifier from configured sources
    let identifier = null;
    for (const source of paramSources) {
      if (source === 'query' && req.query?.identifier) {
        identifier = req.query.identifier;
        break;
      }
      if (source === 'body' && req.body?.identifier) {
        identifier = req.body.identifier;
        break;
      }
    }

    if (!identifier) {
      return res.status(400).json({ error: 'Missing identifier' });
    }

    const forbidden = ensureIdentifierAccess(req, res, identifier);
    if (forbidden) return forbidden;

    return handler(req, res);
  };
}
