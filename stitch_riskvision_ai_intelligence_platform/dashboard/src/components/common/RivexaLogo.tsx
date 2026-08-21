/**
 * RivexaLogo — Centralized RIVEXA brand logo component.
 *
 * This is the SINGLE SOURCE OF TRUTH for the RIVEXA logo across the entire
 * application. All pages, layouts, sidebars, auth flows use this component.
 *
 * To update the brand logo, update this file only.
 */

import React from 'react';

export type RivexaLogoVariant = 'full' | 'compact' | 'icon';

interface RivexaLogoProps {
  /** Display variant:
   *  - "full"    → Full logo image with wordmark + tagline (use at large sizes)
   *  - "compact" → Logo image only, constrained height (header/auth pages)
   *  - "icon"    → Small square crop of the eye symbol (collapsed sidebar)
   */
  variant?: RivexaLogoVariant;
  /** Height in px. Width scales proportionally. Default: 40 for full/compact, 32 for icon */
  size?: number;
  /** Additional CSS classes for the root wrapper */
  className?: string;
  /** Accessible label. Defaults to "RIVEXA" */
  alt?: string;
}

/**
 * RivexaLogo — Primary export.
 *
 * @example
 * // Full logo (e.g. landing page)
 * <RivexaLogo variant="full" size={56} />
 *
 * // Compact logo for auth pages / header
 * <RivexaLogo variant="compact" size={40} />
 *
 * // Icon-only for collapsed sidebar
 * <RivexaLogo variant="icon" size={32} />
 */
