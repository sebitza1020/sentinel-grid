import path from 'node:path';

// lint-staged passes absolute paths. Each workspace uses its own toolchain, so we
// `cd` into the workspace and hand it paths relative to that workspace.
const FRONTEND = 'frontend';
const BACKEND = 'backend';

const rel = (dir, files) => files.map((f) => `"${path.relative(dir, f)}"`).join(' ');

export default {
  // Frontend TypeScript/HTML: ESLint autofix, then Prettier.
  'frontend/**/*.{ts,html}': (files) => {
    const list = rel(FRONTEND, files);
    return [
      `bash -c 'cd ${FRONTEND} && npx eslint --fix ${list}'`,
      `bash -c 'cd ${FRONTEND} && npx prettier --write ${list}'`,
    ];
  },

  // Frontend styles / JSON: Prettier only.
  'frontend/**/*.{scss,css,json}': (files) => {
    const list = rel(FRONTEND, files);
    return [`bash -c 'cd ${FRONTEND} && npx prettier --write ${list}'`];
  },

  // Backend Java: run Spotless over the backend module. (Applying module-wide keeps the
  // hook simple and robust; the files are already Spotless-clean so this is a fast no-op,
  // and lint-staged only re-stages the files that were actually part of the commit.)
  'backend/**/*.java': () => [`bash -c 'cd ${BACKEND} && ./mvnw -q spotless:apply'`],
};
