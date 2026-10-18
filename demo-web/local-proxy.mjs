import { readFileSync } from "node:fs";

export function demoToken(root) {
  try {
    const line = readFileSync(new URL("../.env.demo", root), "utf8")
      .split(/\r?\n/)
      .find((s) => s.startsWith("HEALTH_DEMO_API_TOKEN="));
    return (
      line
        ?.slice(line.indexOf("=") + 1)
        .trim()
        .replace(/^(['"])(.*)\1$/, "$2") || ""
    );
  } catch {
    return "";
  }
}

// A deliberately narrow local editor proxy. It has no reviewer/patient role switch.
export function permittedRequest(req, port) {
  const hosts = new Set([`127.0.0.1:${port}`, `localhost:${port}`]);
  if (!hosts.has(req.headers.host)) return false;
  if (
    req.headers.origin &&
    ![...hosts].some((h) => req.headers.origin === `http://${h}`)
  )
    return false;
  if (req.headers["sec-fetch-site"] === "cross-site") return false;
  const rawPath = req.url.split("?")[0];
  if (/%|\.\.|\\/.test(rawPath)) return false;
  const id = "[a-zA-Z0-9-]+";
  const routes = {
    GET: [
      `/api/health`,
      `/api/patients`,
      `/api/patients/${id}/health-summary`,
      `/api/plan-versions/${id}`,
      `/api/plan-versions/${id}/generation-jobs`,
      `/api/plan-generation-jobs/${id}`,
    ],
    POST: [
      "/api/care-plans",
      `/api/plan-versions/${id}/generation-jobs`,
      `/api/plan-generation-jobs/${id}/adoptions`,
    ],
    PATCH: [`/api/plan-versions/${id}`],
  };
  if (
    !(routes[req.method] || []).some((route) =>
      new RegExp(`^${route}$`).test(rawPath),
    )
  )
    return false;
  return (
    ["GET", "HEAD"].includes(req.method) ||
    (req.headers["content-type"] || "").split(";")[0] === "application/json"
  );
}
