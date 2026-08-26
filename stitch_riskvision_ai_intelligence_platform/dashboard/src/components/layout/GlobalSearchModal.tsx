import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  X,
  Command,
  GitBranch,
  FileCode,
  AlertTriangle,
  Compass,
  ArrowRight,
  Loader2,
  ShieldAlert,
} from 'lucide-react';
import { useGlobalSearch } from '../../hooks/useGlobalSearch';
import type { SearchResultItem } from '../../api/search';

interface GlobalSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const GlobalSearchModal: React.FC<GlobalSearchModalProps> = ({ isOpen, onClose }) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const { data: searchData, isLoading, isError } = useGlobalSearch(query);

  const results = searchData?.results || [];

  // Focus input when modal opens
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
      setQuery('');
      setSelectedIndex(0);
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  // Reset selected index when results change
  useEffect(() => {
    setSelectedIndex(0);
  }, [results]);

  // Handle item navigation
  const handleSelectResult = (item: SearchResultItem) => {
    console.log('[GLOBAL-SEARCH] result selected:', item.type, item.title, 'navigating to:', item.url);
    onClose();
    if (item.url) {
      if (item.url.startsWith('/')) {
        window.location.hash = `#${item.url}`;
      } else {
        window.location.href = item.url;
      }
    }
  };

  // Keyboard navigation inside modal
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (results.length > 0 ? (prev + 1) % results.length : 0));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (results.length > 0 ? (prev - 1 + results.length) % results.length : 0));
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (results.length > 0 && selectedIndex >= 0 && selectedIndex < results.length) {
          handleSelectResult(results[selectedIndex]);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, results, selectedIndex]);

  if (!isOpen) return null;

  const getItemIcon = (type: string) => {
    switch (type) {
      case 'REPOSITORY':
        return <GitBranch size={16} className="text-cyan-400" />;
      case 'SOURCE_FILE':
        return <FileCode size={16} className="text-pink-400" />;
      case 'FINDING':
        return <ShieldAlert size={16} className="text-rose-400" />;
      default:
        return <Compass size={16} className="text-blue-400" />;
    }
  };

  const getRiskBadge = (riskLevel: string) => {
    switch (riskLevel?.toUpperCase()) {
      case 'CRITICAL':
        return 'bg-rose-500/20 text-rose-300 border-rose-500/30';
      case 'HIGH':
        return 'bg-orange-500/20 text-orange-300 border-orange-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/30';
      case 'LOW':
        return 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30';
      default:
        return 'bg-blue-500/20 text-blue-300 border-blue-500/30';
    }
  };

  // Group results
  const repositories = results.filter((r) => r.type === 'REPOSITORY');
  const files = results.filter((r) => r.type === 'SOURCE_FILE');
  const findings = results.filter((r) => r.type === 'FINDING');
  const pages = results.filter((r) => r.type === 'PAGE');

  let globalIndexCounter = 0;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 sm:pt-24 px-4 bg-black/75 backdrop-blur-md transition-all duration-300 animate-fade-in">
      {/* Modal Backdrop Click */}
      <div className="fixed inset-0" onClick={onClose} />

      {/* Command Palette Card */}
      <div className="relative w-full max-w-2xl bg-cyber-950/95 border border-glass-border rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[80vh] z-10">
        {/* Search Header */}
        <div className="relative flex items-center px-4 py-3.5 border-b border-glass-border bg-cyber-900/60">
          <Search size={18} className="text-cyan-400 shrink-0 mr-3" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search repositories, source code files, findings, pages..."
            className="w-full bg-transparent text-sm font-sans text-slate-100 placeholder-slate-500 focus:outline-none"
          />
          {query ? (
            <button
              onClick={() => setQuery('')}
              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06] transition-all mr-2"
            >
              <X size={14} />
            </button>
          ) : (
            <div className="flex items-center gap-1 text-[10px] font-mono text-slate-500 bg-white/[0.04] border border-white/[0.08] px-2 py-0.5 rounded mr-2">
              <Command size={10} /> K
            </div>
          )}
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/[0.06] transition-all"
          >
            <X size={16} />
          </button>
        </div>

        {/* Results Body */}
        <div className="flex-1 overflow-y-auto p-3 space-y-4 min-h-[220px]">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-400 font-mono text-xs gap-2">
              <Loader2 size={24} className="animate-spin text-cyan-400" />
              Searching RIVEXA Intelligence Platform...
            </div>
          ) : isError ? (
            <div className="flex flex-col items-center justify-center py-12 text-rose-400 font-mono text-xs gap-2">
              <AlertTriangle size={24} />
              Search service temporarily unavailable. Please try again.
            </div>
          ) : results.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500 font-mono text-xs gap-2">
              <Search size={24} className="text-slate-600" />
              No matching repositories, files, findings, or pages found for &quot;{query}&quot;
            </div>
          ) : (
            <>
              {/* Repositories Category */}
              {repositories.length > 0 && (
                <div className="space-y-1">
                  <div className="px-3 py-1 text-[10px] font-mono font-bold text-slate-400 uppercase tracking-wider">
                    Repositories ({repositories.length})
                  </div>
                  {repositories.map((item) => {
                    const index = globalIndexCounter++;
                    const isSelected = index === selectedIndex;
                    return (
                      <div
                        key={item.id}
                        onClick={() => handleSelectResult(item)}
                        onMouseEnter={() => setSelectedIndex(index)}
                        className={`group px-3 py-2.5 rounded-xl border flex items-center justify-between transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-white/[0.08] border-cyan-500/40 shadow-lg shadow-cyan-500/5'
                            : 'bg-white/[0.015] border-transparent hover:bg-white/[0.04]'
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="p-2 rounded-lg bg-cyan-500/10 border border-cyan-500/20 shrink-0">
                            {getItemIcon(item.type)}
                          </div>
                          <div className="min-w-0">
                            <h4 className="text-xs font-bold text-slate-100 truncate">{item.title}</h4>
                            <p className="text-[11px] font-mono text-slate-400 truncate mt-0.5">{item.subtitle}</p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border uppercase ${getRiskBadge(item.riskLevel)}`}>
                            {item.riskLevel}
                          </span>
                          <ArrowRight size={14} className={`transition-transform ${isSelected ? 'text-cyan-400 translate-x-0.5' : 'text-slate-600 opacity-0 group-hover:opacity-100'}`} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Source Files Category */}
              {files.length > 0 && (
                <div className="space-y-1">
                  <div className="px-3 py-1 text-[10px] font-mono font-bold text-slate-400 uppercase tracking-wider">
                    Code Vision Source Files ({files.length})
                  </div>
                  {files.map((item) => {
                    const index = globalIndexCounter++;
                    const isSelected = index === selectedIndex;
                    return (
                      <div
                        key={item.id}
                        onClick={() => handleSelectResult(item)}
                        onMouseEnter={() => setSelectedIndex(index)}
                        className={`group px-3 py-2.5 rounded-xl border flex items-center justify-between transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-white/[0.08] border-pink-500/40 shadow-lg shadow-pink-500/5'
                            : 'bg-white/[0.015] border-transparent hover:bg-white/[0.04]'
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="p-2 rounded-lg bg-pink-500/10 border border-pink-500/20 shrink-0">
                            {getItemIcon(item.type)}
                          </div>
                          <div className="min-w-0">
                            <h4 className="text-xs font-mono font-bold text-slate-100 truncate">{item.title}</h4>
                            <p className="text-[11px] font-mono text-slate-400 truncate mt-0.5">{item.subtitle}</p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border uppercase ${getRiskBadge(item.riskLevel)}`}>
                            {item.riskLevel}
                          </span>
                          <ArrowRight size={14} className={`transition-transform ${isSelected ? 'text-pink-400 translate-x-0.5' : 'text-slate-600 opacity-0 group-hover:opacity-100'}`} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Findings Category */}
              {findings.length > 0 && (
                <div className="space-y-1">
                  <div className="px-3 py-1 text-[10px] font-mono font-bold text-slate-400 uppercase tracking-wider">
                    Vulnerability Findings ({findings.length})
                  </div>
                  {findings.map((item) => {
                    const index = globalIndexCounter++;
                    const isSelected = index === selectedIndex;
                    return (
                      <div
                        key={item.id}
                        onClick={() => handleSelectResult(item)}
                        onMouseEnter={() => setSelectedIndex(index)}
                        className={`group px-3 py-2.5 rounded-xl border flex items-center justify-between transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-white/[0.08] border-rose-500/40 shadow-lg shadow-rose-500/5'
                            : 'bg-white/[0.015] border-transparent hover:bg-white/[0.04]'
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="p-2 rounded-lg bg-rose-500/10 border border-rose-500/20 shrink-0">
                            {getItemIcon(item.type)}
                          </div>
                          <div className="min-w-0">
                            <h4 className="text-xs font-bold text-slate-100 truncate">{item.title}</h4>
                            <p className="text-[11px] font-mono text-slate-400 truncate mt-0.5">{item.subtitle}</p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border uppercase ${getRiskBadge(item.riskLevel)}`}>
                            {item.riskLevel}
                          </span>
                          <ArrowRight size={14} className={`transition-transform ${isSelected ? 'text-rose-400 translate-x-0.5' : 'text-slate-600 opacity-0 group-hover:opacity-100'}`} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Pages Category */}
              {pages.length > 0 && (
                <div className="space-y-1">
                  <div className="px-3 py-1 text-[10px] font-mono font-bold text-slate-400 uppercase tracking-wider">
                    Application Pages & Navigation ({pages.length})
                  </div>
                  {pages.map((item) => {
                    const index = globalIndexCounter++;
                    const isSelected = index === selectedIndex;
                    return (
                      <div
                        key={item.id}
                        onClick={() => handleSelectResult(item)}
                        onMouseEnter={() => setSelectedIndex(index)}
                        className={`group px-3 py-2 rounded-xl border flex items-center justify-between transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-white/[0.08] border-blue-500/40 shadow-lg shadow-blue-500/5'
                            : 'bg-white/[0.015] border-transparent hover:bg-white/[0.04]'
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="p-2 rounded-lg bg-blue-500/10 border border-blue-500/20 shrink-0">
                            {getItemIcon(item.type)}
                          </div>
                          <div className="min-w-0">
                            <h4 className="text-xs font-bold text-slate-100 truncate">{item.title}</h4>
                            <p className="text-[11px] font-mono text-slate-400 truncate mt-0.5">{item.subtitle}</p>
                          </div>
                        </div>

                        <ArrowRight size={14} className={`transition-transform ${isSelected ? 'text-blue-400 translate-x-0.5' : 'text-slate-600 opacity-0 group-hover:opacity-100'}`} />
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>

        {/* Search Footer */}
        <div className="px-4 py-2.5 border-t border-glass-border bg-cyber-900/60 flex items-center justify-between text-[11px] font-mono text-slate-400">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-white/[0.08] rounded border border-white/[0.1]">↑↓</kbd> Navigate
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-white/[0.08] rounded border border-white/[0.1]">↵</kbd> Select
            </span>
            <span className="flex items-center gap-1">
              <kbd className="px-1.5 py-0.5 bg-white/[0.08] rounded border border-white/[0.1]">ESC</kbd> Close
            </span>
          </div>
          <span>RIVEXA Global Intelligence Search</span>
        </div>
      </div>
    </div>
  );
};

export default GlobalSearchModal;