export const RivexaLogo: React.FC<RivexaLogoProps> = ({
  variant = 'compact',
  size,
  className = '',
  alt = 'RIVEXA',
}) => {
  if (variant === 'icon') {
    const iconSize = size ?? 32;
    return (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 120 120"
        width={iconSize}
        height={iconSize}
        className={className}
        role="img"
        aria-label={alt}
        style={{ flexShrink: 0 }}
      >
        <defs>
          <linearGradient id="rv-eye-grad-icon" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#00aaff" />
            <stop offset="100%" stopColor="#0044ff" />
          </linearGradient>
          <linearGradient id="rv-inner-grad-icon" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#00d4ff" />
            <stop offset="100%" stopColor="#0066ff" />
          </linearGradient>
          <filter id="glow-icon" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="2.5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <filter id="strong-glow-icon" x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Eye outer path - the teardrop/lens shape */}
        <path
          d="M 12 60 Q 60 8 108 60 Q 60 112 12 60 Z"
          fill="none"
          stroke="url(#rv-eye-grad-icon)"
          strokeWidth="3.5"
          filter="url(#glow-icon)"
        />

        {/* Scan line horizontals */}
        <line x1="18" y1="60" x2="32" y2="60" stroke="#0088ee" strokeWidth="1.5" opacity="0.8" />
        <line x1="88" y1="60" x2="102" y2="60" stroke="#0088ee" strokeWidth="1.5" opacity="0.8" />

        {/* Concentric tech rings */}
        <circle cx="60" cy="60" r="30" fill="none" stroke="url(#rv-eye-grad-icon)" strokeWidth="2" opacity="0.5" />
        <circle
          cx="60"
          cy="60"
          r="22"
          fill="none"
          stroke="url(#rv-eye-grad-icon)"
          strokeWidth="1.2"
          opacity="0.35"
          strokeDasharray="3 5"
        />

        {/* Tick marks on ring */}
        <line x1="60" y1="30" x2="60" y2="25" stroke="#0099dd" strokeWidth="1.5" opacity="0.7" />
        <line x1="60" y1="90" x2="60" y2="95" stroke="#0099dd" strokeWidth="1.5" opacity="0.7" />

        {/* R+V monogram */}
        {/* R */}
        <path
          d="M 46 49 L 46 71 M 46 49 L 57 49 Q 62 49 62 55 Q 62 61 57 61 L 46 61 M 57 61 L 63 71"
          fill="none"
          stroke="url(#rv-inner-grad-icon)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          filter="url(#glow-icon)"
        />
        {/* V */}
        <path
          d="M 65 49 L 72 71 L 79 49"
          fill="none"
          stroke="url(#rv-inner-grad-icon)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          filter="url(#glow-icon)"
        />

        {/* Pupil glow dot */}
        <circle cx="60" cy="60" r="4.5" fill="#00d4ff" filter="url(#strong-glow-icon)" />
        <circle cx="60" cy="60" r="2.5" fill="#ffffff" opacity="0.9" />
      </svg>
    );
  }

  // compact and full: show the full logo image with text next to it
  // Aspect ratio is 420/120 = 3.5. So width = height * 3.5
  const logoHeight = size ?? (variant === 'full' ? 56 : 40);
  const logoWidth = logoHeight * 3.5;

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 420 120"
      width={logoWidth}
      height={logoHeight}
      className={className}
      role="img"
      aria-label={alt}
      style={{
        flexShrink: 0,
        filter: 'drop-shadow(0 0 8px rgba(0, 150, 255, 0.2))',
        userSelect: 'none',
      }}
    >
      <defs>
        <linearGradient id="rv-eye-grad-logo" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00aaff" />
          <stop offset="100%" stopColor="#0044ff" />
        </linearGradient>
        <linearGradient id="rv-inner-grad-logo" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00d4ff" />
          <stop offset="100%" stopColor="#0066ff" />
        </linearGradient>
        <linearGradient id="rv-text-grad-logo" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="60%" stopColor="#e2f1ff" />
          <stop offset="100%" stopColor="#00d4ff" />
        </linearGradient>
        <filter id="glow-logo" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="2.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id="strong-glow-logo" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id="text-glow-logo" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* Left: Icon (0,0 to 120,120) */}
      <g transform="translate(10, 0)">
        {/* Eye outer path */}
        <path
          d="M 12 60 Q 60 8 108 60 Q 60 112 12 60 Z"
          fill="none"
          stroke="url(#rv-eye-grad-logo)"
          strokeWidth="3.5"
          filter="url(#glow-logo)"
        />

        {/* Scan line horizontals */}
        <line x1="18" y1="60" x2="32" y2="60" stroke="#0088ee" strokeWidth="1.5" opacity="0.8" />
        <line x1="88" y1="60" x2="102" y2="60" stroke="#0088ee" strokeWidth="1.5" opacity="0.8" />

        {/* Concentric tech rings */}
        <circle cx="60" cy="60" r="30" fill="none" stroke="url(#rv-eye-grad-logo)" strokeWidth="2" opacity="0.5" />
        <circle
          cx="60"
          cy="60"
          r="22"
          fill="none"
          stroke="url(#rv-eye-grad-logo)"
          strokeWidth="1.2"
          opacity="0.35"
          strokeDasharray="3 5"
        />

        {/* Tick marks on ring */}
        <line x1="60" y1="30" x2="60" y2="25" stroke="#0099dd" strokeWidth="1.5" opacity="0.7" />
        <line x1="60" y1="90" x2="60" y2="95" stroke="#0099dd" strokeWidth="1.5" opacity="0.7" />

        {/* R+V monogram */}
        <path
          d="M 46 49 L 46 71 M 46 49 L 57 49 Q 62 49 62 55 Q 62 61 57 61 L 46 61 M 57 61 L 63 71"
          fill="none"
          stroke="url(#rv-inner-grad-logo)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          filter="url(#glow-logo)"
        />
        <path
          d="M 65 49 L 72 71 L 79 49"
          fill="none"
          stroke="url(#rv-inner-grad-logo)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          filter="url(#glow-logo)"
        />

        {/* Pupil glow dot */}
        <circle cx="60" cy="60" r="4.5" fill="#00d4ff" filter="url(#strong-glow-logo)" />
        <circle cx="60" cy="60" r="2.5" fill="#ffffff" opacity="0.9" />
      </g>

      {/* Right: Wordmark RIVEXA */}
      <text
        x="145"
        y="70"
        fontFamily="'Geist', 'Inter', 'Segoe UI', sans-serif"
        fontWeight="900"
        fontSize="38"
        fill="url(#rv-text-grad-logo)"
        letterSpacing="0.18em"
        filter="url(#text-glow-logo)"
      >
        RIVEXA
      </text>
      <text
        x="147"
        y="92"
        fontFamily="'Geist', 'Inter', 'Segoe UI', sans-serif"
        fontWeight="600"
        fontSize="11"
        fill="#00d4ff"
        letterSpacing="0.48em"
        opacity="0.8"
      >
        {variant === 'full' ? 'PREDICTIVE RISK INTEL' : 'RISK INTEL'}
      </text>
    </svg>
  );
};

export default RivexaLogo;
