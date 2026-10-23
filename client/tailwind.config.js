/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        background: '#09090b',
        surface: '#121215',
        'surface-elevated': '#18181b',
        border: '#27272a',
        primary: {
          DEFAULT: '#3b82f6',
          hover: '#2563eb',
        },
        accent: {
          purple: '#a855f7',
          amber: '#f59e0b',
          emerald: '#10b981',
          rose: '#f43f5e',
          cyan: '#06b6d4',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'Fira Code', 'Consolas', 'monospace'],
      },
      animation: {
        'pulse-subtle': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'border-beam': 'border-beam 2s linear infinite',
      },
    },
  },
  plugins: [],
};
