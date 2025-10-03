import http from "./http";

export interface AIStyleMeta {
  key: string;
  label: string;
  description: string;
}

export async function fetchAiStyles(): Promise<AIStyleMeta[]> {
  const { data } = await http.get<{ styles: AIStyleMeta[] }>("/meta/styles/");
  return data.styles;
}
