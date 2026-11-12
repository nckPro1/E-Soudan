export type TeamRole = 'LEADER' | 'MEMBER' | 'OBSERVER'
export type TeamStatus = 'ACTIVE' | 'ARCHIVED'

export interface TeamSummary {
  id: number
  name: string
  description: string | null
  status: TeamStatus
  memberCount: number
  version: number
  createdAt: string
}

export interface TeamMemberInfo {
  userId: number
  fullName: string
  position: string | null
  teamRole: TeamRole
  joinedAt: string
}

export interface TeamDetail {
  id: number
  name: string
  description: string | null
  status: TeamStatus
  version: number
  createdAt: string
  members: TeamMemberInfo[]
}

export interface CreateTeamInput {
  name: string
  description: string | null
  leaderIds: number[]
  memberIds: number[]
  observerIds: number[]
}
