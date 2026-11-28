import { execFileSync } from 'node:child_process';
import { basename, extname } from 'node:path';
import { pathToFileURL } from 'node:url';

const C_LIKE = new Set([
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.kt',
  '.kts',
  '.java',
  '.css',
  '.scss',
  '.go',
  '.swift',
  '.rs',
]);
const HASH = new Set(['.sh', '.bash', '.yml', '.yaml', '.py', '.toml', '.properties']);
const HASH_NAMES = new Set(['Dockerfile']);
const SKIPPED_PATH = /(^|\/)(node_modules|\.yarn|\.gradle|dist|build|docs|__snapshots__)\//;
const DIRECTIVE =
  /eslint-|oxlint-|biome-|prettier-ignore|@ts-(expect-error|ignore|nocheck|check)|@jsx|istanbul ignore|c8 ignore|\/\/\/\s*<reference|webpack[A-Z]|shellcheck|noqa|pragma|SPDX-License|Copyright|go:build|syntax=|ktlint|detekt|NOSONAR|^#!/;

export function familyOf(path) {
  if (SKIPPED_PATH.test(path)) return null;
  if (HASH_NAMES.has(basename(path))) return 'hash';
  const ext = extname(path);
  if (C_LIKE.has(ext)) return 'c';
  if (HASH.has(ext)) return 'hash';
  return null;
}

function scanLine(line, family, inBlock) {
  let quote = null;
  let blockSeen = inBlock;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    const next = line[i + 1];
    if (inBlock) {
      if (ch === '*' && next === '/') {
        inBlock = false;
        i += 1;
      }
      continue;
    }
    if (quote) {
      if (ch === '\\') i += 1;
      else if (ch === quote) quote = null;
      continue;
    }
    const spaced = i === 0 || /\s/.test(line[i - 1]);
    if (family === 'hash') {
      if (ch === '#' && spaced) return { comment: line.slice(i), inBlock };
    } else if (ch === '/' && next === '/' && (i === 0 || (line[i - 1] !== ':' && line[i - 1] !== '\\'))) {
      return { comment: line.slice(i), inBlock };
    } else if (ch === '/' && next === '*') {
      inBlock = true;
      blockSeen = true;
      i += 1;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === '`') quote = ch;
  }
  return { comment: null, inBlock, blockSeen };
}

export function commentLines(text, family) {
  const found = new Map();
  let inBlock = false;
  text.split('\n').forEach((line, index) => {
    const startedInBlock = inBlock;
    const result = scanLine(line, family, inBlock);
    inBlock = result.inBlock;
    if (family === 'hash' && index === 0 && line.startsWith('#!')) return;
    const text = result.comment ?? (startedInBlock || result.blockSeen ? line.trim() : null);
    if (text !== null && text !== '') found.set(index + 1, text);
  });
  return found;
}

export function addedLines(diff) {
  const files = new Map();
  let current = null;
  for (const line of diff.split('\n')) {
    if (line.startsWith('+++ b/')) {
      current = new Set();
      files.set(line.slice(6), current);
    } else if (line.startsWith('+++ ')) {
      current = null;
    } else if (current && line.startsWith('@@')) {
      const match = /\+(\d+)(?:,(\d+))?/.exec(line);
      const start = Number(match[1]);
      const count = match[2] === undefined ? 1 : Number(match[2]);
      for (let n = start; n < start + count; n += 1) current.add(n);
    }
  }
  return files;
}

export function findAddedComments(diff, readFile) {
  const violations = [];
  for (const [path, lines] of addedLines(diff)) {
    const family = familyOf(path);
    if (!family || lines.size === 0) continue;
    const comments = commentLines(readFile(path), family);
    for (const number of [...lines].sort((a, b) => a - b)) {
      const text = comments.get(number);
      if (text !== undefined && !DIRECTIVE.test(text)) violations.push({ path, line: number, text });
    }
  }
  return violations;
}

function main() {
  const base = process.argv[2] ?? `origin/${process.env.GITHUB_BASE_REF || 'main'}`;
  const git = (...args) => {
    try {
      return execFileSync('git', args, {
        encoding: 'utf8',
        maxBuffer: 256 * 1024 * 1024,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (error) {
      console.error(`git ${args.slice(0, 2).join(' ')} failed: ${String(error.stderr).split('\n')[0]}`);
      process.exit(2);
    }
  };
  const diff = git('diff', '--unified=0', '--no-renames', '--diff-filter=AM', `${base}...HEAD`);
  const violations = findAddedComments(diff, (path) => git('show', `HEAD:${path}`));
  if (violations.length === 0) {
    console.log('No added comments.');
    return;
  }
  for (const { path, line, text } of violations) console.error(`${path}:${line}: ${text.slice(0, 100)}`);
  console.error(
    `\n${violations.length} added comment line(s). Comments are not allowed in this repository: put the why in the commit message or the PR body, and in the naming. Tool directives (eslint-disable, @ts-expect-error, shebangs, license headers) are exempt.`,
  );
  process.exit(1);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
