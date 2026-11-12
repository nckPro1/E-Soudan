// Gọi API của feature team. Component chỉ gọi hàm ở đây, không gọi axios trực tiếp.
import { http } from '../../shared/api/http'
import type { ApiSuccess, PageResult } from '../../shared/types/api'
import type { CreateTeamInput, TeamDetail, TeamRole, TeamStatus, TeamSummary } from './types'

export async function listTeams(query: {
  page: number
  size: number
  q?: string
  status?: TeamStatus | ''
}): Promise<PageResult<TeamSummary>> {
  const params: Record<string, string | number> = { page: query.page, size: query.size }
  if (query.q) params.q = query.q
  if (query.status) params.status = query.status
  const { data } = await http.get<ApiSuccess<PageResult<TeamSummary>>>('/teams', { params })
  return data.data
}

export async function getTeam(id: number): Promise<TeamDetail> {
  const { data } = await http.get<ApiSuccess<TeamDetail>>(`/teams/${id}`)
  return data.data
}

export async function createTeam(input: CreateTeamInput): Promise<TeamDetail> {
  const { data } = await http.post<ApiSuccess<TeamDetail>>('/teams', input)
  return data.data
}

export async function updateTeam(
  id: number,
  input: { name: string; description: string | null; version: number },
): Promise<TeamDetail> {
  const { data } = await http.put<ApiSuccess<TeamDetail>>(`/teams/${id}`, input)
  return data.data
}

export async function archiveTeam(id: number, version: number): Promise<TeamDetail> {
  const { data } = await http.post<ApiSuccess<TeamDetail>>(`/teams/${id}/archive`, { version })
  return data.data
}

export async function addMember(teamId: number, userId: number, teamRole: TeamRole): Promise<TeamDetail> {
  const { data } = await http.post<ApiSuccess<TeamDetail>>(`/teams/${teamId}/members`, { userId, teamRole })
  return data.data
}

export async function changeMemberRole(teamId: number, userId: number, teamRole: TeamRole): Promise<TeamDetail> {
  const { data } = await http.put<ApiSuccess<TeamDetail>>(`/teams/${teamId}/members/${userId}`, { teamRole })
  return data.data
}

export async function removeMember(teamId: number, userId: number): Promise<TeamDetail> {
  const { data } = await http.delete<ApiSuccess<TeamDetail>>(`/teams/${teamId}/members/${userId}`)
  return data.data
}
