import React, { useEffect, useRef } from 'react';

interface SourceCodeViewerProps {
  content: string;
  language?: string;
  activeStartLine?: number;
  activeEndLine?: number;
}

export const SourceCodeViewer: React.FC<SourceCodeViewerProps> = ({
  content,
  language: _language = 'text',
  activeStartLine,
  activeEndLine,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const activeLineRef = useRef<HTMLDivElement>(null);

  const lines = content ? content.split(/\r?\n/) : [];

  useEffect(() => {
    if (activeLineRef.current && containerRef.current) {
      activeLineRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [activeStartLine, activeEndLine]);

  if (!content) {
    return (
      <div className="p-8 text-center text-slate-500 font-mono text-xs bg-slate-950/80 rounded-xl border border-white/[0.06]">
        Source content unavailable for this file.
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="bg-slate-950 border border-white/[0.08] rounded-xl overflow-x-auto font-mono text-xs shadow-inner max-h-[500px]"
    >
      <div className="min-w-full inline-block py-2">
        {lines.map((lineText, idx) => {
          const lineNum = idx + 1;
          const isHighlighted =
            activeStartLine !== undefined &&
            activeEndLine !== undefined &&
            lineNum >= activeStartLine &&
            lineNum <= activeEndLine;

          const isFirstHighlightedLine = isHighlighted && lineNum === activeStartLine;

          return (
            <div
              key={idx}
              ref={isFirstHighlightedLine ? activeLineRef : undefined}
              className={`flex items-start px-3 py-0.5 leading-relaxed transition-colors ${
                isHighlighted
                  ? 'bg-rose-500/20 border-l-4 border-rose-500 text-rose-200'
                  : 'hover:bg-white/[0.03] text-slate-300 border-l-4 border-transparent'
              }`}
            >
              <span className="w-10 shrink-0 text-slate-600 select-none text-right pr-4 text-[10px]">
                {lineNum}
              </span>
              <pre className="flex-1 whitespace-pre font-mono text-[11px] overflow-x-auto text-slate-200">
                {lineText || ' '}
              </pre>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default SourceCodeViewer;
