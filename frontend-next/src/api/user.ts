import request, { unwrap } from "./request"
import type { CurrentUser, LoginReq, LoginResp, RegisterReq } from "@/types/user"

export function login(data: LoginReq) {
  return unwrap<LoginResp>(request.post("/user-service/login", data))
}

export function register(data: RegisterReq) {
  return unwrap<string>(request.post("/user-service/register", data))
}

export function logout() {
  return unwrap<void>(request.post("/user-service/logout"))
}

export function queryCurrentUser() {
  return unwrap<CurrentUser>(request.get("/user-service/query"))
}
