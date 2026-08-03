import React, { useState } from 'react';
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts';
import { BarChart2, GitCommit, Bug, GitPullRequest, Cpu, Heart } from 'lucide-react';
import GlassCard from '../common/GlassCard';

// Dummy structured analytics data for demo/fallback (since real metrics change over time)
const commitData = [
  { day: 'Mon', commits: 24, activeDevs: 4 },
  { day: 'Tue', commits: 35, activeDevs: 6 },
  { day: 'Wed', commits: 18, activeDevs: 3 },
  { day: 'Thu', commits: 45, activeDevs: 8 },
  { day: 'Fri', commits: 55, activeDevs: 9 },
  { day: 'Sat', commits: 12, activeDevs: 2 },
  { day: 'Sun', commits: 8, activeDevs: 1 },
];

const issueData = [
  { week: 'Wk 1', open: 12, closed: 8 },
  { week: 'Wk 2', open: 15, closed: 11 },
  { week: 'Wk 3', open: 22, closed: 14 },
  { week: 'Wk 4', open: 19, closed: 21 },
  { week: 'Wk 5', open: 28, closed: 18 },
  { week: 'Wk 6', open: 32, closed: 25 },
];

const prData = [
  { week: 'W1', merged: 8, failed: 1, open: 3 },
  { week: 'W2', merged: 12, failed: 2, open: 4 },
  { week: 'W3', merged: 15, failed: 0, open: 2 },
  { week: 'W4', merged: 9, failed: 3, open: 5 },
  { week: 'W5', merged: 18, failed: 1, open: 3 },
];

const buildSuccessData = [
  { name: 'Successful Builds', value: 85, color: '#00ff88' },
  { name: 'Failed Builds', value: 15, color: '#ff2d55' },
];

const healthTrendData = [
  { date: '06-15', healthScore: 82, failureProbability: 18 },
  { date: '06-20', healthScore: 78, failureProbability: 22 },
  { date: '06-25', healthScore: 75, failureProbability: 25 },
  { date: '06-30', healthScore: 68, failureProbability: 32 },
  { date: '07-05', healthScore: 55, failureProbability: 45 },
  { date: '07-09', healthScore: 48, failureProbability: 52 },
];

type ActiveChart = 'commits' | 'issues' | 'prs' | 'builds' | 'health';

