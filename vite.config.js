import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173,
    strictPort: true,
    configureServer(server) {
      server.middlewares.use('/api/proxy', async (req, res, next) => {
        try {
          const urlObj = new URL(req.url, `http://${req.headers.host}`);
          const targetUrl = urlObj.searchParams.get('url');

          if (!targetUrl) {
            console.log('[Vite Proxy] Missing URL param');
            res.statusCode = 400;
            res.end(JSON.stringify({ error: 'Missing url' }));
            return;
          }

          console.log('[Vite Proxy] Proxying to:', targetUrl);
          
          const target = new URL(targetUrl);
          const allowed = ['cdn.discordapp.com', 'media.discordapp.net', 'discord.com', 'discordapp.com'];
          if (!allowed.includes(target.hostname)) {
            res.statusCode = 403;
            res.end(JSON.stringify({ error: 'Forbidden host' }));
            return;
          }

          // Forward the request to Discord
          const response = await fetch(targetUrl, {
            headers: {
              'User-Agent': 'Disbox-Web/1.0',
            }
          });
          
          // Helper to copy headers
          const copyHeader = (name) => {
            const val = response.headers.get(name);
            if (val) res.setHeader(name, val);
          };

          copyHeader('Content-Type');
          copyHeader('Content-Length');
          copyHeader('Content-Range');
          
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.statusCode = response.status;
          
          const arrayBuffer = await response.arrayBuffer();
          res.end(Buffer.from(arrayBuffer));
        } catch (e) {
          console.error('Proxy error:', e);
          res.statusCode = 500;
          res.end(JSON.stringify({ error: e.message }));
        }
      });
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
