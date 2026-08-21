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
  ChevronDown,
  TrendingUp,
  TrendingDown,
  Target,
  Calendar,
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

const formatDate = (val: any) => {
  if (!val) return 'Data unavailable';
  try {
    if (Array.isArray(val)) {
      const [y, m, d, h, min, s] = val;
      return new Date(y, m - 1, d, h || 0, min || 0, s || 0).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    }
    const parsed = new Date(val);
    if (isNaN(parsed.getTime())) return 'Data unavailable';
    return parsed.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  }
  catch { return 'Data unavailable'; }
};

interface FeatureItem {
  feature: string;
  impact: number;
  direction: string;
}

function parseJson<T>(raw: string | null | undefined): T[] {
  if (!raw) return [];
  try { return JSON.parse(raw) as T[]; }
  catch { return []; }
}

// ── Recommendation Types ─────────────────────────────────────────────────────

interface AiRecommendation {
  title: string;
  risk_detected: string;
  current_condition: string;
  recommended_action: string;
  why_it_matters: string;
  expected_impact: string;
  estimated_risk_reduction: string;
  implementation_effort: string;
  suggested_priority: string;
}

interface RichRecommendations {
  recommendations: AiRecommendation[];
  roadmap: {
    immediate: string[];
    short_term: string[];
    medium_term: string[];
  };
  projected_status: {
    projected_risk_score: number;
    projected_risk_level: string;
    potential_improvement: string;
  };
}

function parseRichRecommendations(raw: string | null | undefined): RichRecommendations | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && 'recommendations' in parsed) {
      return parsed as RichRecommendations;
    }
    return null;
  } catch {
    return null;
  }
}

function parseLegacyRecommendations(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map(String);
    return [];
  } catch {
    return [];
  }
}

