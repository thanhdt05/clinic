import axios from "axios";

export const SESSION_EXPIRED = "clinic:session-expired";

const http = axios.create();

http.interceptors.request.use(async (request) => {
  if (!["get", "head", "options"].includes(request.method)) {
    const { data } = await http.get("/api/auth/csrf");
    request.headers.set(data.headerName, data.token);
  }
  return request;
});

http.interceptors.response.use(undefined, (error) => {
  if (error.response?.status === 401 && error.config?.url !== "/api/auth/login") {
    window.dispatchEvent(new Event(SESSION_EXPIRED));
  }
  return Promise.reject(error);
});

export default http;
