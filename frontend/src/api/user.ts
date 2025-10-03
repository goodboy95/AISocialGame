import http from "./http";
import type { UserExport } from "../types/user";

export async function exportUserData(): Promise<UserExport> {
  const { data } = await http.get<UserExport>("/auth/me/export/");
  return data;
}

export async function deleteAccount(): Promise<void> {
  await http.delete("/auth/me/");
}
