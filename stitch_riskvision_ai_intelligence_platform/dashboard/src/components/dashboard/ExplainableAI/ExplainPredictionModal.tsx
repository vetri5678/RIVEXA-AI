import React from 'react';
import Badge from '../../common/Badge';
import { X, Sparkles, AlertTriangle, ShieldCheck } from 'lucide-react';
import { useHighRiskProjects } from '../../../hooks/useDashboard';

interface ExplainPredictionModalProps {
  projectId: string | null;
  onClose: () => void;
}

export const ExplainPredictionModal: React.FC<ExplainPredictionModalProps> = ({
  projectId,
  onClose,
}) => {
  const { data: highRisk } = useHighRiskProjects();

  if (!projectId) return null;

  const projectDetails = highRisk?.projects.find((p) => p.project_id === projectId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-cyber-950/80 backdrop-blur-md p-4 animate-count-up font-mono">
      <div className="w-full max-w-2xl bg-cyber-900 border border-slate-800 rounded-lg shadow-2xl flex flex-col justify-between overflow-hidden relative">
        <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-neon-blue via-neon-purple to-neon-pink" />

        {/* Header */}
        <div className="p-6 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2 text-neon-blue">
            <Sparkles size={18} className="animate-pulse" />
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-100">
              AI PREDICTION CORE EXPLAINER
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-100 p-1 bg-cyber-950 border border-slate-800 rounded transition-all duration-200"
          >
            <X size={14} />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-6 overflow-y-auto max-h-[70vh]">
          {projectDetails ? (
            <>
              {/* Project summary banner */}
              <div className="flex items-center justify-between bg-cyber-950/60 p-4 border border-slate-800 rounded-lg">
                <div>
                  <h3 className="text-sm font-bold text-slate-200">
                    {projectDetails.project_name}
                  </h3>
                  <p className="text-[10px] text-slate-500 mt-0.5">ID: {projectDetails.project_id}</p>
                </div>
                <div className="text-right">
                  <span className="text-2xl font-black text-neon-pink block">
                    {(projectDetails.failure_probability * 100).toFixed(1)}%
                  </span>
                  <span className="text-[8px] text-slate-500 uppercase tracking-widest block">
                    Abandonment Probability
                  </span>
                </div>
              </div>

              {/* Stats telemetry */}
              <div className="grid grid-cols-3 gap-3">
                <div className="p-3 border border-slate-800 rounded bg-cyber-950/20 text-center">
                  <span className="text-[9px] text-slate-500 uppercase block mb-0.5">Confidence</span>
                  <span className="text-xs font-bold text-neon-blue">
                    {(projectDetails.confidence_level * 100).toFixed(0)}%
                  </span>
                </div>
                <div className="p-3 border border-slate-800 rounded bg-cyber-950/20 text-center">
                  <span className="text-[9px] text-slate-500 uppercase block mb-0.5">Risk Index</span>
                  <span className="text-xs font-bold text-neon-orange">
                    {projectDetails.risk_score}/100
                  </span>
                </div>
                <div className="p-3 border border-slate-800 rounded bg-cyber-950/20 text-center">
                  <span className="text-[9px] text-slate-500 uppercase block mb-0.5">Last Scan</span>
                  <span className="text-xs font-bold text-slate-300">
                    {new Date(projectDetails.last_updated).toLocaleDateString()}
                  </span>
                </div>
              </div>

              {/* Critical Factors */}
              <div>
                <h4 className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-2">
                  SHAP Failure Drivertelemetry
                </h4>
                <div className="space-y-2">
                  {projectDetails.critical_factors.map((factor, index) => (
                    <div
                      key={index}
                      className="flex items-center justify-between p-2.5 border border-slate-800 bg-cyber-950/40 rounded"
                    >
                      <span className="text-xs text-slate-300">{factor.name}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-[11px] text-slate-400 font-bold">
                          {factor.impact.toFixed(4)}
                        </span>
                        <Badge
                          label={factor.direction === 'increases_risk' ? 'increases risk' : 'reduces risk'}
                          variant={factor.direction === 'increases_risk' ? 'critical' : 'success'}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Recommendation */}
              {projectDetails.recommendation && (
                <div className="p-4 bg-neon-blue/5 border border-neon-blue/20 rounded flex gap-3">
                  <div className="text-neon-blue mt-0.5 shrink-0">
                    <ShieldCheck size={16} />
                  </div>
                  <div>
                    <h4 className="text-[10px] font-bold text-neon-blue uppercase tracking-wider mb-1">
                      AI Mitigation Protocol
                    </h4>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      {projectDetails.recommendation}
                    </p>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-12 text-center text-slate-500">
              <AlertTriangle size={24} className="text-neon-yellow mb-2 animate-bounce" />
              <h4 className="text-xs font-bold text-slate-200 mb-0.5">
                Telemetry Pending
              </h4>
              <p className="text-[10px]">
                Initiate prediction protocol run to generate explainability matrices.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
export default ExplainPredictionModal;
