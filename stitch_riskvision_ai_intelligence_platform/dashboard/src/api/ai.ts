import { apiClient } from './client';

export interface ChatMessage {
  sender: 'user' | 'assistant';
  text: string;
}

export interface ChatResponse {
  success: boolean;
  reply?: string;
  content?: string;
  error?: string;
  message?: string;
}

export async function sendChatMessage(message: string, history: ChatMessage[] = []): Promise<string> {
  try {
    const formattedHistory = history.map((msg) => ({
      sender: msg.sender,
      role: msg.sender === 'user' ? 'user' : 'assistant',
      text: msg.text,
    }));

    const response = await apiClient.post<ChatResponse>('/ai/chat', {
      message,
      history: formattedHistory,
    });

    if (response.data && response.data.success) {
      return response.data.reply || response.data.content || 'Response received.';
    }

    if (response.data && (response.data.error || response.data.message)) {
      throw new Error(response.data.error || response.data.message);
    }

    return 'Response received.';
  } catch (error: any) {
    const errMessage = error.response?.data?.message || error.response?.data?.error || error.message || 'AI Copilot service unavailable';
    throw new Error(errMessage);
  }
}
