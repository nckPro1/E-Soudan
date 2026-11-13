/**
 * Tailwind v3 (cùng phiên bản với bản thiết kế Stitch "Microsoft Teams Chat Clone", xem design/homepage-assets/teams.html)
 * để dán nguyên markup của thiết kế vào component. Các class dạng `bg-[#f0f2f5]`, `w-[52px]`... dùng trực tiếp.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Segoe UI', '-apple-system', 'BlinkMacSystemFont', 'Roboto', 'Helvetica Neue', 'Noto Sans JP', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
