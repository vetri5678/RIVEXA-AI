import React, { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import { usePredictionResult } from '../hooks/useDashboard';
import { useDownloadPdfReport, useDownloadExcelReport } from '../hooks/useRepository';
import {
  ShieldCheck,
  ShieldAlert,
  Brain,
  BarChart2,
  ArrowLeft,
  FileText,
  Download,
  RefreshCw,
  Zap,
  CheckCircle2,
  AlertTriangle,
  GitBranch,
  Globe,
  Clock,
  Loader2,
  ChevronRight,
  TrendingUp,
  TrendingDown,
} from 'lucide-react';

// ── Helpers ────────────────────────────────────────────────────────────────────

const getRiskColor = (level: string) => {
  switch (level) {
    case 'CRITICAL': return { text: 'text-rose-400',    bg: 'bg-rose-500/15',    border: 'border-rose-500/30',    glow: 'shadow-rose-500/20'   };
    case 'HIGH':     return { text: 'text-orange-400',  bg: 'bg-orange-500/15',  border: 'border-orange-500/30',  glow: 'shadow-orange-500/20' };
    case 'MEDIUM':   return { text: 'text-amber-400',   bg: 'bg-amber-500/15',   border: 'border-amber-500/30',   glow: 'shadow-amber-500/20'  };
    default:         return { text: 'text-emerald-400', bg: 'bg-emerald-500/15', border: 'border-emerald-500/30', glow: 'shadow-emerald-500/20' };
  }
};

const formatDate = (val: string | null | undefined) => {
  if (!val) return 'N/A';
  try { return new Date(val).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }); }
  catch { return val; }
};

interface FeatureItem {
  feature: string;
  impact: number;
  direction: 'increases_risk' | 'decreases_risk';
}

function parseJson<T>(raw: string | null | undefined): T[] {
  if (!raw) return [];
  try { return JSON.parse(raw) as T[]; }
  catch { return []; }
}

// ── Component ──────────────────────────────────────────────────────────────────

