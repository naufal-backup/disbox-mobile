import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  console.error('[jwt] Missing JWT_SECRET environment variable');
}

/**
 * Sign a JWT token
 * @param {Object} payload - { identifier, mode }
 * @returns {string} JWT token
 */
export function sign(payload) {
  if (!JWT_SECRET) {
    throw new Error('JWT_SECRET not configured');
  }
  // Default expiration: 7 days
  return jwt.sign(payload, JWT_SECRET, { expiresIn: '7d' });
}

/**
 * Verify a JWT token
 * @param {string} token - JWT token
 * @returns {Object|null} decoded payload or null if invalid
 */
export function verify(token) {
  if (!JWT_SECRET) {
    console.error('[jwt] Cannot verify: JWT_SECRET not configured');
    return null;
  }
  try {
    return jwt.verify(token, JWT_SECRET);
  } catch (e) {
    if (e.name === 'TokenExpiredError') {
      console.warn('[jwt] Token expired');
    } else {
      console.warn('[jwt] Invalid token:', e.message);
    }
    return null;
  }
}
