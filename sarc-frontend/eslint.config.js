// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/** Las peticiones HTTP se hacen en data-access (y las consultas reactivas en application). */
const httpOnlyInDataAccess = {
  name: '@angular/common/http',
  importNames: ['HttpClient', 'httpResource'],
  message: 'Las peticiones van en data-access; las consultas reactivas, en application.',
};
/** Import relativo que sale del modulo hacia el interior de otro modulo de features. */
const otherFeature = {
  regex:
    '^(\\.\\./)+(?!core/|shared/|layout/)[a-z-]+/(domain|application|data-access|presentation)/',
  message: 'Un modulo de features no importa el interior de otro; mueva lo comun a shared o core.',
};

module.exports = defineConfig([
  {
    // Tipos generados desde el contrato OpenAPI (npm run api:types).
    ignores: [
      'src/app/core/api/generated/**',
      'node_modules/**',
      'dist/**',
      'build/**',
      'coverage/**',
      '.angular/**',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'sarc',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'sarc',
          style: 'kebab-case',
        },
      ],
    },
  },
  // Fronteras de capas (README): presentation -> application -> data-access -> domain. Un modulo
  // de features no importa el interior de otro; lo comun va en shared o core.
  {
    files: ['src/app/features/*/domain/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [httpOnlyInDataAccess],
          patterns: [
            {
              regex: '^\\.\\./(application|data-access|presentation)/',
              message: 'El dominio no depende de otras capas.',
            },
            otherFeature,
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/features/*/data-access/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              regex: '^\\.\\./(application|presentation)/',
              message: 'El acceso a datos solo conoce el dominio.',
            },
            otherFeature,
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/features/*/application/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { regex: '^\\.\\./presentation/', message: 'La aplicacion no depende de la vista.' },
            otherFeature,
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/features/*/presentation/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [httpOnlyInDataAccess],
          patterns: [
            {
              regex: '^(\\.\\./){1,2}data-access/',
              message: 'La vista usa la fachada de application, no el acceso a datos.',
            },
            otherFeature,
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
