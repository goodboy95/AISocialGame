import type { WordPair, WordPairPayload } from "../types/word-pairs";
import http from "./http";

export interface WordPairQuery {
  topic?: string;
  difficulty?: string;
  q?: string;
}

export async function fetchWordPairs(params: WordPairQuery = {}): Promise<WordPair[]> {
  const response = await http.get<WordPair[]>("/games/word-pairs/", { params });
  return response.data;
}

export async function createWordPair(payload: WordPairPayload): Promise<WordPair> {
  const response = await http.post<WordPair>("/games/word-pairs/", payload);
  return response.data;
}

export async function updateWordPair(id: number, payload: Partial<WordPairPayload>): Promise<WordPair> {
  const response = await http.patch<WordPair>(`/games/word-pairs/${id}/`, payload);
  return response.data;
}

export async function deleteWordPair(id: number): Promise<void> {
  await http.delete(`/games/word-pairs/${id}/`);
}

export interface BulkImportPayload {
  items: WordPairPayload[];
}

export interface BulkImportResponse {
  items: WordPair[];
  created: number;
}

export async function importWordPairs(payload: BulkImportPayload): Promise<BulkImportResponse> {
  const response = await http.post<BulkImportResponse>("/games/word-pairs/import/", payload);
  return response.data;
}

export interface ExportResponse {
  items: WordPair[];
}

export async function exportWordPairs(params: WordPairQuery = {}): Promise<ExportResponse> {
  const response = await http.get<ExportResponse>("/games/word-pairs/export/", { params });
  return response.data;
}
