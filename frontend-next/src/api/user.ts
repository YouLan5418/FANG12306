import request, { unwrap } from "./request"
import type { CurrentUser, LoginReq, LoginResp, Passenger, RegisterReq } from "@/types/user"

export function login(data: LoginReq) {
  return unwrap<LoginResp>(request.post("/user-service/v1/login", data))
}

export function register(data: RegisterReq) {
  return unwrap<string>(request.post("/user-service/register", data))
}

export function logout() {
  return unwrap<void>(request.get("/user-service/logout"))
}

export function queryCurrentUser() {
  return unwrap<CurrentUser>(request.get("/user-service/query"))
}

export function queryPassengers() {
  return unwrap<Passenger[]>(request.get("/user-service/passenger/query"))
}
