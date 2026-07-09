import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { motion } from 'framer-motion';

export const ProjectLifecycleWidget: React.FC = () => {
  const steps = [
    { label: 'Idea', count: 12, color: 'bg-slate-800' },
    { label: 'Dev', count: 48, color: 'bg-neon-blue' },
    { label: 'Testing', count: 24, color: 'bg-neon-purple' },
    { label: 'Deploy', count: 18, color: 'bg-neon-green' },
    { label: 'Ops', count: 32, color: 'bg-neon-yellow' },
    { label: 'Inactive', count: 14, color: 'bg-neon-orange' },
    { label: 'Dead', count: 6, color: 'bg-neon-pink' },
  ];

  const total = steps.reduce((sum, s) => sum + s.count, 0);

  return (
    <WidgetWrapper
      title="PROJECT LIFECYCLE TRACKER"
      subtitle="Repository distributions across active & inactive phases"
      isLoading={false}
      isError={false}
    >
      <div className="space-y-4 py-2 font-mono text-[9px]">
        {/* Horizontal bar compilation */}
        <div className="flex h-3 w-full bg-cyber-950 border border-slate-800 rounded-full overflow-hidden">
          {steps.map((step) => {
            const pct = (step.count / total) * 100;
            return (
              <div
                key={step.label}
                className={step.color}
                style={{ width: `${pct}%` }}
                title={`${step.label}: ${step.count}`}
              />
            );
          })}
        </div>

        {/* Text descriptions grids */}
        <div className="grid grid-cols-4 md:grid-cols-7 gap-2">
          {steps.map((step) => (
            <div key={step.label} className="p-1.5 border border-slate-900 bg-cyber-950/20 text-center rounded">
              <span className="text-slate-500 uppercase block tracking-wider mb-0.5">{step.label}</span>
              <span className="text-slate-200 font-bold text-[10px]">{step.count}</span>
            </div>
          ))}
        </div>
      </div>
    </WidgetWrapper>
  );
};
export default ProjectLifecycleWidget;
