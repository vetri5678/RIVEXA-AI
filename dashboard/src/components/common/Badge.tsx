import React from 'react';
import clsx from 'clsx';

interface BadgeProps {
  label: string;
  variant?: 'low' | 'medium' | 'high' | 'critical' | 'info' | 'success' | 'warning' | 'error';
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({ label, variant = 'info', className }) => {
  const styles = {
    low: 'bg-neon-green/10 text-neon-green border-neon-green/20 glow-text-green',
    medium: 'bg-neon-yellow/10 text-neon-yellow border-neon-yellow/20 glow-text-yellow',
    high: 'bg-neon-orange/10 text-neon-orange border-neon-orange/20 glow-text-orange',
    critical: 'bg-neon-pink/10 text-neon-pink border-neon-pink/20 glow-text-red border-neon-pink/30 animate-pulse',
    success: 'bg-neon-green/10 text-neon-green border-neon-green/20',
    info: 'bg-neon-blue/10 text-neon-blue border-neon-blue/20 glow-text-blue',
    warning: 'bg-neon-yellow/10 text-neon-yellow border-neon-yellow/20',
    error: 'bg-neon-pink/10 text-neon-pink border-neon-pink/20',
  };

  const cleanVariant = (label || '').toLowerCase() as keyof typeof styles;
  const activeStyle = styles[cleanVariant] || styles[variant] || styles.info;

  return (
    <span
      className={clsx(
        'inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-bold border uppercase tracking-wider',
        activeStyle,
        className
      )}
    >
      {label}
    </span>
  );
};
export default Badge;
