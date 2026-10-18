import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { demoToken, permittedRequest } from "./local-proxy.mjs";

const port = Number(process.env.DEMO_WEB_PORT || 9090);
const apiPort = Number(process.env.DEMO_API_PORT || 9089);
if (![port, apiPort].every((p) => Number.isInteger(p) && p > 0 && p <= 65535))
  throw new Error("Invalid local port");
const token = demoToken(import.meta.url);
export default defineConfig({
  define: {
    __LOCAL_API_DOCS_URL__: JSON.stringify(
      `http://127.0.0.1:${apiPort}/ncd-ai/doc.html`,
    ),
  },
  plugins: [
    vue(),
    {
      name: "local-demo-editor-only",
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (!req.url.startsWith("/api")) return next();
          const status = !permittedRequest(req, port)
            ? 403
            : !token
              ? 503
              : null;
          if (status) {
            res.writeHead(status, {
              "Content-Type": "application/json",
              "Cache-Control": "no-store",
            });
            return res.end(
              JSON.stringify({
                code: status,
                message:
                  status === 503
                    ? "请先运行 scripts/run-demo.sh，初始化本地 DEMO 身份。"
                    : "仅允许本地工作台的方案编辑请求。",
              }),
            );
          }
          res.setHeader("Cache-Control", "no-store");
          next();
        });
      },
    },
  ],
  server: {
    host: "127.0.0.1",
    port,
    strictPort: true,
    cors: false,
    fs: { strict: true, allow: [new URL(".", import.meta.url).pathname] },
    proxy: {
      "^/api/": {
        target: `http://127.0.0.1:${apiPort}`,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, "/ncd-ai"),
        proxyTimeout: 20000,
        timeout: 25000,
        configure(proxy) {
          proxy.on("proxyReq", (request) => {
            request.setHeader("Authorization", token);
            request.removeHeader("cookie");
          });
        },
      },
    },
  },
});
