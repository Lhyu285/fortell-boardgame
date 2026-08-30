export async function apiRequest(path, options = {}) {
  const response = await fetch(path, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {})
    },
    ...options
  });

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { message: text };
    }
  }

  if (!response.ok) {
    throw new Error(data?.message ?? response.statusText ?? "请求失败");
  }
  return data;
}

export function apiGet(path) {
  return apiRequest(path, { method: "GET" });
}

export function apiPost(path, body) {
  return apiRequest(path, {
    method: "POST",
    body: JSON.stringify(body ?? {})
  });
}

export function apiDelete(path) {
  return apiRequest(path, { method: "DELETE" });
}
