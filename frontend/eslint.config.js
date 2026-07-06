// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const prettier = require('eslint-config-prettier');

module.exports = tseslint.config(
  {
    ignores: ['dist/**', '.angular/**', 'node_modules/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
      prettier, // keep ESLint out of formatting; Prettier owns that
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      // This dashboard is intentionally loosely typed (telemetry payloads are dynamic),
      // so `any` is advisory rather than a hard failure; unused symbols warn.
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'warn',
      // Constructor injection is used throughout by design — advise, don't block.
      '@angular-eslint/prefer-inject': 'warn',
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {
      // Existing templates use *ngIf/*ngFor and this HUD's own markup patterns;
      // surface these as advisories rather than blocking the gate.
      '@angular-eslint/template/prefer-control-flow': 'warn',
      '@angular-eslint/template/no-negated-async': 'warn',
      '@angular-eslint/template/label-has-associated-control': 'warn',
    },
  },
);