export const PredictionResult: React.FC = () => {
  const { predictionId } = useParams<{ predictionId: string }>();
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');

  const { data, isLoading, isError, error, refetch } = usePredictionResult(predictionId ?? null);
  const pdfMutation   = useDownloadPdfReport();
  const excelMutation = useDownloadExcelReport();

  // Parse SHAP feature importance JSON
  const features = useMemo<FeatureItem[]>(() => {
    return parseJson<FeatureItem>(data?.featureImportanceJson);
  }, [data?.featureImportanceJson]);

  const recommendations = useMemo<string[]>(() => {
    return parseJson<string>(data?.recommendationsJson);
  }, [data?.recommendationsJson]);

  const maxImpact = useMemo(
    () => Math.max(...features.map((f) => Math.abs(f.impact)), 0.01),
    [features]
  );

  // ── Download helpers ──────────────────────────────────────────────────────

  const handleDownloadPdf = async () => {
    if (!data?.repositoryId) return;
    try {
      const blob = await pdfMutation.mutateAsync(data.repositoryId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `prediction-${predictionId}-report.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch { /* silently ignore – mutation error handled by react-query */ }
  };

  const handleDownloadExcel = async () => {
    if (!data?.repositoryId) return;
    try {
      const blob = await excelMutation.mutateAsync(data.repositoryId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `prediction-${predictionId}-report.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } catch { /* silently ignore */ }
  };

  // ── Loading ───────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <DashboardLayout onSearchChange={setSearchTerm} searchValue={searchTerm} onQuickAction={() => {}}>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-5">
          <div className="p-5 rounded-2xl bg-cyan-500/10 border border-cyan-500/20">
            <Loader2 size={40} className="text-cyan-400 animate-spin" />
          </div>
          <div className="text-center">
            <p className="text-white font-bold text-lg mb-1">Loading Prediction Result</p>
            <p className="text-slate-400 text-sm">Fetching prediction data for ID: <span className="font-mono text-cyan-400 text-xs">{predictionId}</span></p>
          </div>
        </div>
      </DashboardLayout>
    );
  }

  // ── Error ─────────────────────────────────────────────────────────────────

  if (isError || !data) {
    const errMsg =
      (error as any)?.response?.data?.message ||
      (error as any)?.message ||
      'Could not load the prediction result. The prediction may not exist or the backend may be unavailable.';
    return (
      <DashboardLayout onSearchChange={setSearchTerm} searchValue={searchTerm} onQuickAction={() => {}}>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-5 max-w-lg mx-auto text-center">
          <div className="p-5 rounded-2xl bg-rose-500/10 border border-rose-500/20">
            <ShieldAlert size={40} className="text-rose-400" />
          </div>
          <div>
            <p className="text-white font-bold text-lg mb-2">Prediction Not Found</p>
            <p className="text-slate-400 text-sm leading-relaxed">{errMsg}</p>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => refetch()}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-bold hover:bg-slate-700 transition-all"
            >
              <RefreshCw size={13} /> Retry
            </button>
            <button
              onClick={() => navigate('/prediction/run')}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-xs font-bold hover:from-cyan-500 hover:to-blue-500 transition-all"
            >
              <Zap size={13} /> Run New Prediction
            </button>
          </div>
        </div>
      </DashboardLayout>
    );
  }

  // ── Result ────────────────────────────────────────────────────────────────

  const risk = getRiskColor(data.riskLevel ?? 'LOW');
  const failPct = ((data.failureProbability ?? 0) * 100).toFixed(1);
  const confidencePct = ((data.confidence ?? 0) * 100).toFixed(1);
  const healthPct = (data.healthScore ?? 0).toFixed(1);

  return (
    <DashboardLayout onSearchChange={setSearchTerm} searchValue={searchTerm} onQuickAction={() => {}}>

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <div className={`glass-strong rounded-2xl p-6 mb-6 border ${risk.border} relative overflow-hidden shadow-2xl`}>
        <div className={`absolute -top-16 -right-16 w-72 h-72 rounded-full ${risk.bg} blur-3xl opacity-50 pointer-events-none`} />
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />

        <div className="flex flex-col md:flex-row md:items-start justify-between gap-5 relative z-10">
          <div className="flex items-start gap-4">
            <div className={`p-3.5 rounded-2xl ${risk.bg} border ${risk.border} ${risk.text} shrink-0 shadow-lg ${risk.glow}`}>
              <ShieldCheck size={28} />
            </div>
            <div>
              <div className="flex items-center gap-3 flex-wrap mb-1">
                <h1 className="text-xl font-extrabold tracking-tight text-white font-sans">
                  {data.repositoryName ?? 'Repository'}
                </h1>
                <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase font-mono ${risk.bg} ${risk.text} border ${risk.border}`}>
                  {data.riskLevel ?? 'UNKNOWN'} RISK
                </span>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 uppercase">
                  {data.predictionStatus ?? 'COMPLETED'}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2">
                {data.repositoryUrl && (
                  <a href={data.repositoryUrl} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-cyan-400 transition-colors">
                    <Globe size={11} /> {data.repositoryUrl}
                  </a>
                )}
                {data.organization && (
                  <span className="flex items-center gap-1 text-[11px] text-slate-400">
                    <GitBranch size={11} /> {data.organization}
                  </span>
                )}
                {data.language && (
                  <span className="text-[11px] text-slate-400 font-mono">{data.language}</span>
                )}
                <span className="flex items-center gap-1 text-[11px] text-slate-500">
                  <Clock size={10} /> {formatDate(data.createdAt as any)}
                </span>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={() => navigate('/prediction/run')}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-300 text-xs font-bold hover:bg-slate-700 transition-all"
            >
              <ArrowLeft size={13} /> New Prediction
            </button>
            <button
              onClick={handleDownloadPdf}
              disabled={pdfMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-300 text-xs font-bold hover:bg-slate-700 transition-all disabled:opacity-50"
            >
              {pdfMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : <FileText size={12} />}
              PDF
            </button>
            <button
              onClick={handleDownloadExcel}
              disabled={excelMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-900/40 border border-emerald-500/30 text-emerald-400 text-xs font-bold hover:bg-emerald-900/60 transition-all disabled:opacity-50"
            >
              {excelMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : <Download size={12} />}
              Excel
            </button>
          </div>
        </div>
      </div>

      {/* ── KPI Row ─────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        {[
          {
            label: 'Failure Probability',
            value: `${failPct}%`,
            sub: 'ML Ensemble',
            color: data.failureProbability! >= 0.5 ? 'text-rose-400' : 'text-amber-400',
            icon: <AlertTriangle size={14} className={data.failureProbability! >= 0.5 ? 'text-rose-400' : 'text-amber-400'} />,
          },
          {
            label: 'Risk Score',
            value: `${data.riskScore ?? 0}`,
            sub: 'Out of 100',
            color: risk.text,
            icon: <ShieldAlert size={14} className={risk.text} />,
          },
          {
            label: 'AI Confidence',
            value: `${confidencePct}%`,
            sub: 'SHAP Verified',
            color: 'text-blue-400',
            icon: <Brain size={14} className="text-blue-400" />,
          },
          {
            label: 'Health Score',
            value: `${healthPct}`,
            sub: 'Project Vitality',
            color: 'text-emerald-400',
            icon: <CheckCircle2 size={14} className="text-emerald-400" />,
          },
        ].map((kpi) => (
          <div key={kpi.label} className="metric-card">
            <div className="flex items-center justify-between text-slate-400 mb-2">
              <span className="text-[10px] font-mono font-bold uppercase tracking-wider">{kpi.label}</span>
              {kpi.icon}
            </div>
            <span className={`text-3xl font-extrabold font-mono block mb-1 ${kpi.color}`}>{kpi.value}</span>
            <span className="text-[10px] text-slate-500">{kpi.sub}</span>
          </div>
        ))}
      </div>

      {/* ── Main Grid ───────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">

        {/* Probability Distribution */}
        <div className="glass-strong rounded-2xl border border-white/[0.08] p-5">
          <div className="flex items-center gap-2 mb-5">
            <BarChart2 size={15} className="text-blue-400" />
            <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">Probability Distribution</h2>
          </div>

          {/* Gauge bar */}
          <div className="mb-4">
            <div className="flex justify-between text-[10px] font-mono text-slate-400 mb-2">
              <span>0%</span>
              <span className={`font-bold text-sm ${risk.text}`}>{failPct}% Failure Probability</span>
              <span>100%</span>
            </div>
            <div className="h-4 rounded-full bg-slate-800 overflow-hidden relative">
              <div
                className={`h-full rounded-full transition-all duration-1000 ease-out relative ${
                  data.failureProbability! >= 0.75 ? 'bg-gradient-to-r from-red-600 to-rose-500' :
                  data.failureProbability! >= 0.50 ? 'bg-gradient-to-r from-orange-500 to-amber-500' :
                  data.failureProbability! >= 0.25 ? 'bg-gradient-to-r from-amber-500 to-yellow-400' :
                  'bg-gradient-to-r from-emerald-500 to-green-400'
                }`}
                style={{ width: `${(data.failureProbability ?? 0) * 100}%` }}
              >
                <div className="absolute inset-0 bg-white/10 animate-pulse" />
              </div>
            </div>
          </div>

          {/* Risk zones legend */}
          <div className="grid grid-cols-4 gap-1.5">
            {[
              { label: 'LOW',      range: '0–25%',   color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20', active: (data.failureProbability ?? 0) < 0.25 },
              { label: 'MEDIUM',   range: '25–50%',  color: 'text-amber-400',   bg: 'bg-amber-500/10 border-amber-500/20',     active: (data.failureProbability ?? 0) >= 0.25 && (data.failureProbability ?? 0) < 0.50 },
              { label: 'HIGH',     range: '50–75%',  color: 'text-orange-400',  bg: 'bg-orange-500/10 border-orange-500/20',   active: (data.failureProbability ?? 0) >= 0.50 && (data.failureProbability ?? 0) < 0.75 },
              { label: 'CRITICAL', range: '75–100%', color: 'text-rose-400',    bg: 'bg-rose-500/10 border-rose-500/20',       active: (data.failureProbability ?? 0) >= 0.75 },
            ].map((zone) => (
              <div key={zone.label} className={`p-2 rounded-xl border text-center transition-all ${zone.bg} ${zone.active ? 'opacity-100 shadow-sm' : 'opacity-40'}`}>
                <span className={`block text-[9px] font-bold uppercase ${zone.color}`}>{zone.label}</span>
                <span className="block text-[9px] text-slate-500 font-mono mt-0.5">{zone.range}</span>
              </div>
            ))}
          </div>

          {/* Model info */}
          <div className="mt-4 p-3 bg-slate-900/40 border border-white/[0.04] rounded-xl flex items-center justify-between">
            <span className="text-[10px] text-slate-400">Model Version</span>
            <span className="text-[10px] font-mono text-cyan-400 font-bold">{data.modelVersion ?? 'graveyard-ml-v1.0'}</span>
          </div>
        </div>

        {/* SHAP Feature Importance */}
        <div className="glass-strong rounded-2xl border border-white/[0.08] p-5">
          <div className="flex items-center gap-2 mb-5">
            <Brain size={15} className="text-purple-400" />
            <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">SHAP Feature Importance</h2>
            <span className="ml-auto text-[10px] text-slate-500 font-mono">Explainable AI</span>
          </div>

          {features.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-center text-slate-500 gap-2">
              <Brain size={24} className="text-slate-600" />
              <span className="text-xs">No SHAP data available for this prediction.</span>
            </div>
          ) : (
            <div className="space-y-3">
              {features.slice(0, 8).map((f, i) => {
                const pct = Math.abs(f.impact) / maxImpact * 100;
                const increases = f.direction === 'increases_risk';
                return (
                  <div key={i} className="space-y-1">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="flex items-center gap-1.5 text-slate-300 font-mono">
                        {increases
                          ? <TrendingUp size={11} className="text-rose-400 shrink-0" />
                          : <TrendingDown size={11} className="text-emerald-400 shrink-0" />}
                        {f.feature.replace(/_/g, ' ')}
                      </span>
                      <span className={`font-bold font-mono text-[10px] ${increases ? 'text-rose-400' : 'text-emerald-400'}`}>
                        {f.impact.toFixed(3)}
                      </span>
                    </div>
                    <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-700 ${increases ? 'bg-gradient-to-r from-rose-600 to-rose-400' : 'bg-gradient-to-r from-emerald-600 to-emerald-400'}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* ── Recommendations ─────────────────────────────────────────────────── */}
      {recommendations.length > 0 && (
        <div className="glass-strong rounded-2xl border border-white/[0.08] p-5 mb-6">
          <div className="flex items-center gap-2 mb-4">
            <Zap size={15} className="text-cyan-400" />
            <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">AI Recommendations</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {recommendations.map((rec, i) => (
              <div key={i} className="flex items-start gap-2.5 p-3 bg-slate-900/40 border border-white/[0.05] rounded-xl">
                <ChevronRight size={13} className="text-cyan-400 shrink-0 mt-0.5" />
                <p className="text-xs text-slate-300 leading-relaxed">{rec}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Repository Metadata ─────────────────────────────────────────────── */}
      <div className="glass-strong rounded-2xl border border-white/[0.08] p-5 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <GitBranch size={15} className="text-slate-400" />
          <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">Repository Information</h2>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          {[
            { label: 'Repository ID',  value: data.repositoryId?.toString() ?? 'N/A', mono: true },
            { label: 'Prediction ID',  value: data.predictionId?.toString() ?? 'N/A', mono: true },
            { label: 'Git Provider',   value: data.gitProvider ?? 'GitHub' },
            { label: 'Branch',         value: data.branch ?? 'main', mono: true },
            { label: 'Visibility',     value: data.visibility ?? 'N/A' },
            { label: 'Language',       value: data.language ?? 'N/A' },
            { label: 'Triggered By',   value: data.triggeredBy ?? 'MANUAL' },
            { label: 'Predicted At',   value: formatDate(data.createdAt as any) },
          ].map((item) => (
            <div key={item.label} className="p-3 bg-slate-900/40 border border-white/[0.05] rounded-xl">
              <span className="block text-[9px] font-bold uppercase tracking-wider text-slate-500 mb-1">{item.label}</span>
              <span className={`block text-[11px] text-slate-200 truncate ${item.mono ? 'font-mono' : ''}`}>{item.value}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Action Footer ───────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4 glass-strong rounded-2xl border border-white/[0.08] p-5">
        <div className="flex items-center gap-2 text-slate-400">
          <TrendingUp size={14} />
          <span className="text-xs">Run another prediction to track risk trends over time</span>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-300 text-xs font-bold hover:bg-slate-700 transition-all"
          >
            <ArrowLeft size={13} /> Dashboard
          </button>
          <button
            onClick={() => navigate('/prediction/run')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-xs font-bold hover:from-cyan-400 hover:to-blue-500 transition-all shadow-lg shadow-cyan-500/20"
          >
            <Zap size={13} /> New Prediction
          </button>
        </div>
      </div>

    </DashboardLayout>
  );
};

export default PredictionResult;
