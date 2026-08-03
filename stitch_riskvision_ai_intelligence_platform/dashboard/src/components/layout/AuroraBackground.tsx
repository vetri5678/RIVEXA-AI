import React from 'react';

export const AuroraBackground: React.FC = () => {
  return (
    <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden bg-[#050816]">
      {/* Soft Aurora Glow Orbs */}
      <div 
        className="absolute -top-[20%] -left-[10%] w-[50vw] h-[50vw] rounded-full opacity-20 blur-[120px] pointer-events-none animate-float"
        style={{ background: 'radial-gradient(circle, #3B82F6 0%, #38BDF8 50%, transparent 80%)' }}
      />
      <div 
        className="absolute top-[30%] -right-[15%] w-[45vw] h-[45vw] rounded-full opacity-15 blur-[140px] pointer-events-none animate-float-delayed"
        style={{ background: 'radial-gradient(circle, #8B5CF6 0%, #6D28D9 50%, transparent 80%)' }}
      />
      <div 
        className="absolute -bottom-[20%] left-[20%] w-[55vw] h-[55vw] rounded-full opacity-15 blur-[150px] pointer-events-none animate-float"
        style={{ background: 'radial-gradient(circle, #0EA5E9 0%, #3B82F6 50%, transparent 80%)' }}
      />

      {/* Cybernetic Subtle Grid Overlay */}
      <div 
        className="absolute inset-0 opacity-[0.03] pointer-events-none"
        style={{
          backgroundImage: `linear-gradient(to right, #ffffff 1px, transparent 1px), linear-gradient(to bottom, #ffffff 1px, transparent 1px)`,
          backgroundSize: '48px 48px',
        }}
      />

      {/* Top Ambient Highlight */}
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-blue-500/30 to-transparent" />
    </div>
  );
};

export default AuroraBackground;
