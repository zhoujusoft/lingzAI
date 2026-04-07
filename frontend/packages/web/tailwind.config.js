/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
    theme: {
        extend: {
            colors: {
                primary: '#3b82f6',
                'primary-hover': '#2563eb',
                'royal-blue': '#4f46e5',
                'tech-blue': '#1e40af',
            },
            fontFamily: {
                sans: [
                    'Noto Sans SC',
                    'PingFang SC',
                    'Microsoft YaHei',
                    'Noto Sans CJK SC',
                    'Source Han Sans SC',
                    'sans-serif',
                ],
            },
        },
    },
    plugins: [],
};
