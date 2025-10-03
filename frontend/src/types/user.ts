export interface UserProfile {
  id: number;
  username: string;
  email: string;
  display_name: string | null;
  avatar: string | null;
  bio: string | null;
}
