/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        background: 'var(--color-bg)',
        surface: 'var(--color-surface)',
        'surface-elevated': 'var(--color-surface-elevated)',
        border: 'var(--color-border)',
        brand: {
          orange: '#f37022',
          'orange-hover': '#ff6500',
          'orange-dark': '#c5621b',
          purple: '#5f308c',
          'purple-hover': '#77239b',
          beige: '#eae8e4',
          noir: '#080407',
        },
        primary: {
          DEFAULT: '#f37022',
          hover: '#ff6500',
          dark: '#c5621b',
        },
        secondary: {
          DEFAULT: '#5f308c',
          hover: '#77239b',
          dark: '#1b0351',
        },
        accent: {
          orange: '#f37022',
          purple: '#77239b',
          amber: '#f59e0b',
          emerald: '#10b981',
          rose: '#fb322b',
          cyan: '#00a8fa',
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
