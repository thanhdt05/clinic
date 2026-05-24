import http from "./http";

export async function getCurrentUser(signal) {
  const { data } = await http.get("/api/auth/me", { signal });
  return data;
}

export async function loginApi(login, password) {
  try {
    await http.post("/api/auth/login", new URLSearchParams({ login, password }));
    return await getCurrentUser();
  } catch (error) {
    if (error.response?.status === 401) {
      throw new Error("Tên đăng nhập, email hoặc mật khẩu không hợp lệ.");
    }
    throw new Error("Đăng nhập thất bại.");
  }
}

export function logoutApi() {
  return http.post("/api/auth/logout");
}
