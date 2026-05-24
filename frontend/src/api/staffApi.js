import http from "./http";

export async function getAllEmployees() {
  const { data } = await http.get("/api/admin/employees");
  return data;
}

export async function getDoctors() {
  const { data } = await http.get("/api/admin/doctors");
  return data;
}

export async function deleteEmployee(id) {
  const { data } = await http.delete(`/api/admin/employees/${id}`);
  return data;
}
