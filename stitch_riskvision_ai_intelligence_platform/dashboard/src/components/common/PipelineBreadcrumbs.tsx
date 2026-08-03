import React from 'react';
import { ChevronRight, Home, Layers } from 'lucide-react';
import { Link } from 'react-router-dom';

interface BreadcrumbsProps {
  currentStage: string;
}

export const PipelineBreadcrumbs: React.FC<BreadcrumbsProps> = ({ currentStage }) => {
  return (
    <nav className="flex items-center gap-2 text-xs font-mono text-slate-400 mb-6 bg-white/[0.02] border border-white/[0.06] px-4 py-2.5 rounded-xl">
      <Link
        to="/dashboard"
        className="flex items-center gap-1.5 hover:text-cyan-400 transition-colors"
      >
        <Home size={13} className="text-slate-400" />
        <span>Dashboard</span>
      </Link>
      <ChevronRight size={12} className="text-slate-600" />
      <span className="flex items-center gap-1 text-slate-400">
        <Layers size={13} className="text-blue-400" />
        <span>Neural Pipeline</span>
      </span>
      <ChevronRight size={12} className="text-slate-600" />
      <span className="font-bold text-cyan-400 uppercase tracking-wider">{currentStage}</span>
    </nav>
  );
};

export default PipelineBreadcrumbs;
