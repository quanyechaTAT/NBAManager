export interface RAGConversationMessage {
  role: 'user' | 'assistant'
  content: string
  error?: string
}

export interface RAGConversationTurn {
  role: 'user' | 'assistant'
  content: string
}

const HISTORY_TURN_LIMIT = 8
const HISTORY_CONTENT_LIMIT = 1000

export function buildRagConversationHistory(
  messages: RAGConversationMessage[],
): RAGConversationTurn[] {
  const validMessages = messages
    .filter(message => !message.error && message.content.trim().length > 0)
  const completeTurns: RAGConversationTurn[] = []
  for (let index = 0; index < validMessages.length - 1; index += 1) {
    const user = validMessages[index]
    const assistant = validMessages[index + 1]
    if (user.role !== 'user' || assistant.role !== 'assistant') continue
    completeTurns.push(
      { role: user.role, content: user.content.trim().slice(0, HISTORY_CONTENT_LIMIT) },
      { role: assistant.role, content: assistant.content.trim().slice(0, HISTORY_CONTENT_LIMIT) },
    )
    index += 1
  }
  return completeTurns.slice(-HISTORY_TURN_LIMIT)
}
