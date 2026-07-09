import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { motion } from 'framer-motion';
import { Database, Cpu, Hammer, FileText, CheckCircle } from 'lucide-react';

export const PredictionPipelineWidget: React.FC = () => {
  const stages = [
    { name: 'Repo Sync', icon: Database, color: 'text-neon-blue border-neon-blue/20' },
    { name: 'Extract', icon: Cpu, color: 'text-neon-purple border-neon-purple/20' },
    { name: 'Cleanse', icon: Hammer, color: 'text-neon-yellow border-neon-yellow/20' },
    { name: 'Model Engine', icon: Cpu, color: 'text-neon-orange border-neon-orange/20' },
    { name: 'Inference', icon: CheckCircle, color: 'text-neon-green border-neon-green/20' },
    { name: 'SHAP (XAI)', icon: FileText, color: 'text-neon-pink border-neon-pink/20' },
  ];

  return (
    <WidgetWrapper
      title="NEURAL PIPELINE LIFECYCLE"
      subtitle="Execution stages of the ML prediction stream"
      isLoading={false}
      isError={false}
    >
      <div className="flex flex-col md:flex-row items-center justify-between gap-2 py-4 font-mono text-[9px] w-full">
        {stages.map((stage, idx) => (
          <React.Fragment key={stage.name}>
            <motion.div
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
              className={`flex flex-col items-center p-2.5 border bg-cyber-950/40 rounded-lg text-center w-full md:w-20 ${stage.color} hover:border-neon-blue/50 transition-all duration-300`}
            >
              <stage.icon size={16} className="mb-1.5 animate-pulse-slow" />
              <span className="font-bold uppercase tracking-wider text-slate-300 block">
                {stage.name}
              </span>
            </motion.div>
            {idx < stages.length - 1 && (
              <span className="text-slate-700 font-bold rotate-90 md:rotate-0 my-1 md:my-0">
                →
              </span>
            )}
          </React.Fragment>
        ))}
      </div>
    </WidgetWrapper>
  );
};
export default PredictionPipelineWidget;
