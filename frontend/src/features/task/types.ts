export type TaskStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Person {
  id: number
  fullName: string
}

export interface Submission {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: number
  uploadedAt: string
}

export interface Task {
  id: number
  teamId: number
  teamName: string | null
  title: string
  description: string | null
  assignee: Person | null
  assignedBy: Person | null
  dueDate: string | null
  priority: TaskPriority
  status: TaskStatus
  requireFile: boolean
  revisionReason: string | null
  version: number
  createdAt: string
  submissions: Submission[]
}

export interface HistoryEntry {
  id: number
  fromStatus: TaskStatus | null
  toStatus: TaskStatus
  changedBy: Person | null
  changedAt: string
  note: string | null
}

export interface TaskInput {
  title: string
  description: string | null
  assigneeId: number
  dueDate: string | null
  priority: TaskPriority
  requireFile: boolean
}

export interface TaskListQuery {
  page: number
  size: number
  q?: string
  status?: TaskStatus | ''
  assigneeId?: number | ''
}

export interface TaskComment {
  id: number
  author: Person | null
  body: string
  createdAt: string
}
