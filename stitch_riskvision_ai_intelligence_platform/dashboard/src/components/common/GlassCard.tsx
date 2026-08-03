import React from 'react';
import clsx from 'clsx';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  isCritical?: boolean;
  glowColor?: 'blue' | 'green' | 'red' | 'purple' | 'none';
  onClick?: () => void;
}

export const GlassCard: React.FC<GlassCardProps> = ({
  children,
  className,
  isCritical = false,
  glowColor = 'none',
  onClick,
}) => {
  const accentClasses = {
    none: 'hover:border-white/[0.15]',
    blue: 'hover:border-blue-500/40 hover:shadow-[0_0_25px_rgba(59,130,246,0.15)]',
    green: 'hover:border-emerald-500/40 hover:shadow-[0_0_25px_rgba(16,185,129,0.15)]',
    red: 'hover:border-red-500/40 hover:shadow-[0_0_25px_rgba(239,68,68,0.15)]',
    purple: 'hover:border-purple-500/40 hover:shadow-[0_0_25px_rgba(139,92,246,0.15)]',
  };

  return (
    <div
      onClick={onClick}
      className={clsx(
        'glass rounded-2xl p-5 shadow-card transition-all duration-300 relative overflow-hidden border border-white/[0.08]',
        isCritical ? 'border-red-500/40 bg-red-500/[0.03] hover:border-red-500/60' : accentClasses[glowColor],
        onClick && 'cursor-pointer hover:-translate-y-1 active:translate-y-0',
        className
      )}
    >
      {/* Top subtle highlight */}
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-white/10 to-transparent" />
      {children}
    </div>
  );
};

export default GlassCard;
