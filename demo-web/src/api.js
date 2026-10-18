export class ApiError extends Error {
  constructor(message, status = 0, requestId = "") {
    super(message);
    this.status = status;
    this.requestId = requestId;
  }
}
export async function api(path, { method = "GET", body, key } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 23000);
  try {
    const response = await fetch(`/api${path}`, {
      method,
      signal: controller.signal,
      cache: "no-store",
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    let result;
    try {
      result = await response.json();
    } catch {
      throw new ApiError(
        "后端暂时未响应，请检查本地服务后重试。",
        response.status,
      );
    }
    if (!response.ok)
      throw new ApiError(
        result.message || "请求失败",
        response.status,
        response.headers.get("X-Request-Id") || "",
      );
    return result.data;
  } catch (error) {
    if (error.name === "AbortError")
      throw new ApiError(
        "请求超时；如已提交生成，请继续查询同一次生成，避免重复提交。",
      );
    if (error instanceof ApiError) throw error;
    throw new ApiError("无法连接本地服务，请检查后端和数据库是否运行。");
  } finally {
    clearTimeout(timer);
  }
}
