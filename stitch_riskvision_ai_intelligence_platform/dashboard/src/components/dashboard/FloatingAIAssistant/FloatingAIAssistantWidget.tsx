import React, { useState, useRef, useEffect } from 'react';
import GlassCard from '../../common/GlassCard';
import { MessageSquare, Send, Sparkles, X, Loader2, AlertCircle } from 'lucide-react';
import { sendChatMessage, type ChatMessage } from '../../../api/ai';

export const FloatingAIAssistantWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    { sender: 'assistant', text: 'AI Copilot interface active. Ask me about repository risks, telemetry, or system health.' },
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) {
      scrollToBottom();
    }
  }, [messages, isLoading, isOpen]);

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMsg = input.trim();
    const updatedMessages: ChatMessage[] = [...messages, { sender: 'user', text: userMsg }];

    setMessages(updatedMessages);
    setInput('');
    setIsLoading(true);
    setError(null);

    try {
      console.log('[Frontend] Sending message to AI Copilot backend:', userMsg);
      const aiReply = await sendChatMessage(userMsg, updatedMessages);
      console.log('[Frontend] Received AI response successfully:', aiReply.substring(0, 50));
      setMessages((prev) => [...prev, { sender: 'assistant', text: aiReply }]);
    } catch (err: any) {
      console.error('[Frontend] AI Copilot request failed:', err);
      const errorMessage = err.message || 'Failed to connect to AI Copilot service';
      setError(errorMessage);
      setMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: `⚠️ Error: ${errorMessage}. Please verify backend connection or OpenRouter configuration.`,
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 z-40 font-mono max-w-[calc(100vw-2rem)]">
      {!isOpen ? (
        <button
          onClick={() => setIsOpen(true)}
          className="h-12 w-12 rounded-full bg-neon-blue text-cyber-950 flex items-center justify-center shadow-neon-blue hover:scale-105 active:scale-95 transition-all duration-300 animate-float cursor-pointer ml-auto"
          title="Open AI Copilot"
        >
          <MessageSquare size={20} />
        </button>
      ) : (
        <GlassCard className="w-[calc(100vw-2rem)] sm:w-80 h-[26rem] sm:h-[28rem] flex flex-col justify-between border-neon-blue/30 shadow-neon-blue/15 animate-count-up p-4 bg-cyber-900/95 backdrop-blur-md">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2">
            <div className="flex items-center gap-1.5 text-neon-blue">
              <Sparkles size={14} className={isLoading ? 'animate-spin' : ''} />
              <span className="font-bold text-xs uppercase tracking-wider">
                AI COPILOT TERMINAL
              </span>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="text-slate-400 hover:text-slate-100 p-0.5 rounded hover:bg-cyber-800/40"
              title="Close terminal"
            >
              <X size={14} />
            </button>
          </div>

          {/* Error Banner */}
          {error && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-[10px] p-2 rounded mb-2 flex items-center gap-1.5">
              <AlertCircle size={12} className="shrink-0" />
              <span className="truncate">{error}</span>
            </div>
          )}

          {/* Message List */}
          <div className="flex-1 overflow-y-auto space-y-2 p-1 text-[11px] scrollbar-thin">
            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`p-2.5 rounded leading-relaxed whitespace-pre-wrap max-w-[90%] ${
                  msg.sender === 'user'
                    ? 'bg-neon-blue/15 border border-neon-blue/30 text-slate-100 self-end ml-auto shadow-sm'
                    : 'bg-cyber-950/90 border border-slate-800 text-slate-300'
                }`}
              >
                {msg.text}
              </div>
            ))}

            {/* Loading Indicator */}
            {isLoading && (
              <div className="p-2.5 rounded bg-cyber-950 border border-slate-800 text-neon-blue max-w-[85%] flex items-center gap-2">
                <Loader2 size={12} className="animate-spin" />
                <span className="text-[10px] text-slate-400">Copilot is thinking...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input Area */}
          <div className="flex items-center gap-2 border-t border-slate-800 pt-2 mt-2">
            <input
              type="text"
              value={input}
              disabled={isLoading}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              placeholder={isLoading ? 'Generating response...' : 'Ask Copilot...'}
              className="flex-1 py-1.5 text-xs border border-slate-300 focus:border-neon-blue rounded px-2 bg-white text-black placeholder-[#6B7280] disabled:bg-slate-200 disabled:text-slate-500 focus:outline-none focus:ring-1 focus:ring-neon-blue font-sans"
              style={{ color: '#000000', backgroundColor: '#FFFFFF' }}
            />
            <button
              onClick={handleSend}
              disabled={isLoading || !input.trim()}
              className="h-8 w-8 rounded bg-neon-blue/10 border border-neon-blue/30 hover:border-neon-blue hover:bg-neon-blue/20 text-neon-blue flex items-center justify-center transition-all duration-300 disabled:opacity-40 disabled:cursor-not-allowed"
              title="Send message"
            >
              {isLoading ? <Loader2 size={12} className="animate-spin" /> : <Send size={12} />}
            </button>
          </div>
        </GlassCard>
      )}
    </div>
  );
};

export default FloatingAIAssistantWidget;
