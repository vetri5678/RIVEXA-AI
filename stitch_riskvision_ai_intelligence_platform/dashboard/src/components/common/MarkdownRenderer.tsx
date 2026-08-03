import React from 'react';

interface MarkdownRendererProps {
  content: string;
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content }) => {
  if (!content) return null;

  // Split content by code blocks first
  const parts = content.split(/(```[\s\S]*?```)/g);

  return (
    <div className="space-y-2 text-xs font-sans text-slate-300 leading-relaxed">
      {parts.map((part, index) => {
        if (part.startsWith('```') && part.endsWith('```')) {
          // It's a code block
          const lines = part.slice(3, -3).trim().split('\n');
          let language = 'text';
          let codeLines = lines;
          if (lines.length > 0 && !lines[0].includes(' ') && lines[0].length < 15) {
            language = lines[0];
            codeLines = lines.slice(1);
          }
          const rawCode = codeLines.join('\n');

          return (
            <div key={index} className="my-3 border border-blue-500/20 bg-slate-950/80 rounded-xl overflow-hidden shadow-2xl font-mono">
              <div className="flex items-center justify-between px-3.5 py-1.5 bg-slate-900 border-b border-blue-500/10 text-[9px] uppercase tracking-wider font-bold text-cyan-400">
                <span>{language} Block</span>
                <button
                  onClick={() => navigator.clipboard.writeText(rawCode)}
                  className="hover:text-white transition-colors cursor-pointer"
                >
                  COPY
                </button>
              </div>
              <pre className="p-3 text-[10px] text-cyan-300 overflow-x-auto leading-relaxed max-h-64 no-scrollbar">
                <code>{rawCode}</code>
              </pre>
            </div>
          );
        }

        // Parse regular text line by line
        const lines = part.split('\n');
        return (
          <div key={index} className="space-y-1">
            {lines.map((line, lIdx) => {
              const trimmed = line.trim();
              if (!trimmed) return <div key={lIdx} className="h-2" />;

              // Headers
              if (trimmed.startsWith('#')) {
                const level = trimmed.match(/^#+/)?.[0].length || 1;
                const text = trimmed.replace(/^#+\s*/, '');
                const sizeClass = level === 1 ? 'text-lg font-extrabold text-white mt-4 mb-2' :
                                  level === 2 ? 'text-sm font-bold text-slate-100 mt-3 mb-1.5' :
                                  'text-xs font-bold text-slate-200 mt-2 mb-1';
                return (
                  <div key={lIdx} className={sizeClass}>
                    {parseInline(text)}
                  </div>
                );
              }

              // Bullet points
              if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
                const text = trimmed.slice(2);
                return (
                  <ul key={lIdx} className="list-disc pl-4 space-y-0.5 my-1">
                    <li className="text-slate-300 font-medium">
                      {parseInline(text)}
                    </li>
                  </ul>
                );
              }

              // Standard line
              return (
                <p key={lIdx} className="font-medium text-slate-300">
                  {parseInline(line)}
                </p>
              );
            })}
          </div>
        );
      })}
    </div>
  );
};

// Simple helper to parse bold (`**text**`) and inline code (`` `code` ``)
function parseInline(text: string) {
  const parts = text.split(/(\*\*.*?\*\*|`.*?`)/g);
  return parts.map((part, idx) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={idx} className="font-extrabold text-white">{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('`') && part.endsWith('`')) {
      return <code key={idx} className="px-1.5 py-0.5 rounded bg-blue-500/10 border border-blue-500/20 text-cyan-400 font-mono text-[10px]">{part.slice(1, -1)}</code>;
    }
    return part;
  });
}

export default MarkdownRenderer;
