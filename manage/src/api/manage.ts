import http from "./http";

export interface AiModelConfig {
  id: number;
  name: string;
  base_url: string;
  token: string;
  created_at: string;
  updated_at: string;
}

export interface AiModelConfigPayload {
  name: string;
  base_url: string;
  token: string;
}

export interface AiModelConfigSummary {
  id: number;
  name: string;
}

export interface UndercoverAiRole {
  id: number;
  name: string;
  model: AiModelConfigSummary;
  personality: string;
  created_at: string;
  updated_at: string;
}

export interface UndercoverAiRolePayload {
  name: string;
  model_id: number;
  personality: string;
}

export interface ManageOverview {
  models: AiModelConfig[];
  undercoverRoles: UndercoverAiRole[];
}

export async function fetchOverview(): Promise<ManageOverview> {
  const { data } = await http.get<ManageOverview>("/manage/overview/");
  return data;
}

export async function fetchAiModels(): Promise<AiModelConfig[]> {
  const { data } = await http.get<AiModelConfig[]>("/manage/ai-models/");
  return data;
}

export async function createAiModel(payload: AiModelConfigPayload): Promise<AiModelConfig> {
  const { data } = await http.post<AiModelConfig>("/manage/ai-models/", payload);
  return data;
}

export async function updateAiModel(id: number, payload: AiModelConfigPayload): Promise<AiModelConfig> {
  const { data } = await http.patch<AiModelConfig>(`/manage/ai-models/${id}/`, payload);
  return data;
}

export async function deleteAiModel(id: number): Promise<void> {
  await http.delete(`/manage/ai-models/${id}/`);
}

export async function fetchUndercoverRoles(): Promise<UndercoverAiRole[]> {
  const { data } = await http.get<UndercoverAiRole[]>("/manage/undercover/roles/");
  return data;
}

export async function createUndercoverRole(payload: UndercoverAiRolePayload): Promise<UndercoverAiRole> {
  const { data } = await http.post<UndercoverAiRole>("/manage/undercover/roles/", payload);
  return data;
}

export async function updateUndercoverRole(id: number, payload: UndercoverAiRolePayload): Promise<UndercoverAiRole> {
  const { data } = await http.patch<UndercoverAiRole>(`/manage/undercover/roles/${id}/`, payload);
  return data;
}

export async function deleteUndercoverRole(id: number): Promise<void> {
  await http.delete(`/manage/undercover/roles/${id}/`);
}
