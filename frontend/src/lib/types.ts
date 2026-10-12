// Mirrors the backend DTOs.

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type ProjectRole = 'VIEWER' | 'MEMBER' | 'ADMIN' | 'OWNER'
export type GlobalRole = 'USER' | 'ADMIN'

export interface UserSummary {
  id: number
  fullName: string
  email: string
  avatarColor: string
}

export interface UserProfile extends UserSummary {
  role: GlobalRole
  emailNotifications: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

export interface DemoLogin {
  email: string
  fullName: string
  description: string
  color: string
  password: string
}

export interface ProjectSummary {
  id: number
  key: string
  name: string
  description: string | null
  owner: UserSummary
  myRole: ProjectRole
  taskCount: number
  doneCount: number
  memberCount: number
  updatedAt: string
}

export interface ProjectDetail {
  id: number
  key: string
  name: string
  description: string | null
  owner: UserSummary
  myRole: ProjectRole
  createdAt: string
  updatedAt: string
}

export interface TaskSummary {
  id: number
  projectId: number
  key: string
  title: string
  priority: Priority
  columnId: number
  columnName: string
  position: string
  assignee: UserSummary | null
  dueDate: string | null
  completedAt: string | null
  commentCount: number
  createdAt: string
  version: number
}

export interface TaskDetail {
  id: number
  key: string
  projectId: number
  title: string
  description: string | null
  priority: Priority
  columnId: number
  columnName: string
  assignee: UserSummary | null
  reporter: UserSummary
  dueDate: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface ColumnView {
  id: number
  name: string
  position: string
  wipLimit: number | null
  doneColumn: boolean
  tasks: TaskSummary[]
}

export interface MemberView {
  user: UserSummary
  role: ProjectRole
  joinedAt: string
}

export interface BoardView {
  project: ProjectDetail
  columns: ColumnView[]
  members: MemberView[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface CommentView {
  id: number
  taskId: number
  body: string
  author: UserSummary
  edited: boolean
  createdAt: string
  updatedAt: string
}

export interface ActivityView {
  id: number
  action: string
  entityType: 'PROJECT' | 'TASK' | 'COLUMN' | 'COMMENT' | 'MEMBER'
  entityId: number | null
  summary: string
  actor: UserSummary | null
  createdAt: string
}

export interface NotificationView {
  id: number
  type: 'TASK_ASSIGNED' | 'TASK_COMMENTED' | 'TASK_DUE_SOON' | 'ADDED_TO_PROJECT'
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

export interface AdminUserView {
  id: number
  email: string
  fullName: string
  role: GlobalRole
  enabled: boolean
  avatarColor: string
  createdAt: string
}

export interface SystemStats {
  users: number
  projects: number
  tasks: number
  openTasks: number
  comments: number
}

export type BoardEventType =
  | 'TASK_CREATED'
  | 'TASK_UPDATED'
  | 'TASK_MOVED'
  | 'TASK_DELETED'
  | 'COLUMNS_CHANGED'
  | 'COMMENTS_CHANGED'
  | 'PROJECT_UPDATED'
  | 'MEMBERS_CHANGED'
  | 'PROJECT_DELETED'

export interface BoardEvent {
  type: BoardEventType
  projectId: number
  actorId: number
  payload: unknown
  at: string
}

export const ROLE_RANK: Record<ProjectRole, number> = { VIEWER: 0, MEMBER: 1, ADMIN: 2, OWNER: 3 }

export const atLeast = (role: ProjectRole | undefined, required: ProjectRole) =>
  role !== undefined && ROLE_RANK[role] >= ROLE_RANK[required]
