import path from 'node:path';

const FRONTEND = 'frontend';
const BACKEND = 'backend';
const WINDOWS = process.platform === 'win32';

const quote = (file) => `"${file.replaceAll('"', '\\"')}"`;
const fileList = (files) => files.map(quote).join(' ');
const frontendBin = (name) =>
  quote(path.join(FRONTEND, 'node_modules', '.bin', `${name}${WINDOWS ? '.cmd' : ''}`));
const mavenWrapper = WINDOWS
  ? `${quote(path.join(BACKEND, 'mvnw.cmd'))} -q -f ${quote(path.join(BACKEND, 'pom.xml'))}`
  : `${quote(path.join(BACKEND, 'mvnw'))} -q -f ${quote(path.join(BACKEND, 'pom.xml'))}`;

export default {
  // Frontend TypeScript/HTML: ESLint autofix, then Prettier.
  'frontend/**/*.{ts,html}': (files) => {
    const list = fileList(files);
    return [
      `${frontendBin('eslint')} --fix ${list}`,
      `${frontendBin('prettier')} --write ${list}`,
    ];
  },

  // Frontend styles / JSON: Prettier only.
  'frontend/**/*.{scss,css,json}': (files) => {
    return [`${frontendBin('prettier')} --write ${fileList(files)}`];
  },

  // Backend Java: run Spotless over the backend module. (Applying module-wide keeps the
  // hook simple and robust; the files are already Spotless-clean so this is a fast no-op,
  // and lint-staged only re-stages the files that were actually part of the commit.)
  'backend/**/*.java': () => [`${mavenWrapper} spotless:apply`],
};
