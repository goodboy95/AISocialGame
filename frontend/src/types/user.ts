export interface UserProfile {
  id: number;
  username: string;
  email: string;
  display_name: string | null;
  avatar: string | null;
  bio: string | null;
}

export interface UserMembershipSnapshot {
  roomId: number;
  roomName: string;
  roomCode: string;
  status: string;
  joinedAt: string;
  isHost: boolean;
  isAi: boolean;
  aiStyle: string | null;
  role: string | null;
  word: string | null;
  alive: boolean;
}

export interface UserOwnedRoomSnapshot {
  id: number;
  name: string;
  code: string;
  createdAt: string;
  status: string;
}

export interface UserExport {
  exported_at: string;
  profile: UserProfile;
  memberships: UserMembershipSnapshot[];
  ownedRooms: UserOwnedRoomSnapshot[];
  statistics: {
    joinedRooms: number;
    ownedRooms: number;
  };
}
