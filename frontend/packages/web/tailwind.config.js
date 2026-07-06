const withOpacity =
    variable =>
    ({ opacityValue }) => {
        if (opacityValue === undefined) {
            return `rgb(var(${variable}) / 1)`;
        }
        return `rgb(var(${variable}) / ${opacityValue})`;
    };

/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
    theme: {
        extend: {
            colors: {
                primary: withOpacity('--color-accent'),
                'primary-hover': withOpacity('--color-accent-strong'),
                page: withOpacity('--color-bg-page'),
                surface: withOpacity('--color-bg-surface'),
                'surface-alt': withOpacity('--color-bg-surface-alt'),
                strong: withOpacity('--color-text-strong'),
                body: withOpacity('--color-text-body'),
                muted: withOpacity('--color-text-muted'),
                'border-soft': withOpacity('--color-border-soft'),
                'border-strong': withOpacity('--color-border-strong'),
                accent: withOpacity('--color-accent'),
                'accent-soft': withOpacity('--color-accent-soft'),
                success: withOpacity('--color-success'),
                warning: withOpacity('--color-warning'),
                danger: withOpacity('--color-danger'),
                'royal-blue': '#4f46e5',
                'tech-blue': '#1e40af',
            },
            fontFamily: {
                sans: [
                    'Segoe UI Variable Text',
                    'PingFang SC',
                    'Microsoft YaHei',
                    'Noto Sans CJK SC',
                    'Source Han Sans SC',
                    'sans-serif',
                ],
            },
            boxShadow: {
                soft: 'var(--shadow-soft)',
                panel: 'var(--shadow-panel)',
            },
        },
    },
    plugins: [],
};