const getPriorityStyle = (priority: string) => {
  const p = priority?.toLowerCase() || '';
  if (p.includes('p0') || p.includes('immediate') || p.includes('critical')) {
    return { badge: 'bg-rose-500/20 text-rose-300 border-rose-500/30', dot: 'bg-rose-400', label: 'P0 CRITICAL' };
  } else if (p.includes('p1') || p.includes('high')) {
    return { badge: 'bg-orange-500/20 text-orange-300 border-orange-500/30', dot: 'bg-orange-400', label: 'P1 HIGH' };
  } else if (p.includes('p2') || p.includes('medium')) {
    return { badge: 'bg-amber-500/20 text-amber-300 border-amber-500/30', dot: 'bg-amber-400', label: 'P2 MEDIUM' };
  }
  return { badge: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30', dot: 'bg-emerald-400', label: 'P3 LOW' };
};

const getEffortStyle = (effort: string) => {
  const e = effort?.toLowerCase() || '';
  if (e.includes('high')) return 'text-rose-400';
  if (e.includes('medium')) return 'text-amber-400';
  return 'text-emerald-400';
};

const getImpactStyle = (impact: string) => {
  const i = impact?.toLowerCase() || '';
  if (i.includes('critical')) return 'text-rose-400';
  if (i.includes('high')) return 'text-orange-400';
  if (i.includes('medium')) return 'text-amber-400';
  return 'text-emerald-400';
};

// ── Component ──────────────────────────────────────────────────────────────────

export const PredictionResult: React.FC = () => {
  const { predictionId } = useParams<{ predictionId: string }>();
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = usePredictionResult(predictionId ?? null);
  const pdfMutation   = useDownloadPdfReport();
  const excelMutation = useDownloadExcelReport();

  // Parse SHAP feature importance JSON with robust key mapping
  const featureJsonRaw = data?.featureImportanceJson || data?.feature_importance_json;
  const features = useMemo<FeatureItem[]>(() => {
    const rawList = parseJson<any>(featureJsonRaw);
    return rawList.map((item) => {
      const feat = item.display_name || item.feature_name || item.feature || 'Unknown Feature';
      const imp = typeof item.impact === 'number' ? item.impact : 0;
      const dir = String(item.direction || '').toLowerCase();
      return {
        feature: String(feat),
        impact: imp,
        direction: dir,
      };
    });
  }, [featureJsonRaw]);

  // Rich AI recommendations parsing
  const recsJsonRaw = data?.recommendationsJson || data?.recommendations_json;
  const richRecommendations = useMemo<RichRecommendations | null>(() => {
    return parseRichRecommendations(recsJsonRaw);
  }, [recsJsonRaw]);
  const legacyRecommendations = useMemo<string[]>(() => {
    if (richRecommendations) return [];
    return parseLegacyRecommendations(recsJsonRaw);
  }, [recsJsonRaw, richRecommendations]);
  const [expandedRec, setExpandedRec] = useState<number | null>(null);

  const maxImpact = useMemo(
    () => Math.max(...features.map((f) => Math.abs(f.impact)), 0.01),
    [features]
  );

  // ── Download helpers ──────────────────────────────────────────────────────

  const repoIdVal = data?.repositoryId || data?.repository_id;
  const predIdVal = data?.predictionId || data?.prediction_id;
  const targetId  = predIdVal || predictionId || repoIdVal;

  const handleDownloadPdf = async () => {
    if (!targetId) return;
    setDownloadError(null);
    try {
      const blob = await pdfMutation.mutateAsync(targetId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const sanitizeName = (data?.repositoryName || data?.repository_name || 'project').replace(/[^a-zA-Z0-9_-]/g, '_');
      a.download = `RIVEXA_${sanitizeName}_Risk_Report.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      console.error(err);
      if (err?.response?.status === 403) {
        setDownloadError("Forbidden: You do not have permission to download this report.");
      } else if (err?.response?.status === 404) {
        setDownloadError("Report not found on the server.");
      } else {
        setDownloadError(err?.response?.data?.message || err?.message || "Failed to download PDF report. Please try again.");
      }
    }
  };

  const handleDownloadExcel = async () => {
    if (!targetId) return;
    setDownloadError(null);
    try {
      const blob = await excelMutation.mutateAsync(targetId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const sanitizeName = (data?.repositoryName || data?.repository_name || 'project').replace(/[^a-zA-Z0-9_-]/g, '_');
      a.download = `RIVEXA_${sanitizeName}_Risk_Report.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      console.error(err);
      if (err?.response?.status === 403) {
        setDownloadError("Forbidden: You do not have permission to download this report.");
      } else if (err?.response?.status === 404) {
        setDownloadError("Report not found on the server.");
      } else {
        setDownloadError(err?.response?.data?.message || err?.message || "Failed to download Excel spreadsheet. Please try again.");
      }
    }
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

  // ── Result Field Resolution ───────────────────────────────────────────────

  const failureProbVal = data?.failureProbability ?? data?.failure_probability;
  const riskLevelVal   = data?.riskLevel || data?.risk_level;
  const riskScoreVal   = data?.riskScore ?? data?.risk_score;
  const healthScoreVal = data?.healthScore ?? data?.health_score;
  const confidenceVal  = data?.confidence;
  const modelVerVal    = data?.modelVersion || data?.model_version;
  const predStatusVal  = data?.predictionStatus || data?.prediction_status;
  const repoNameVal    = data?.repositoryName || data?.repository_name;
  const repoUrlVal     = data?.repositoryUrl || data?.repository_url;
  const gitProviderVal = data?.gitProvider || data?.git_provider;
  const triggeredByVal = data?.triggeredBy || data?.triggered_by;
  const createdAtVal   = data?.createdAt || data?.created_at;

  const risk = getRiskColor(riskLevelVal ?? 'LOW');
  const failPct = failureProbVal != null ? (failureProbVal * 100).toFixed(1) : null;
  const confidencePct = confidenceVal != null ? (confidenceVal * 100).toFixed(1) : null;
  const healthPct = healthScoreVal != null ? healthScoreVal.toFixed(1) : null;

  return (
    <DashboardLayout onSearchChange={setSearchTerm} searchValue={searchTerm} onQuickAction={() => {}}>

      {downloadError && (
        <div className="mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldAlert size={16} />
            <span>{downloadError}</span>
          </div>
          <button onClick={() => setDownloadError(null)} className="text-rose-400 hover:text-white font-bold text-xs uppercase px-2 py-1">
            Dismiss
          </button>
        </div>
      )}

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
                  {repoNameVal ?? 'Data unavailable'}
                </h1>
                <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase font-mono ${risk.bg} ${risk.text} border ${risk.border}`}>
                  {riskLevelVal ? `${riskLevelVal} RISK` : 'Data unavailable'}
                </span>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 uppercase">
                  {predStatusVal ?? 'COMPLETED'}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2">
                {repoUrlVal && (
                  <a href={repoUrlVal} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-cyan-400 transition-colors break-all max-w-full sm:max-w-md truncate">
                    <Globe size={11} className="shrink-0" /> <span className="truncate">{repoUrlVal}</span>
                  </a>
                )}
                {data.organization && (
                  <span className="flex items-center gap-1 text-[11px] text-slate-400">
                    <GitBranch size={11} className="shrink-0" /> {data.organization}
                  </span>
                )}
                {data.language && (
                  <span className="text-[11px] text-slate-400 font-mono">{data.language}</span>
                )}
                <span className="flex items-center gap-1 text-[11px] text-slate-500">
                  <Clock size={10} className="shrink-0" /> {formatDate(createdAtVal)}
                </span>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap items-center gap-2 shrink-0 w-full sm:w-auto mt-2 md:mt-0">
            <button
              onClick={() => navigate('/prediction/run')}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-300 text-xs font-bold hover:bg-slate-700 transition-all cursor-pointer"
            >
              <ArrowLeft size={13} /> <span className="hidden xs:inline">New Prediction</span>
            </button>
            <button
              onClick={handleDownloadPdf}
              disabled={pdfMutation.isPending}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-slate-300 text-xs font-bold hover:bg-slate-700 transition-all disabled:opacity-50 cursor-pointer"
            >
              {pdfMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : <FileText size={12} />}
              PDF
            </button>
            <button
              onClick={handleDownloadExcel}
              disabled={excelMutation.isPending}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-900/40 border border-emerald-500/30 text-emerald-400 text-xs font-bold hover:bg-emerald-900/60 transition-all disabled:opacity-50 cursor-pointer"
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
            value: failPct != null ? `${failPct}%` : 'Data unavailable',
            sub: 'ML Ensemble',
            color: (failureProbVal ?? 0) >= 0.5 ? 'text-rose-400' : 'text-amber-400',
            icon: <AlertTriangle size={14} className={(failureProbVal ?? 0) >= 0.5 ? 'text-rose-400' : 'text-amber-400'} />,
          },
          {
            label: 'Risk Score',
            value: riskScoreVal != null ? `${riskScoreVal}` : 'Data unavailable',
            sub: 'Out of 100',
            color: risk.text,
            icon: <ShieldAlert size={14} className={risk.text} />,
          },
          {
            label: 'AI Confidence',
            value: confidencePct != null ? `${confidencePct}%` : 'Data unavailable',
            sub: 'SHAP Verified',
            color: 'text-blue-400',
            icon: <Brain size={14} className="text-blue-400" />,
          },
          {
            label: 'Health Score',
            value: healthPct != null ? `${healthPct}` : 'Data unavailable',
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
              <span className={`font-bold text-sm ${risk.text}`}>{failPct != null ? `${failPct}% Failure Probability` : 'Data unavailable'}</span>
              <span>100%</span>
            </div>
            <div className="h-4 rounded-full bg-slate-800 overflow-hidden relative">
              <div
                className={`h-full rounded-full transition-all duration-1000 ease-out relative ${
                  (failureProbVal ?? 0) >= 0.75 ? 'bg-gradient-to-r from-red-600 to-rose-500' :
                  (failureProbVal ?? 0) >= 0.50 ? 'bg-gradient-to-r from-orange-500 to-amber-500' :
                  (failureProbVal ?? 0) >= 0.25 ? 'bg-gradient-to-r from-amber-500 to-yellow-400' :
                  'bg-gradient-to-r from-emerald-500 to-green-400'
                }`}
                style={{ width: `${(failureProbVal ?? 0) * 100}%` }}
              >
                <div className="absolute inset-0 bg-white/10 animate-pulse" />
              </div>
            </div>
          </div>

          {/* Risk zones legend */}
          <div className="grid grid-cols-4 gap-1.5">
            {[
              { label: 'LOW',      range: '0–25%',   color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20', active: (failureProbVal ?? 0) < 0.25 },
              { label: 'MEDIUM',   range: '25–50%',  color: 'text-amber-400',   bg: 'bg-amber-500/10 border-amber-500/20',     active: (failureProbVal ?? 0) >= 0.25 && (failureProbVal ?? 0) < 0.50 },
              { label: 'HIGH',     range: '50–75%',  color: 'text-orange-400',  bg: 'bg-orange-500/10 border-orange-500/20',   active: (failureProbVal ?? 0) >= 0.50 && (failureProbVal ?? 0) < 0.75 },
              { label: 'CRITICAL', range: '75–100%', color: 'text-rose-400',    bg: 'bg-rose-500/10 border-rose-500/20',       active: (failureProbVal ?? 0) >= 0.75 },
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
            <span className="text-[10px] font-mono text-cyan-400 font-bold">{modelVerVal ?? 'Data unavailable'}</span>
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
                const increases = f.direction.includes('increase') || f.direction.includes('increasing');
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

      {/* ── AI Recommendations ───────────────────────────────────────────────── */}
      {richRecommendations && richRecommendations.recommendations.length > 0 ? (
        <div className="mb-6 space-y-4">
          {/* Header row */}
          <div className="flex items-center gap-2">
            <Zap size={15} className="text-cyan-400" />
            <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">AI Recommendations</h2>
            <span className="ml-auto px-2 py-0.5 rounded-full text-[10px] font-bold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30">
              {richRecommendations.recommendations.length} actions identified
            </span>
          </div>

          {/* Recommendation Cards */}
          <div className="space-y-3">
            {richRecommendations.recommendations.map((rec, i) => {
              const pStyle = getPriorityStyle(rec.suggested_priority);
              const isExpanded = expandedRec === i;
              return (
                <div
                  key={i}
                  className={`glass-strong rounded-2xl border border-white/[0.08] overflow-hidden transition-all duration-200 ${
                    isExpanded ? 'border-cyan-500/20 shadow-lg shadow-cyan-500/5' : 'hover:border-white/[0.12]'
                  }`}
                >
                  {/* Card Header — always visible */}
                  <button
                    onClick={() => setExpandedRec(isExpanded ? null : i)}
                    className="w-full flex items-start gap-3 p-4 text-left"
                  >
                    <div className={`w-2 h-2 rounded-full mt-1.5 shrink-0 ${pStyle.dot}`} />
                    <div className="flex-1 min-w-0">
                      <div className="flex flex-wrap items-center gap-2 mb-1">
                        <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold border uppercase ${pStyle.badge}`}>
                          {pStyle.label}
                        </span>
                        <span className="text-xs font-semibold text-white truncate">{rec.title}</span>
                      </div>
                      <p className="text-[11px] text-slate-400">{rec.risk_detected}</p>
                    </div>
                    <ChevronDown
                      size={14}
                      className={`text-slate-500 shrink-0 mt-0.5 transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}
                    />
                  </button>

                  {/* Expanded detail */}
                  {isExpanded && (
                    <div className="px-4 pb-4 pt-0 border-t border-white/[0.05] space-y-3">
                      {rec.current_condition && (
                        <div className="mt-3 p-3 rounded-xl bg-slate-900/50 border border-white/[0.04]">
                          <span className="block text-[9px] font-bold uppercase tracking-wider text-slate-500 mb-1">Current Condition</span>
                          <p className="text-xs text-slate-300">{rec.current_condition}</p>
                        </div>
                      )}
                      <div className="p-3 rounded-xl bg-cyan-900/20 border border-cyan-500/15">
                        <span className="block text-[9px] font-bold uppercase tracking-wider text-cyan-500 mb-1">Recommended Action</span>
                        <p className="text-xs text-slate-200">{rec.recommended_action}</p>
                      </div>
                      {rec.why_it_matters && (
                        <div className="p-3 rounded-xl bg-slate-900/40 border border-white/[0.04]">
                          <span className="block text-[9px] font-bold uppercase tracking-wider text-slate-500 mb-1">Why It Matters</span>
                          <p className="text-xs text-slate-300 leading-relaxed">{rec.why_it_matters}</p>
                        </div>
                      )}
                      <div className="grid grid-cols-3 gap-2">
                        <div className="p-2 rounded-xl bg-slate-900/40 border border-white/[0.04] text-center">
                          <span className="block text-[9px] text-slate-500 mb-1">Impact</span>
                          <span className={`text-[11px] font-bold ${getImpactStyle(rec.expected_impact)}`}>{rec.expected_impact}</span>
                        </div>
                        <div className="p-2 rounded-xl bg-slate-900/40 border border-white/[0.04] text-center">
                          <span className="block text-[9px] text-slate-500 mb-1">Effort</span>
                          <span className={`text-[11px] font-bold ${getEffortStyle(rec.implementation_effort)}`}>{rec.implementation_effort}</span>
                        </div>
                        <div className="p-2 rounded-xl bg-slate-900/40 border border-white/[0.04] text-center">
                          <span className="block text-[9px] text-slate-500 mb-1">Risk Reduction</span>
                          <span className="text-[11px] font-bold text-emerald-400">{rec.estimated_risk_reduction}</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* Risk Reduction Roadmap */}
          {richRecommendations.roadmap && (
            <div className="glass-strong rounded-2xl border border-white/[0.08] p-5">
              <div className="flex items-center gap-2 mb-4">
                <Calendar size={14} className="text-purple-400" />
                <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider">Risk Reduction Roadmap</h3>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {[
                  { key: 'immediate', label: 'Immediate (0–7 days)', color: 'border-rose-500/30 bg-rose-500/5', headingColor: 'text-rose-400' },
                  { key: 'short_term', label: 'Short Term (1–4 weeks)', color: 'border-amber-500/30 bg-amber-500/5', headingColor: 'text-amber-400' },
                  { key: 'medium_term', label: 'Medium Term (1–3 months)', color: 'border-emerald-500/30 bg-emerald-500/5', headingColor: 'text-emerald-400' },
                ].map(({ key, label, color, headingColor }) => {
                  const items = richRecommendations.roadmap[key as keyof typeof richRecommendations.roadmap] || [];
                  return (
                    <div key={key} className={`p-3 rounded-xl border ${color}`}>
                      <span className={`block text-[10px] font-bold uppercase tracking-wider mb-2 ${headingColor}`}>{label}</span>
                      {items.length === 0 ? (
                        <p className="text-[11px] text-slate-500">No actions required</p>
                      ) : (
                        <ul className="space-y-1.5">
                          {items.map((item, idx) => (
                            <li key={idx} className="flex items-start gap-1.5">
                              <ChevronRight size={11} className={`${headingColor} shrink-0 mt-0.5`} />
                              <span className="text-[11px] text-slate-300 leading-relaxed">{item}</span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Projected Status After Improvements */}
          {richRecommendations.projected_status && richRecommendations.projected_status.projected_risk_score != null && (
            <div className="glass-strong rounded-2xl border border-emerald-500/20 bg-emerald-900/10 p-5">
              <div className="flex items-center gap-2 mb-4">
                <Target size={14} className="text-emerald-400" />
                <h3 className="text-xs font-bold text-emerald-300 uppercase tracking-wider">Projected Status After Improvements</h3>
              </div>
              <div className="grid grid-cols-3 gap-4">
                <div className="text-center">
                  <span className="block text-[9px] text-slate-500 uppercase tracking-wider mb-1">Current Risk Score</span>
                  <span className={`text-2xl font-extrabold font-mono ${risk.text}`}>{riskScoreVal ?? '—'}</span>
                </div>
                <div className="flex items-center justify-center">
                  <div className="flex items-center gap-2 text-emerald-400">
                    <TrendingDown size={20} />
                    <span className="text-sm font-bold">
                      {richRecommendations.projected_status.potential_improvement}
                    </span>
                  </div>
                </div>
                <div className="text-center">
                  <span className="block text-[9px] text-slate-500 uppercase tracking-wider mb-1">Projected Risk Score</span>
                  <span className="text-2xl font-extrabold font-mono text-emerald-400">
                    {richRecommendations.projected_status.projected_risk_score}
                  </span>
                  <span className={`block text-[10px] font-bold mt-0.5 ${
                    getRiskColor(richRecommendations.projected_status.projected_risk_level).text
                  }`}>
                    {richRecommendations.projected_status.projected_risk_level}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      ) : legacyRecommendations.length > 0 ? (
        // Legacy flat string list fallback
        <div className="glass-strong rounded-2xl border border-white/[0.08] p-5 mb-6">
          <div className="flex items-center gap-2 mb-4">
            <Zap size={15} className="text-cyan-400" />
            <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">AI Recommendations</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {legacyRecommendations.map((rec, i) => (
              <div key={i} className="flex items-start gap-2.5 p-3 bg-slate-900/40 border border-white/[0.05] rounded-xl">
                <ChevronRight size={13} className="text-cyan-400 shrink-0 mt-0.5" />
                <p className="text-xs text-slate-300 leading-relaxed">{rec}</p>
              </div>
            ))}
          </div>
        </div>
      ) : recsJsonRaw ? (
        // Data present but not parseable yet
        <div className="glass-strong rounded-2xl border border-white/[0.08] p-5 mb-6 flex items-center gap-3">
          <Loader2 size={14} className="text-cyan-400 animate-spin" />
          <p className="text-xs text-slate-400">Analyzing repository risks and generating AI recommendations…</p>
        </div>
      ) : null}

      {/* ── Repository Metadata ─────────────────────────────────────────────── */}
      <div className="glass-strong rounded-2xl border border-white/[0.08] p-5 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <GitBranch size={15} className="text-slate-400" />
          <h2 className="text-xs font-bold text-slate-200 uppercase tracking-wider">Repository Information</h2>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          {[
            { label: 'Repository ID',  value: repoIdVal ? String(repoIdVal) : 'Data unavailable', mono: true },
            { label: 'Prediction ID',  value: predIdVal ? String(predIdVal) : 'Data unavailable', mono: true },
            { label: 'Git Provider',   value: gitProviderVal ?? 'Data unavailable' },
            { label: 'Branch',         value: data.branch ?? 'Data unavailable', mono: true },
            { label: 'Visibility',     value: data.visibility ?? 'Data unavailable' },
            { label: 'Language',       value: data.language ?? 'Data unavailable' },
            { label: 'Triggered By',   value: triggeredByVal ?? 'Data unavailable' },
            { label: 'Predicted At',   value: formatDate(createdAtVal) },
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
