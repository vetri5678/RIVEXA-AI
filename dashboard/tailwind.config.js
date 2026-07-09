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
        // Primary AI Cyber palette
        cyber: {
          950: '#020817',
          900: '#050b1a',
          800: '#0a1628',
          700: '#0d1f3c',
          600: '#102850',
          500: '#1a3a6b',
        },
        neon: {
          blue:    '#00d4ff',
          green:   '#00ff88',
          purple:  '#a855f7',
          pink:    '#ff2d55',
          orange:  '#ff6b35',
          yellow:  '#f59e0b',
        },
        risk: {
          critical: '#ff2d55',
          high:     '#ff6b35',
          medium:   '#f59e0b',
          low:      '#00ff88',
        },
        glass: {
          DEFAULT: 'rgba(10, 22, 40, 0.8)',
          border:  'rgba(0, 212, 255, 0.15)',
          hover:   'rgba(0, 212, 255, 0.08)',
        },
      },
      fontFamily: {
        sans:  ['Inter', 'system-ui', 'sans-serif'],
        mono:  ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
        display: ['Inter', 'sans-serif'],
      },
      backgroundImage: {
        'cyber-grid': `
          linear-gradient(rgba(0,212,255,0.03) 1px, transparent 1px),
          linear-gradient(90deg, rgba(0,212,255,0.03) 1px, transparent 1px)
        `,
        'glow-blue':   'radial-gradient(circle at center, rgba(0,212,255,0.15) 0%, transparent 70%)',
        'glow-red':    'radial-gradient(circle at center, rgba(255,45,85,0.15) 0%, transparent 70%)',
        'glow-green':  'radial-gradient(circle at center, rgba(0,255,136,0.15) 0%, transparent 70%)',
      },
      boxShadow: {
        'neon-blue':   '0 0 20px rgba(0,212,255,0.3), 0 0 40px rgba(0,212,255,0.1)',
        'neon-green':  '0 0 20px rgba(0,255,136,0.3), 0 0 40px rgba(0,255,136,0.1)',
        'neon-red':    '0 0 20px rgba(255,45,85,0.3),  0 0 40px rgba(255,45,85,0.1)',
        'glass':       '0 8px 32px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.05)',
        'glass-hover': '0 12px 40px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.08)',
      },
      animation: {
        'pulse-slow':    'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'glow':          'glow 2s ease-in-out infinite alternate',
        'scan':          'scan 3s linear infinite',
        'float':         'float 6s ease-in-out infinite',
        'spin-slow':     'spin 8s linear infinite',
        'count-up':      'countUp 0.8s ease-out forwards',
      },
      keyframes: {
        glow: {
          '0%':   { boxShadow: '0 0 5px rgba(0,212,255,0.2)' },
          '100%': { boxShadow: '0 0 20px rgba(0,212,255,0.6), 0 0 40px rgba(0,212,255,0.2)' },
        },
        scan: {
          '0%':   { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(100vh)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%':      { transform: 'translateY(-8px)' },
        },
        countUp: {
          '0%':   { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      backdropBlur: {
        xs: '2px',
      },
    },
  },
  plugins: [],
}
