import React, { useMemo } from 'react';
import {
  GitBranch, ShieldCheck, Eye, AlertTriangle, Skull, Archive, Brain
} from 'lucide-react';
import type { RepositoryStatistics, RepositorySummary } from '../../types/repository';

interface Props {
  stats: RepositoryStatistics | undefined;
  isLoading: boolean;
  repositories?: RepositorySummary[];
}

interface StatCard {
  label: string;
  value: string | number;
  icon: React.ElementType;
  color: string;
  glow: string;
  border: string;
  bg: string;
  suffix?: string;
}

const SkeletonCard: React.FC = () => (
  <div className="bg-cyber-900/60 border border-glass-border rounded-xl p-4 animate-pulse">
    <div className="flex items-center gap-3 mb-3">
      <div className="w-9 h-9 rounded-lg bg-cyber-800" />
      <div className="h-3 w-24 bg-cyber-800 rounded" />
    </div>
    <div className="h-8 w-16 bg-cyber-800 rounded mb-1" />
    <div className="h-2 w-20 bg-cyber-800/60 rounded" />
  </div>
);

export const RepositorySummaryCards: React.FC<Props> = ({ stats, isLoading, repositories = [] }) => {
  const computedStats = useMemo(() => {
    if (stats) {
      return stats;
    }
    if (repositories.length === 0) return undefined;

    let healthy = 0;
    let underObservation = 0;
    let highRisk = 0;
    let predictedDead = 0;
    let archived = 0;
    let withPredictions = 0;
    let pendingPrediction = 0;
    let sumHealth = 0;
    let sumFailProb = 0;

    repositories.forEach((r) => {
      if (r.status === 'ARCHIVED') {
        archived++;
        return;
      }

      const hasPred = r.predictionStatus === 'COMPLETED' || r.failureProbability != null || (r.healthScore != null && r.healthScore > 0);
      if (hasPred) {
        withPredictions++;
        const failProb = r.failureProbability ?? 0;
        const health = r.healthScore ?? Math.max(0, (1.0 - failProb) * 100);
        sumHealth += health;
        sumFailProb += failProb;

        const rl = (r.riskLevel || '').toUpperCase();
        if (rl === 'LOW' || (!rl && failProb < 0.25)) {
          healthy++;
        } else if (rl === 'MEDIUM' || (!rl && failProb < 0.50)) {
          underObservation++;
        } else if (rl === 'HIGH' || (!rl && failProb < 0.75)) {
          highRisk++;
        } else {
          predictedDead++;
        }
      } else {
        pendingPrediction++;
      }
    });

    const total = repositories.length;
    const active = total - archived;
    const coveragePct = total > 0 ? (withPredictions / total) * 100 : 0;
    const avgHealthScore = withPredictions > 0 ? sumHealth / withPredictions : 0;
    const avgFailureProbability = withPredictions > 0 ? sumFailProb / withPredictions : 0;

    return {
      total,
      healthy,
      underObservation,
      highRisk,
      predictedDead,
      archived,
      active,
      pendingPrediction,
      aiCoveragePercent: Math.round(coveragePct * 10) / 10,
      avgHealthScore: Math.round(avgHealthScore * 10) / 10,
      avgFailureProbability: Math.round(avgFailureProbability * 1000) / 1000,
      totalPredictionsRun: withPredictions,
      lastSyncTime: new Date().toISOString(),
    };
  }, [stats, repositories]);

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7 gap-4 mb-6">
        {Array.from({ length: 7 }).map((_, i) => <SkeletonCard key={i} />)}
      </div>
    );
  }

  const cards: StatCard[] = [
    {
      label: 'Total Repos',
      value: computedStats?.total ?? 0,
      icon: GitBranch,
      color: 'text-neon-blue',
      glow: 'shadow-[0_0_20px_rgba(0,212,255,0.1)]',
      border: 'border-neon-blue/20',
      bg: 'bg-neon-blue/5',
    },
    {
      label: 'Healthy',
      value: computedStats?.healthy ?? 0,
      icon: ShieldCheck,
      color: 'text-neon-green',
      glow: 'shadow-[0_0_20px_rgba(0,255,136,0.1)]',
      border: 'border-neon-green/20',
      bg: 'bg-neon-green/5',
    },
    {
      label: 'Observing',
      value: computedStats?.underObservation ?? 0,
      icon: Eye,
      color: 'text-neon-yellow',
      glow: 'shadow-[0_0_20px_rgba(245,158,11,0.1)]',
      border: 'border-yellow-500/20',
      bg: 'bg-yellow-500/5',
    },
    {
      label: 'High Risk',
      value: computedStats?.highRisk ?? 0,
      icon: AlertTriangle,
      color: 'text-neon-orange',
      glow: 'shadow-[0_0_20px_rgba(251,146,60,0.1)]',
      border: 'border-orange-500/20',
      bg: 'bg-orange-500/5',
    },
    {
      label: 'Pred. Dead',
      value: computedStats?.predictedDead ?? 0,
      icon: Skull,
      color: 'text-neon-pink',
      glow: 'shadow-[0_0_20px_rgba(255,45,85,0.15)]',
      border: 'border-neon-pink/20',
      bg: 'bg-neon-pink/5',
    },
    {
      label: 'Archived',
      value: computedStats?.archived ?? 0,
      icon: Archive,
      color: 'text-slate-400',
      glow: '',
      border: 'border-slate-700/40',
      bg: 'bg-slate-800/20',
    },
    {
      label: 'AI Coverage',
      value: `${computedStats?.aiCoveragePercent?.toFixed(1) ?? '0.0'}`,
      icon: Brain,
      color: 'text-neon-purple',
      glow: 'shadow-[0_0_20px_rgba(168,85,247,0.1)]',
      border: 'border-purple-500/20',
      bg: 'bg-purple-500/5',
      suffix: '%',
    },
  ];

  return (
    <div className="grid grid-cols-1 xs:grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7 gap-3 sm:gap-4 mb-6">
      {cards.map((card) => {
        const Icon = card.icon;
        return (
          <div
            key={card.label}
            className={`group relative overflow-hidden bg-cyber-900/60 border ${card.border} rounded-xl p-4 transition-all duration-300 hover:-translate-y-0.5 ${card.glow} hover:${card.border.replace('/20', '/40')}`}
          >
            {/* Ambient glow orb */}
            <div className={`absolute -top-4 -right-4 w-16 h-16 rounded-full blur-2xl opacity-20 ${card.bg} group-hover:opacity-40 transition-opacity`} />

            <div className="relative">
              <div className="flex items-center gap-2 mb-3">
                <div className={`p-1.5 rounded-lg ${card.bg} ${card.border} border`}>
                  <Icon size={14} className={card.color} />
                </div>
                <span className="text-[10px] font-mono text-slate-500 uppercase tracking-widest truncate">
                  {card.label}
                </span>
              </div>

              <div className={`text-2xl font-black font-mono ${card.color} leading-none mb-1`}>
                {card.value}{card.suffix ?? ''}
              </div>

              {/* Mini progress bar */}
              {computedStats && card.label !== 'AI Coverage' && typeof card.value === 'number' && computedStats.total > 0 && (
                <div className="h-0.5 bg-cyber-800 rounded-full mt-2 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-700 ${card.bg.replace('bg-', 'bg-').replace('/5', '/60')}`}
                    style={{ width: `${Math.min(100, ((card.value as number) / computedStats.total) * 100)}%` }}
                  />
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default RepositorySummaryCards;
