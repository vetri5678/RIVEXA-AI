/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./stitch_riskvision_ai_intelligence_platform/index.html",
    "./stitch_riskvision_ai_intelligence_platform/dashboard/index.html",
    "./stitch_riskvision_ai_intelligence_platform/dashboard/src/**/*.{js,ts,jsx,tsx}",
    "./stitch_riskvision_ai_intelligence_platform/js/**/*.js",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        navy: {
          950: '#0B1120',
          900: '#0F172A',
          850: '#172033',
          800: '#1E293B',
          750: '#27354A',
          700: '#334155',
          600: '#475569',
        },
        brand: {
          50:  '#EFF6FF',
          100: '#DBEAFE',
          500: '#3B82F6',
          600: '#2563EB',
          700: '#1D4ED8',
        },
        accent: {
          cyan:    '#06B6D4',
          emerald: '#10B981',
          amber:   '#F59E0B',
          rose:    '#F43F5E',
          violet:  '#8B5CF6',
        },
        surface: {
          dark:    '#0F172A',
          card:    '#1E293B',
          border:  '#334155',
          input:   '#0B1120',
        },
        primary: '#3B82F6',
        'primary-fixed-dim': '#60A5FA',
        'on-primary': '#FFFFFF',
        'on-surface': '#F8FAFC',
        'on-surface-variant': '#94A3B8',
        tertiary: '#06B6D4',
        secondary: '#8B5CF6',
      },
      boxShadow: {
        'card': '0 4px 20px -2px rgba(0, 0, 0, 0.4), 0 2px 6px -1px rgba(0, 0, 0, 0.2)',
        'card-hover': '0 10px 30px -4px rgba(59, 130, 246, 0.15), 0 4px 12px -2px rgba(0, 0, 0, 0.4)',
        'glow-blue': '0 0 20px rgba(59, 130, 246, 0.35)',
      },
    },
  },
  plugins: [],
};
