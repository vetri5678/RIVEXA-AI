/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // ── Primary Surface ──────────────────────────────────
        surface: {
          DEFAULT: '#07122a',
          base:    '#050816',
          secondary: '#0B1220',
          card:    '#111827',
          raised:  '#101827',
          overlay: '#0D1525',
          dim: '#07122a',
          bright: '#2f3952',
        },
        "surface-container-lowest": "#030d25",
        "surface-container-low": "#101b33",
        "surface-container": "#151f37",
        "surface-container-high": "#1f2942",
        "surface-container-highest": "#2a344e",
        "on-surface": "#d9e2ff",
        "on-surface-variant": "#bac9cc",
        "outline": "#849396",
        "outline-variant": "#3b494c",
        "surface-tint": "#00daf3",
        "primary": "#c3f5ff",
        "primary-container": "#00e5ff",
        "on-primary": "#00363d",
        "on-primary-container": "#00626e",
        // ── Brand Palette ────────────────────────────────────
        brand: {
          blue:   '#3B82F6',
          cyan:   '#38BDF8',
          purple: '#8B5CF6',
          violet: '#6D28D9',
          indigo: '#4F46E5',
          50:  '#EFF6FF',
          100: '#DBEAFE',
          400: '#60A5FA',
          500: '#3B82F6',
          600: '#2563EB',
          700: '#1D4ED8',
        },
        // ── Semantic ─────────────────────────────────────────
        success:  '#10B981',
        warning:  '#F59E0B',
        danger:   '#EF4444',
        info:     '#38BDF8',
        // ── Text ─────────────────────────────────────────────
        text: {
          primary:   '#F8FAFC',
          secondary: '#94A3B8',
          muted:     '#64748B',
          accent:    '#38BDF8',
        },
        // ── Border ───────────────────────────────────────────
        border: {
          DEFAULT: 'rgba(255,255,255,0.08)',
          subtle:  'rgba(255,255,255,0.04)',
          accent:  'rgba(59,130,246,0.3)',
          glow:    'rgba(56,189,248,0.4)',
        },
        // ── Risk Levels ──────────────────────────────────────
        risk: {
          critical: '#EF4444',
          high:     '#F97316',
          medium:   '#F59E0B',
          low:      '#10B981',
          none:     '#64748B',
        },
        // ── Legacy aliases (preserved for existing widgets) ──
        navy: {
          950: '#050816',
          900: '#0B1220',
          850: '#101827',
          800: '#111827',
          750: '#1a2540',
          700: '#1E2D45',
          600: '#2A3B55',
        },
        'neon-blue':  '#38BDF8',
        'neon-pink':  '#EC4899',
        'cyber-900':  '#0B1220',
        'cyber-850':  '#0D1525',
        'cyber-800':  '#111827',
      },

      fontFamily: {
        sans:    ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
        display: ['Inter', 'system-ui', 'sans-serif'],
        mono:    ['"JetBrains Mono"', '"Fira Code"', 'Consolas', 'monospace'],
      },

      fontSize: {
        'hero':    ['48px', { lineHeight: '1.1', letterSpacing: '-0.03em', fontWeight: '800' }],
        'title':   ['36px', { lineHeight: '1.15', letterSpacing: '-0.02em', fontWeight: '700' }],
        'section': ['28px', { lineHeight: '1.2', letterSpacing: '-0.015em', fontWeight: '600' }],
        'card':    ['20px', { lineHeight: '1.3', letterSpacing: '-0.01em', fontWeight: '600' }],
      },

      boxShadow: {
        'subtle':        '0 1px 3px 0 rgba(0,0,0,0.4)',
        'card':          '0 4px 20px -2px rgba(0,0,0,0.5), 0 2px 8px -1px rgba(0,0,0,0.3)',
        'card-hover':    '0 12px 40px -4px rgba(0,0,0,0.6), 0 4px 16px -2px rgba(0,0,0,0.4)',
        'glow-blue':     '0 0 30px rgba(59,130,246,0.25), 0 0 60px rgba(59,130,246,0.1)',
        'glow-cyan':     '0 0 30px rgba(56,189,248,0.25), 0 0 60px rgba(56,189,248,0.1)',
        'glow-purple':   '0 0 30px rgba(139,92,246,0.25)',
        'inner-glow':    'inset 0 1px 0 rgba(255,255,255,0.08)',
        'panel':         '0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.4)',
      },

      backgroundImage: {
        // Gradient mesh backgrounds
        'gradient-radial':      'radial-gradient(var(--tw-gradient-stops))',
        'gradient-mesh-blue':   'radial-gradient(ellipse at 20% 50%, rgba(59,130,246,0.15) 0%, transparent 60%), radial-gradient(ellipse at 80% 20%, rgba(139,92,246,0.1) 0%, transparent 60%)',
        'gradient-mesh-dark':   'radial-gradient(ellipse at 50% 0%, rgba(59,130,246,0.08) 0%, transparent 70%)',
        // Button gradients
        'btn-primary':          'linear-gradient(135deg, #3B82F6 0%, #2563EB 100%)',
        'btn-primary-hover':    'linear-gradient(135deg, #60A5FA 0%, #3B82F6 100%)',
        'btn-cyan':             'linear-gradient(135deg, #38BDF8 0%, #0EA5E9 100%)',
        'btn-purple':           'linear-gradient(135deg, #8B5CF6 0%, #7C3AED 100%)',
        // Card accents
        'accent-border':        'linear-gradient(90deg, #3B82F6, #38BDF8, #8B5CF6)',
        'sidebar-active':       'linear-gradient(135deg, rgba(59,130,246,0.15) 0%, rgba(56,189,248,0.05) 100%)',
      },

      backdropBlur: {
        xs: '2px',
        '3xl': '64px',
      },

      animation: {
        'aurora':        'aurora 12s ease infinite alternate',
        'float':         'float 6s ease-in-out infinite',
        'float-delayed': 'float 8s ease-in-out infinite 2s',
        'shimmer':       'shimmer 2s linear infinite',
        'pulse-glow':    'pulse-glow 2s ease-in-out infinite',
        'gradient-x':    'gradient-x 8s ease infinite',
        'spin-slow':     'spin 3s linear infinite',
        'fade-in-up':    'fade-in-up 0.4s ease-out',
        'scale-in':      'scale-in 0.2s ease-out',
        'slide-in-left': 'slide-in-left 0.3s ease-out',
        'slide-in-right':'slide-in-right 0.3s ease-out',
      },

      keyframes: {
        aurora: {
          '0%':   { backgroundPosition: '0% 50%' },
          '50%':  { backgroundPosition: '100% 50%' },
          '100%': { backgroundPosition: '0% 50%' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%':      { transform: 'translateY(-12px)' },
        },
        shimmer: {
          '0%':   { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'pulse-glow': {
          '0%, 100%': { opacity: '0.6', transform: 'scale(1)' },
          '50%':      { opacity: '1', transform: 'scale(1.05)' },
        },
        'gradient-x': {
          '0%, 100%': { backgroundSize: '200% 200%', backgroundPosition: 'left center' },
          '50%':      { backgroundSize: '200% 200%', backgroundPosition: 'right center' },
        },
        'fade-in-up': {
          '0%':   { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'scale-in': {
          '0%':   { opacity: '0', transform: 'scale(0.94)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        'slide-in-left': {
          '0%':   { opacity: '0', transform: 'translateX(-16px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        'slide-in-right': {
          '0%':   { opacity: '0', transform: 'translateX(16px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
      },

      transitionTimingFunction: {
        'spring':      'cubic-bezier(0.34, 1.56, 0.64, 1)',
        'smooth':      'cubic-bezier(0.4, 0, 0.2, 1)',
        'decelerate':  'cubic-bezier(0, 0, 0.2, 1)',
        'accelerate':  'cubic-bezier(0.4, 0, 1, 1)',
      },
    },
  },
  plugins: [],
}
