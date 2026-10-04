export default {content: [
  './index.html',
  './src/**/*.{js,ts,jsx,tsx}'
],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'monospace'],
      },
      colors: {
        navy: {
          50: '#eef3f9',
          600: '#1c456f',
          700: '#143658',
          800: '#0e2b47',
          900: '#0b2545',
          950: '#071a33',
        },
        brand: {
          50: '#eef5fd',
          100: '#d9e9fa',
          200: '#b3d1f4',
          300: '#7fb2ea',
          400: '#4a8ddc',
          500: '#1f6fd0',
          600: '#1759ac',
          700: '#134788',
          800: '#113a6e',
        },
        teal: {
          50: '#e8f7f2',
          100: '#cdeee4',
          400: '#2bb894',
          500: '#0e9f7e',
          600: '#0b7f65',
          700: '#0a6853',
        },
        amber: {
          50: '#fef6e7',
          100: '#fdecc8',
          400: '#eda600',
          500: '#d98a00',
          600: '#b37200',
          700: '#8f5b00',
        },
        critical: {
          50: '#fdeded',
          100: '#fad9d9',
          400: '#e35d5d',
          500: '#d13f3f',
          600: '#b32e2e',
          700: '#8f2424',
        },
      },
      borderRadius: {
        DEFAULT: '6px',
        md: '6px',
        lg: '8px',
        xl: '10px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.06)',
        panel: '0 8px 24px rgba(11, 37, 69, 0.12)',
      },
      fontSize: {
        '2xs': ['11px', '16px'],
      },
    },
  },
}
