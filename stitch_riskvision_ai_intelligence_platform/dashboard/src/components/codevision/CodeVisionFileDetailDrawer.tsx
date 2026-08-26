import React, { useState } from 'react';
import {
  X,
  FileCode,
  AlertTriangle,
  Zap,
  Code2,
} from 'lucide-react';
import { useCodeVisionFileDetail } from '../../hooks/useCodeVision';
import { SourceCodeViewer } from './SourceCodeViewer';
import type { CodeFinding } from '../../api/codeVision';

interface CodeVisionFileDetailDrawerProps {
  repositoryId?: string;
  fileId?: string;
  onClose: () => void;
}

export const CodeVisionFileDetailDrawer: React.FC<CodeVisionFileDetailDrawerProps> = ({
  repositoryId,
  fileId,
  onClose,
}) => {
  const { data: fileDetail, isLoading } = useCodeVisionFileDetail(repositoryId, fileId);
  const [selectedFinding, setSelectedFinding] = useState<CodeFinding | null>(null);
  const [showSourceViewer, setShowSourceViewer] = useState(true);

  React.useEffect(() => {
    if (fileId) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [fileId]);

  if (!fileId) return null;

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-rose-500/20 text-rose-300 border-rose-500/30';
      case 'HIGH':
        return 'bg-orange-500/20 text-orange-300 border-orange-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/30';
      default:
        return 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30';
    }
  };

  const findings = fileDetail?.findings || [];
  const activeFinding = selectedFinding || (findings.length > 0 ? findings[0] : null);

  return (
    <div className="fixed inset-0 z-50 overflow-hidden flex justify-end bg-black/70 backdrop-blur-sm transition-opacity duration-300">
      <div className="w-full max-w-full sm:max-w-2xl md:max-w-4xl bg-cyber-950 border-l border-glass-border h-full flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-glass-border bg-cyber-900/40 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2 bg-pink-500/15 border border-pink-500/30 rounded-xl text-pink-400 shrink-0">
              <FileCode size={20} />
            </div>
            <div className="min-w-0">
              <h2 className="text-sm font-mono font-bold text-slate-100 truncate">
                {fileDetail?.filePath || 'File Analysis Details'}
              </h2>
              <div className="flex items-center gap-2 mt-0.5 text-[11px] font-mono text-slate-400">
                <span>{fileDetail?.language}</span>
                <span>•</span>
                <span>{fileDetail?.linesOfCode} LOC</span>
                <span>•</span>
                <span className="text-cyan-400">{fileDetail?.analysisType} Analysis</span>
              </div>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-cyber-800 transition-all"
          >
            <X size={18} />
          </button>
        </div>

        {/* Content Body */}
        {isLoading ? (
          <div className="flex-1 flex flex-col items-center justify-center p-8 gap-3 font-mono text-xs text-slate-400">
            <div className="w-8 h-8 rounded-full border-2 border-pink-500 border-t-transparent animate-spin" />
            Loading file analysis & code findings...
          </div>
        ) : fileDetail ? (
          <div className="flex-1 overflow-y-auto p-6 space-y-6 font-sans">
            {/* Top Score Banner */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="p-4 rounded-xl bg-white/[0.02] border border-white/[0.06]">
                <span className="text-[10px] font-mono text-slate-500 block uppercase mb-1">
                  File Risk Score
                </span>
                <div className="flex items-baseline gap-2">
                  <span
                    className={`text-2xl font-bold font-mono ${
                      fileDetail.riskScore >= 75
                        ? 'text-rose-400'
                        : fileDetail.riskScore >= 50
                        ? 'text-orange-400'
                        : fileDetail.riskScore >= 25
                        ? 'text-amber-400'
                        : 'text-emerald-400'
                    }`}
                  >
                    {fileDetail.riskScore}
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">/ 100</span>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-white/[0.02] border border-white/[0.06]">
                <span className="text-[10px] font-mono text-slate-500 block uppercase mb-1">
                  Severity Level
                </span>
                <span
                  className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-mono font-bold border uppercase ${getSeverityBadge(
                    fileDetail.severity
                  )}`}
                >
                  {fileDetail.severity}
                </span>
              </div>

              <div className="p-4 rounded-xl bg-white/[0.02] border border-white/[0.06]">
                <span className="text-[10px] font-mono text-slate-500 block uppercase mb-1">
                  Analysis Confidence
                </span>
                <span className="text-xl font-bold font-mono text-cyan-400">
                  {fileDetail.confidence}%
                </span>
              </div>

              <div className="p-4 rounded-xl bg-white/[0.02] border border-white/[0.06]">
                <span className="text-[10px] font-mono text-slate-500 block uppercase mb-1">
                  Findings Count
                </span>
                <span className="text-xl font-bold font-mono text-pink-400">
                  {fileDetail.findingCount}
                </span>
              </div>
            </div>

            {/* Code Viewer Toggle */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-mono font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
                  <Code2 size={14} className="text-cyan-400" /> Source Code & Region Inspector
                </h3>
                <button
                  onClick={() => setShowSourceViewer(!showSourceViewer)}
                  className="text-[10px] font-mono text-cyan-400 hover:underline"
                >
                  {showSourceViewer ? 'Hide Viewer' : 'Show Viewer'}
                </button>
              </div>

              {showSourceViewer && (
                <SourceCodeViewer
                  content={
                    fileDetail.metrics?.code_sample ||
                    fileDetail.metrics?.source_code ||
                    `// File: ${fileDetail.filePath}\n// Language: ${fileDetail.language}\n// Risk Score: ${fileDetail.riskScore}/100 | Severity: ${fileDetail.severity}\n\n// Source file content unavailable or excluded.`
                  }
                  language={fileDetail.language}
                  activeStartLine={activeFinding?.startLine}
                  activeEndLine={activeFinding?.endLine}
                />
              )}
            </div>

            {/* Findings List */}
            <div className="space-y-4">
              <h3 className="text-xs font-mono font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
                <AlertTriangle size={14} className="text-rose-400" /> Code Findings & Risk Localization ({findings.length})
              </h3>

              {findings.length === 0 ? (
                <div className="p-6 text-center bg-white/[0.02] border border-white/[0.06] rounded-xl font-mono text-xs text-slate-500">
                  No critical code issues or static analysis warnings detected in this file.
                </div>
              ) : (
                <div className="space-y-3">
                  {findings.map((f, i) => {
                    const isSelected = activeFinding?.id === f.id;
                    return (
                      <div
                        key={f.id || i}
                        onClick={() => setSelectedFinding(f)}
                        className={`p-4 rounded-xl border transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-white/[0.04] border-cyan-500/40 shadow-lg shadow-cyan-500/5'
                            : 'bg-white/[0.015] border-white/[0.06] hover:border-white/[0.12]'
                        }`}
                      >
                        <div className="flex items-start justify-between gap-3 mb-2">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span
                              className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border uppercase ${getSeverityBadge(
                                f.severity
                              )}`}
                            >
                              {f.severity}
                            </span>
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-mono bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                              {f.findingType}
                            </span>
                            {f.symbolName && (
                              <span className="text-[11px] font-mono text-slate-300 font-bold bg-slate-800 px-2 py-0.5 rounded">
                                {f.symbolName}
                              </span>
                            )}
                          </div>

                          <div className="text-right text-[10px] font-mono text-slate-400 shrink-0">
                            {f.startLine ? (
                              <span className="text-rose-400 font-bold">
                                Lines {f.startLine}–{f.endLine || f.startLine}
                              </span>
                            ) : (
                              <span>Estimated Region</span>
                            )}
                          </div>
                        </div>

                        <h4 className="text-xs font-bold text-slate-100 mb-1.5">{f.title}</h4>
                        <p className="text-xs text-slate-300 leading-relaxed mb-3">{f.description}</p>

                        {/* Evidence */}
                        {f.evidence && (
                          <div className="p-2.5 rounded-lg bg-slate-950 border border-white/[0.06] font-mono text-[11px] text-slate-400 mb-3 overflow-x-auto">
                            <span className="text-[9px] text-slate-500 uppercase block mb-1 font-bold">Evidence</span>
                            <code>{f.evidence}</code>
                          </div>
                        )}

                        {/* Recommendation */}
                        <div className="p-3 rounded-xl bg-emerald-950/20 border border-emerald-500/20 text-xs text-emerald-300 flex items-start gap-2">
                          <Zap size={14} className="text-emerald-400 shrink-0 mt-0.5" />
                          <div>
                            <span className="font-bold block text-[10px] uppercase font-mono text-emerald-400 mb-0.5">
                              Recommended Action / Fix
                            </span>
                            <span>{f.recommendation}</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default CodeVisionFileDetailDrawer;
