import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';

export default tseslint.config(
  {ignores:['dist','node_modules','vite.config.js','vite.config.d.ts']},
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files:['src/**/*.{ts,tsx}'],
    languageOptions:{globals:{window:'readonly',document:'readonly',localStorage:'readonly',location:'readonly',fetch:'readonly',confirm:'readonly',URLSearchParams:'readonly',setTimeout:'readonly',HTMLInputElement:'readonly'}},
    plugins:{'react-hooks':reactHooks},
    rules:{...reactHooks.configs.recommended.rules,'@typescript-eslint/no-explicit-any':'off','@typescript-eslint/no-unused-vars':'off','no-undef':'off'}
  }
);