export const RepositoryAnalyticsCharts: React.FC = () => {
  const [activeChart, setActiveChart] = useState<ActiveChart>('health');

  const renderActiveChart = () => {
    switch (activeChart) {
      case 'commits':
        return (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={commitData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="commitGlow" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#00d4ff" stopOpacity={0.4}/>
                    <stop offset="95%" stopColor="#00d4ff" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" opacity={0.3} />
                <XAxis dataKey="day" stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <YAxis stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0b1329', borderColor: 'rgba(0, 212, 255, 0.3)', borderRadius: 8 }}
                  labelStyle={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: 10 }}
                  itemStyle={{ color: '#f1f5f9', fontFamily: 'monospace', fontSize: 11 }}
                />
                <Area type="monotone" dataKey="commits" stroke="#00d4ff" strokeWidth={2} fillOpacity={1} fill="url(#commitGlow)" name="Commits Today" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        );
      case 'issues':
        return (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={issueData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" opacity={0.3} />
                <XAxis dataKey="week" stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <YAxis stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0b1329', borderColor: 'rgba(0, 212, 255, 0.3)', borderRadius: 8 }}
                  labelStyle={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: 10 }}
                  itemStyle={{ color: '#f1f5f9', fontFamily: 'monospace', fontSize: 11 }}
                />
                <Legend verticalAlign="top" height={36} wrapperStyle={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Line type="monotone" dataKey="open" stroke="#ff2d55" strokeWidth={2} dot={{ r: 3 }} name="Open Issues" />
                <Line type="monotone" dataKey="closed" stroke="#00ff88" strokeWidth={2} dot={{ r: 3 }} name="Closed Issues" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        );
      case 'prs':
        return (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={prData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" opacity={0.3} />
                <XAxis dataKey="week" stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <YAxis stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0b1329', borderColor: 'rgba(0, 212, 255, 0.3)', borderRadius: 8 }}
                  labelStyle={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: 10 }}
                  itemStyle={{ color: '#f1f5f9', fontFamily: 'monospace', fontSize: 11 }}
                />
                <Legend verticalAlign="top" height={36} wrapperStyle={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Bar dataKey="merged" stackId="a" fill="#00ff88" name="Merged PRs" />
                <Bar dataKey="open" stackId="a" fill="#00d4ff" name="Open PRs" />
                <Bar dataKey="failed" stackId="a" fill="#ff2d55" name="Failed PRs" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        );
      case 'builds':
        return (
          <div className="h-64 flex items-center justify-center">
            <div className="w-1/2 h-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={buildSuccessData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={80}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {buildSuccessData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0b1329', borderColor: 'rgba(0, 212, 255, 0.3)', borderRadius: 8 }}
                    itemStyle={{ color: '#f1f5f9', fontFamily: 'monospace', fontSize: 11 }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="w-1/2 space-y-3 font-mono text-xs">
              {buildSuccessData.map((item, index) => (
                <div key={index} className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }} />
                  <span className="text-slate-400">{item.name}:</span>
                  <span className="text-slate-100 font-bold">{item.value}%</span>
                </div>
              ))}
            </div>
          </div>
        );
      case 'health':
      default:
        return (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={healthTrendData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="healthGlow" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#00ff88" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#00ff88" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="probGlow" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ff2d55" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#ff2d55" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" opacity={0.3} />
                <XAxis dataKey="date" stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <YAxis stroke="#64748b" tickLine={false} style={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0b1329', borderColor: 'rgba(0, 212, 255, 0.3)', borderRadius: 8 }}
                  labelStyle={{ color: '#94a3b8', fontFamily: 'monospace', fontSize: 10 }}
                  itemStyle={{ color: '#f1f5f9', fontFamily: 'monospace', fontSize: 11 }}
                />
                <Legend verticalAlign="top" height={36} wrapperStyle={{ fontSize: 10, fontFamily: 'monospace' }} />
                <Area type="monotone" dataKey="healthScore" stroke="#00ff88" strokeWidth={2} fillOpacity={1} fill="url(#healthGlow)" name="Health Score" />
                <Area type="monotone" dataKey="failureProbability" stroke="#ff2d55" strokeWidth={2} fillOpacity={1} fill="url(#probGlow)" name="Failure Probability" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        );
    }
  };

  return (
    <GlassCard className="p-5 font-mono">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-neon-blue/10 border border-neon-blue/20 rounded-lg text-neon-blue">
            <BarChart2 size={16} />
          </div>
          <div>
            <h3 className="text-xs font-mono font-bold text-slate-100 uppercase tracking-widest">
              Repository Analytics Core
            </h3>
            <p className="text-[10px] text-slate-500 font-mono mt-0.5">
              Live telemetry aggregation, commit activity, build pipeline rates and health trends
            </p>
          </div>
        </div>

        {/* Tab switches */}
        <div className="flex flex-wrap gap-1">
          {[
            { id: 'health', label: 'Health Trend', icon: Heart },
            { id: 'commits', label: 'Commits', icon: GitCommit },
            { id: 'issues', label: 'Issues', icon: Bug },
            { id: 'prs', label: 'PRs', icon: GitPullRequest },
            { id: 'builds', label: 'Build Success', icon: Cpu },
          ].map(tab => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveChart(tab.id as ActiveChart)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-mono transition-all ${
                  activeChart === tab.id
                    ? 'bg-neon-blue/10 text-neon-blue border border-neon-blue/30 shadow-[0_0_15px_rgba(0,212,255,0.08)]'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-cyber-850/60'
                }`}
              >
                <Icon size={12} />
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {renderActiveChart()}
    </GlassCard>
  );
};

export default RepositoryAnalyticsCharts;
