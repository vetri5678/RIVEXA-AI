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
  const glowClasses = {
    none: '',
    blue: 'hover:shadow-[0_0_25px_rgba(0,212,255,0.15)] hover:border-neon-blue/30',
    green: 'hover:shadow-[0_0_25px_rgba(0,255,136,0.15)] hover:border-neon-green/30',
    red: 'hover:shadow-[0_0_25px_rgba(255,45,85,0.15)] hover:border-neon-pink/30',
    purple: 'hover:shadow-[0_0_25px_rgba(168,85,247,0.15)] hover:border-neon-purple/30',
  };

  return (
    <div
      onClick={onClick}
      className={clsx(
        isCritical ? 'glass-panel-critical' : 'glass-panel',
        glowClasses[glowColor],
        onClick && 'cursor-pointer transform hover:-translate-y-0.5 active:translate-y-0',
        className
      )}
    >
      {children}
    </div>
  );
};
export default GlassCard;
