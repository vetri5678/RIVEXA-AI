import React, { useState } from 'react';
import GlassCard from '../../common/GlassCard';
import { MessageSquare, Send, Sparkles, X } from 'lucide-react';
import { useOverview, useHighRiskProjects } from '../../../hooks/useDashboard';

export const FloatingAIAssistantWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<Array<{ sender: 'user' | 'assistant'; text: string }>>([
    { sender: 'assistant', text: 'AI prediction interface active. Ask me about critical repository risks.' },
  ]);
  const [input, setInput] = useState('');

  const { data: overview } = useOverview();
  const { data: critical } = useHighRiskProjects(5);

  const handleSend = () => {
    if (!input.trim()) return;

    const userMsg = input.trim();
    setMessages((prev) => [...prev, { sender: 'user', text: userMsg }]);
    setInput('');

    setTimeout(() => {
      let reply = "Processing telemetry scan. Please state repository ID or specific failure factors.";

      const query = userMsg.toLowerCase();
      if (query.includes('critical') || query.includes('highest') || query.includes('risk')) {
        if (critical && critical.projects.length > 0) {
          const names = critical.projects.map((p) => p.project_name).join(', ');
          reply = `Active threat indicators detected on: ${names}. Average failure probability is ${(
            critical.projects.reduce((acc, p) => acc + p.failure_probability, 0) /
            critical.projects.length *
            100
          ).toFixed(1)}%.`;
        } else {
          reply = 'Nominal status. No critical or high-risk repositories detected.';
        }
      } else if (query.includes('summary') || query.includes('status')) {
        if (overview) {
          reply = `Currently monitoring ${overview.total_projects} repositories. Graveyard index is ${overview.graveyard_index} with model confidence at ${(overview.avg_confidence * 100).toFixed(1)}%.`;
        }
      }

      setMessages((prev) => [...prev, { sender: 'assistant', text: reply }]);
    }, 800);
  };

  return (
    <div className="fixed bottom-6 right-6 z-40 font-mono">
      {!isOpen ? (
        <button
          onClick={() => setIsOpen(true)}
          className="h-12 w-12 rounded-full bg-neon-blue text-cyber-950 flex items-center justify-center shadow-neon-blue hover:scale-105 active:scale-95 transition-all duration-300 animate-float"
        >
          <MessageSquare size={20} />
        </button>
      ) : (
        <GlassCard className="w-80 h-96 flex flex-col justify-between border-neon-blue/30 shadow-neon-blue/15 animate-count-up p-4 bg-cyber-900/95 backdrop-blur-md">
          <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2">
            <div className="flex items-center gap-1.5 text-neon-blue">
              <Sparkles size={14} />
              <span className="font-bold text-xs uppercase tracking-wider">
                AI COPILOT TERMINAL
              </span>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="text-slate-400 hover:text-slate-100 p-0.5 rounded hover:bg-cyber-800/40"
            >
              <X size={14} />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto space-y-2 p-1 text-[10px]">
            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`p-2 rounded leading-relaxed max-w-[85%] ${
                  msg.sender === 'user'
                    ? 'bg-neon-blue/10 border border-neon-blue/20 text-slate-100 self-end ml-auto'
                    : 'bg-cyber-950 border border-slate-800 text-slate-400'
                }`}
              >
                {msg.text}
              </div>
            ))}
          </div>

          <div className="flex items-center gap-2 border-t border-slate-800 pt-2 mt-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              placeholder="Ask Copilot..."
              className="flex-1 glass-input py-1 text-xs"
            />
            <button
              onClick={handleSend}
              className="h-8 w-8 rounded bg-neon-blue/10 border border-neon-blue/30 hover:border-neon-blue hover:bg-neon-blue/20 text-neon-blue flex items-center justify-center transition-all duration-300"
            >
              <Send size={10} />
            </button>
          </div>
        </GlassCard>
      )}
    </div>
  );
};
export default FloatingAIAssistantWidget;
