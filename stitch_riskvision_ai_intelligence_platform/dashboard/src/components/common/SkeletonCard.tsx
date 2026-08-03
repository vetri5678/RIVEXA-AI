import React from 'react';
import clsx from 'clsx';
import GlassCard from './GlassCard';

interface SkeletonCardProps {
  className?: string;
  rows?: number;
}

export const SkeletonCard: React.FC<SkeletonCardProps> = ({ className, rows = 3 }) => {
  return (
    <GlassCard className={clsx('p-5 animate-pulse-slow w-full', className)}>
      <div className="h-6 bg-cyber-800 rounded-md w-2/5 mb-6"></div>
      <div className="space-y-4">
        {Array.from({ length: rows }).map((_, idx) => (
          <div key={idx} className="flex gap-4 items-center">
            <div className="h-4 bg-cyber-800 rounded-md flex-1"></div>
            <div className="h-4 bg-cyber-800 rounded-md w-1/4"></div>
          </div>
        ))}
      </div>
    </GlassCard>
  );
};
export default SkeletonCard;
