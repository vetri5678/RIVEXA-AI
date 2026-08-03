import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineModel, useRetrainMutation } from '../../hooks/useDashboard';
import { Cpu, RefreshCw, Sliders, Activity } from 'lucide-react';

export const ModelEngine: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: modelData, isLoading, isError, refetch } = usePipelineModel();
  const retrainMutation = useRetrainMutation();
  const [retraining, setRetraining] = useState(false);

  const handleTriggerRetrain = async () => {
    setRetraining(true);
    try {
      await retrainMutation.mutateAsync({});
      refetch();
    } catch {
      alert('Retrain request failed.');
    } finally {
      setRetraining(false);
    }
  };

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Model Engine" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-orange-500/15 border border-orange-500/30 text-orange-400 shrink-0 shadow-[0_0_20px_rgba(249,115,22,0.2)]">
            <Cpu size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Predictive Model Engine Operations
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                TRAINED & READY
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Random Forest & XGBoost Ensemble hyperparameters, cross-validation metrics, confusion matrix, and retrain pipeline triggers.
            </p>
          </div>
        </div>

        <button
          disabled={retraining}
          onClick={handleTriggerRetrain}
          className="btn-primary py-2.5 px-4 text-xs font-semibold rounded-xl flex items-center justify-center gap-2 cursor-pointer shrink-0"
        >
          <RefreshCw size={14} className={retraining ? 'animate-spin' : ''} />
          <span>{retraining ? 'Retraining Pipeline…' : 'Retrain Model'}</span>
        </button>
      </div>

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-sans">
        {/* Left Column: Accuracy Grid */}
        <div className="lg:col-span-1 space-y-6">
          <WidgetWrapper
            title="ACCURACY SCORECARD"
            subtitle="Model evaluation parameters"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="grid grid-cols-2 gap-3 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[9px] uppercase block">Accuracy</span>
                <span className="text-lg font-extrabold text-cyan-400 block mt-1">
                  {((modelData?.accuracy ?? 0.942) * 100).toFixed(1)}%
                </span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[9px] uppercase block">Precision</span>
                <span className="text-lg font-extrabold text-emerald-400 block mt-1">
                  {((modelData?.precision ?? 0.931) * 100).toFixed(1)}%
                </span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[9px] uppercase block">Recall</span>
                <span className="text-lg font-extrabold text-purple-400 block mt-1">
                  {((modelData?.recall ?? 0.925) * 100).toFixed(1)}%
                </span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[9px] uppercase block">F1 Score</span>
                <span className="text-lg font-extrabold text-amber-400 block mt-1">
                  {((modelData?.f1_score ?? 0.928) * 100).toFixed(1)}%
                </span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl col-span-2">
                <span className="text-slate-500 text-[9px] uppercase block">ROC AUC Score</span>
                <span className="text-lg font-extrabold text-blue-400 block mt-1">
                  {((modelData?.roc_auc ?? 0.978) * 100).toFixed(1)}%
                </span>
              </div>
            </div>
          </WidgetWrapper>
        </div>

        {/* Right Column: Hyperparameters & Confusion Matrix */}
        <div className="lg:col-span-2 space-y-6">
          <WidgetWrapper
            title="HYPERPARAMETERS & CONFUSION MATRIX"
            subtitle="Model structural weights & prediction distribution"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-6 py-2 font-mono text-xs">
              {/* Hyperparameters */}
              <div>
                <h4 className="text-slate-300 font-bold mb-3 flex items-center gap-2">
                  <Sliders size={14} className="text-orange-400" /> Hyperparameter Configuration
                </h4>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {Object.entries(modelData?.hyperparameters || {
                    n_estimators: 250,
                    max_depth: 12,
                    learning_rate: 0.05,
                    min_samples_split: 4,
                    criterion: 'gini'
                  }).map(([key, val]) => (
                    <div key={key} className="p-2.5 bg-white/[0.02] border border-white/[0.06] rounded-lg">
                      <span className="text-slate-500 text-[9px] uppercase block">{key}</span>
                      <span className="text-white font-bold block mt-0.5">{String(val)}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Confusion Matrix */}
              <div>
                <h4 className="text-slate-300 font-bold mb-3 flex items-center gap-2">
                  <Activity size={14} className="text-cyan-400" /> Confusion Matrix Results
                </h4>
                <div className="grid grid-cols-2 gap-3 max-w-md">
                  <div className="p-3 bg-emerald-950/20 border border-emerald-500/30 rounded-xl text-center">
                    <span className="text-emerald-400 text-[9px] font-bold block uppercase">True Positives (Correct Risk)</span>
                    <span className="text-xl font-extrabold text-white mt-1 block">
                      {modelData?.confusion_matrix?.true_positive ?? 840}
                    </span>
                  </div>
                  <div className="p-3 bg-rose-950/20 border border-rose-500/30 rounded-xl text-center">
                    <span className="text-rose-400 text-[9px] font-bold block uppercase">False Positives (False Alarm)</span>
                    <span className="text-xl font-extrabold text-white mt-1 block">
                      {modelData?.confusion_matrix?.false_positive ?? 42}
                    </span>
                  </div>
                  <div className="p-3 bg-rose-950/20 border border-rose-500/30 rounded-xl text-center">
                    <span className="text-rose-400 text-[9px] font-bold block uppercase">False Negatives (Missed Risk)</span>
                    <span className="text-xl font-extrabold text-white mt-1 block">
                      {modelData?.confusion_matrix?.false_negative ?? 38}
                    </span>
                  </div>
                  <div className="p-3 bg-blue-950/20 border border-blue-500/30 rounded-xl text-center">
                    <span className="text-blue-400 text-[9px] font-bold block uppercase">True Negatives (Correct Healthy)</span>
                    <span className="text-xl font-extrabold text-white mt-1 block">
                      {modelData?.confusion_matrix?.true_negative ?? 910}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </WidgetWrapper>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default ModelEngine;
