import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    allowedHosts: ['localhost', '127.0.0.1', '.cpolar.top'],
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        configure: configureProxyErrorHandling
      },
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
        configure: configureProxyErrorHandling
      }
    }
  }
});

function configureProxyErrorHandling(proxy) {
  const handleProxyError = (error) => {
    if (isExpectedDisconnect(error)) {
      return;
    }
    console.error(error);
  };

  proxy.on("error", handleProxyError);
  proxy.on("proxyReq", (proxyReq) => {
    proxyReq.on("error", handleProxyError);
  });
  proxy.on("proxyReqWs", (proxyReq) => {
    proxyReq.on("error", handleProxyError);
  });
  proxy.on("proxyRes", (proxyRes) => {
    proxyRes.on("error", handleProxyError);
  });
  proxy.on("open", (proxySocket) => {
    proxySocket.on("error", handleProxyError);
  });
}

function isExpectedDisconnect(error) {
  const text = [
    error?.code,
    error?.errno,
    error?.syscall,
    error?.message
  ].filter(Boolean).join(" ");

  return /ECONNABORTED|ECONNRESET|EPIPE|socket hang up/i.test(text);
}
