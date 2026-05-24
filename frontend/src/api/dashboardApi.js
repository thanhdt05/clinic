import http from "./http";

export async function getDashboardStats() {
  const { data } = await http.get("/api/dashboard/stats");
  return data || {};
}
