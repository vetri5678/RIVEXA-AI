import React, { useState, useEffect } from 'react';
import { Sparkles, AlertTriangle, CheckCircle, RefreshCw, Cpu, Code } from 'lucide-react';
import MarkdownRenderer from './MarkdownRenderer';

interface AICardProps {
  title: string;
  subtitle?: string;
  content: string;
  isLoading: boolean;
  onRetry?: () => void;
  className?: string;
}

export const AICard: React.FC<AICardProps> = ({
  title,
  subtitle = 'Cognitive Analysis Engine Active',
  content,
  isLoading,
  onRetry,
  className = '',
}) => {
  const [typedContent, setTypedContent] = useState('');
  const [parsedData, setParsedData] = useState<any>(null);
  const [isAccordionOpen, setIsAccordionOpen] = useState(true);

  // Parse JSON or degrade to markdown
  useEffect(() => {
    if (!content) {
      setParsedData(null);
      return;
    }

    // Try parsing as JSON first
    let cleanJson = content.trim();
    
    // Stripe away markdown block wrapper if present
    if (cleanJson.startsWith('```json')) {
      cleanJson = cleanJson.slice(7);
    }
    if (cleanJson.startsWith('```')) {
      cleanJson = cleanJson.slice(3);
    }
    if (cleanJson.endsWith('```')) {
      cleanJson = cleanJson.slice(0, -3);
    }
    cleanJson = cleanJson.trim();

    try {
      const parsed = JSON.parse(cleanJson);
      setParsedData(parsed);
    } catch {
      // Degrade to plain text markdown
      setParsedData(null);
    }
  }, [content]);

  // Typing simulation effect for smooth cognitive UX
  useEffect(() => {
    if (isLoading) {
      setTypedContent('');
      return;
    }
    if (!content) return;

    let index = 0;
    const interval = setInterval(() => {
      setTypedContent((prev) => prev + content.charAt(index));
      index++;
      if (index >= content.length) {
        clearInterval(interval);
      }
    }, 2); // Fast and smooth typing simulation

    return () => clearInterval(interval);
  }, [content, isLoading]);

  // Severity color mappings
  const getSeverityBadge = (severity: string) => {
    const sev = (severity || 'LOW').toUpperCase();
    const style = {
      CRITICAL: 'bg-red-500/10 text-red-400 border border-red-500/30 shadow-[0_0_8px_rgba(239,68,68,0.2)]',
      HIGH: 'bg-amber-500/10 text-amber-400 border border-amber-500/30 shadow-[0_0_8px_rgba(245,158,11,0.2)]',
      MEDIUM: 'bg-blue-500/10 text-cyan-400 border border-blue-500/30 shadow-[0_0_8px_rgba(6,182,212,0.2)]',
      LOW: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 shadow-[0_0_8px_rgba(16,185,129,0.2)]',
    }[sev] ?? 'bg-slate-800 text-slate-300 border border-slate-700';

    return (
      <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold font-mono tracking-widest uppercase ${style}`}>
        {sev}
      </span>
    );
  };

  return (
    <div className={`glass relative rounded-2xl border border-blue-500/20 bg-gradient-to-br from-cyber-950/80 to-blue-950/40 p-5 shadow-[0_0_20px_rgba(59,130,246,0.15)] overflow-hidden transition-all duration-300 hover:shadow-[0_0_30px_rgba(6,182,212,0.25)] hover:border-cyan-500/30 ${className}`}>
      
      {/* Laser Scanning Line Overlay when loading */}
      {isLoading && (
        <div className="absolute inset-0 w-full h-[2px] bg-gradient-to-r from-transparent via-cyan-400 to-transparent opacity-60 animate-scan z-10" />
      )}

      {/* Header */}
      <div className="flex items-start justify-between border-b border-white/[0.06] pb-3 mb-4 shrink-0 font-sans">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-blue-500/10 border border-blue-500/30 text-cyan-400">
            <Sparkles size={14} className={isLoading ? 'animate-spin text-cyan-400' : 'text-blue-400'} />
          </div>
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-100 font-mono flex items-center gap-1.5">
              {title}
            </h3>
            <span className="text-[10px] text-slate-500 block font-normal tracking-wide mt-0.5">{subtitle}</span>
          </div>
        </div>
        {onRetry && (
          <button
            onClick={onRetry}
            className="text-slate-400 hover:text-white transition-all cursor-pointer p-1 rounded hover:bg-white/[0.04]"
          >
            <RefreshCw size={12} className={isLoading ? 'animate-spin' : ''} />
          </button>
        )}
      </div>

      {/* Content Area */}
      <div className="space-y-4">
        {isLoading ? (
          <div className="space-y-3 py-6 text-center flex flex-col items-center justify-center font-mono text-[10px] text-slate-500 tracking-wider">
            <Cpu size={24} className="text-cyan-400 animate-pulse mb-2" />
            <div className="w-24 h-1.5 bg-cyber-900 rounded-full overflow-hidden mb-1">
              <div className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 animate-loading-bar" />
            </div>
            <span>ANALYZING CLUSTER TELEMETRY...</span>
          </div>
        ) : parsedData ? (
          // Render Structured JSON Format
          <div className="space-y-4 font-sans text-xs">
            {/* Meta Row */}
            <div className="flex items-center justify-between border-b border-white/[0.04] pb-2 text-[10px] font-mono">
              <div className="flex items-center gap-1.5">
                <span className="text-slate-500">RISK VALUE:</span>
                {getSeverityBadge(parsedData.severity)}
              </div>
              {parsedData.confidence && (
                <div className="flex items-center gap-1.5">
                  <span className="text-slate-500">CONFIDENCE:</span>
                  <span className="text-cyan-400 font-bold font-mono tracking-wide">{parsedData.confidence}</span>
                </div>
              )}
            </div>

            {/* Summary */}
            {parsedData.summary && (
              <div className="bg-blue-950/20 border border-blue-500/10 rounded-xl p-3">
                <h4 className="text-[10px] font-mono font-black text-cyan-400 uppercase tracking-widest mb-1.5 flex items-center gap-1">
                  <CheckCircle size={10} /> Summary
                </h4>
                <p className="text-slate-200 text-xs leading-relaxed font-medium">{parsedData.summary}</p>
              </div>
            )}

            {/* Root Cause Accordion */}
            {parsedData.rootCause && (
              <div className="border border-white/[0.05] rounded-xl overflow-hidden">
                <button
                  onClick={() => setIsAccordionOpen(!isAccordionOpen)}
                  className="w-full flex items-center justify-between px-3.5 py-2.5 bg-white/[0.02] hover:bg-white/[0.04] font-mono text-[10px] text-slate-300 font-bold uppercase tracking-wider text-left transition-colors cursor-pointer"
                >
                  <span>Failure Vector & Root Cause</span>
                  <span>{isAccordionOpen ? '▼' : '▶'}</span>
                </button>
                {isAccordionOpen && (
                  <div className="p-3.5 border-t border-white/[0.05] bg-[#070b19]/60">
                    <p className="text-slate-300 text-xs leading-relaxed leading-relaxed font-medium">{parsedData.rootCause}</p>
                  </div>
                )}
              </div>
            )}

            {/* Recommendations */}
            {parsedData.recommendations && parsedData.recommendations.length > 0 && (
              <div>
                <h4 className="text-[10px] font-mono font-black text-cyan-400 uppercase tracking-widest mb-2 flex items-center gap-1">
                  <AlertTriangle size={10} /> Mitigation Playbook
                </h4>
                <div className="grid grid-cols-1 gap-2">
                  {parsedData.recommendations.map((rec: string, idx: number) => (
                    <div key={idx} className="flex items-start gap-2.5 p-2.5 bg-white/[0.02] border border-white/[0.04] rounded-lg">
                      <span className="text-cyan-400 font-bold font-mono shrink-0">0{idx + 1}</span>
                      <span className="text-slate-300 text-xs leading-relaxed font-medium">{rec}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Recommended Fix Code Block */}
            {parsedData.recommendedFix && (
              <div>
                <h4 className="text-[10px] font-mono font-black text-cyan-400 uppercase tracking-widest mb-2 flex items-center gap-1">
                  <Code size={12} /> Suggested Remediation
                </h4>
                <MarkdownRenderer content={`\`\`\`java\n${parsedData.recommendedFix}\n\`\`\``} />
              </div>
            )}

            {/* Impact */}
            {parsedData.impact && (
              <div className="border border-white/[0.05] bg-[#0c122b]/40 rounded-xl p-3">
                <span className="text-[9px] font-mono text-slate-500 uppercase block mb-1">Impact Score</span>
                <p className="text-slate-400 text-xs font-medium">{parsedData.impact}</p>
              </div>
            )}
          </div>
        ) : (
          // Degrading Markdown Format
          <MarkdownRenderer content={typedContent || content} />
        )}
      </div>
    </div>
  );
};

export default AICard;
