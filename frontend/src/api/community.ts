import request from '@/utils/request'
import type { PageResponse } from '@/api/types'

export interface Post {
  id: number
  userId: number
  username?: string
  title: string
  content: string
  category: string
  tags: string
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  isTop: boolean
  isFeatured?: boolean
  likedByMe: boolean
  createTime: string
  lastReplyTime?: string
}

export interface Comment {
  id: number
  postId: number
  userId: number
  username: string
  parentId: number | null
  content: string
  likeCount: number
  likedByMe: boolean
  likedByAuthor: boolean
  repliedByAuthor: boolean
  pinned: boolean
  createTime: string
  replies?: Comment[]
}

export interface PostRequest {
  title: string
  content: string
  category: string
  tags?: string
}

export interface CommentRequest {
  content: string
  parentId?: number
  gameId?: string
}

export function fetchPosts(params: { page: number; size: number; category?: string; q?: string }) {
  return request.get<PageResponse<Post>>('/posts', { params })
}

export function fetchHotPosts(params: { page: number; size: number }) {
  return request.get<PageResponse<Post>>('/posts/hot', { params })
}

export function fetchPost(id: number) {
  return request.get<Post>(`/posts/${id}`)
}

export function createPost(data: PostRequest) {
  return request.post<Post>('/posts', data)
}

export function updatePost(id: number, data: PostRequest) {
  return request.put<Post>(`/posts/${id}`, data)
}

export function deletePost(id: number) {
  return request.delete(`/posts/${id}`)
}

export function togglePostLike(id: number) {
  return request.post<{ liked: boolean }>(`/posts/${id}/like`)
}

export function fetchComments(postId: number) {
  return request.get<Comment[]>(`/comments/post/${postId}`)
}

export function fetchGameComments(gameId: string) {
  return request.get<Comment[]>(`/comments/game/${gameId}`)
}

export function createComment(data: CommentRequest & { postId?: number; gameId?: string }) {
  return request.post<Comment>('/comments', data)
}

export function deleteComment(id: number) {
  return request.delete(`/comments/${id}`)
}

export function toggleCommentLike(id: number) {
  return request.post<{ liked: boolean }>(`/comments/${id}/like`)
}

export function toggleCommentPin(id: number) {
  return request.post(`/comments/${id}/pin`)
}
