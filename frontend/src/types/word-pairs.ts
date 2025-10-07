export interface WordPair {
  id: number;
  topic: string;
  civilian_word: string;
  undercover_word: string;
  difficulty: "easy" | "medium" | "hard";
  created_at: string;
  updated_at: string;
}

export type WordPairPayload = Pick<WordPair, "topic" | "civilian_word" | "undercover_word" | "difficulty">;
