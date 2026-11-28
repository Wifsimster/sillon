import assert from 'node:assert/strict';
import { test } from 'node:test';
import { addedLines, commentLines, familyOf, findAddedComments } from '../check-no-added-comments.mjs';

test('familyOf picks the comment syntax from the path', () => {
  assert.equal(familyOf('apps/expo/App.tsx'), 'c');
  assert.equal(familyOf('scripts/check.sh'), 'hash');
  assert.equal(familyOf('.github/workflows/ci.yml'), 'hash');
  assert.equal(familyOf('README.md'), null);
  assert.equal(familyOf('docs/tickets/01.ts'), null);
  assert.equal(familyOf('node_modules/x/index.js'), null);
});

test('commentLines finds full-line, trailing and block comments', () => {
  const source = [
    'const a = 1;',
    '// note',
    'const b = 2; // trailing',
    '/*',
    ' still inside',
    '*/',
    'const c = 3;',
  ].join('\n');
  assert.deepEqual([...commentLines(source, 'c').keys()], [2, 3, 4, 5, 6]);
});

test('commentLines ignores comment markers inside strings and URLs', () => {
  const source = [
    "const url = 'https://example.com';",
    'const s = "// not a comment";',
    'const t = `/* nor this */`;',
  ].join('\n');
  assert.deepEqual([...commentLines(source, 'c').keys()], []);
});

test('commentLines handles hash comments and keeps the shebang', () => {
  const source = ['#!/usr/bin/env bash', '# note', 'echo "a # b"', 'echo $# args', 'run # trailing'].join('\n');
  assert.deepEqual([...commentLines(source, 'hash').keys()], [2, 5]);
});

test('addedLines reads the new line ranges of each file', () => {
  const diff = ['+++ b/a.ts', '@@ -1,0 +2,2 @@', '+x', '+y', '@@ -9 +11 @@', '+z', '+++ /dev/null'].join('\n');
  assert.deepEqual([...addedLines(diff).get('a.ts')], [2, 3, 11]);
});

test('findAddedComments flags only added comment lines and spares directives', () => {
  const file = [
    '// old comment',
    'const a = 1;',
    '// new comment',
    '// eslint-disable-next-line no-console',
    'console.log(a);',
  ].join('\n');
  const diff = [
    '+++ b/a.ts',
    '@@ -2,0 +3,3 @@',
    '+// new comment',
    '+// eslint-disable-next-line no-console',
    '+console.log(a);',
  ].join('\n');
  const violations = findAddedComments(diff, () => file);
  assert.deepEqual(
    violations.map((v) => [v.path, v.line]),
    [['a.ts', 3]],
  );
});

test('findAddedComments leaves non code files alone', () => {
  const diff = ['+++ b/README.md', '@@ -0,0 +1 @@', '+# Title'].join('\n');
  assert.deepEqual(
    findAddedComments(diff, () => '# Title'),
    [],
  );
});
