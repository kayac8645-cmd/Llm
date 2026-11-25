// Smoke-tests the Kotlin/JS facade consumed by the Windows UI.
// Usage: node scripts/verify-shared-js.mjs [path-to-dist]
import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const dist = process.argv[2] ?? 'shared/build/dist/js/productionLibrary';
const mod = await import(pathToFileURL(path.resolve(dist, 'freedom-shared.mjs')).href);
const core = mod.FreedomCore.getInstance();
assert.ok(core, 'FreedomCore export missing');

assert.deepEqual([...core.resolveInput('example.com', 'google')], ['navigate', 'https://example.com/', '']);
assert.equal(core.resolveInput('hello world', 'bing')[1], 'https://www.bing.com/search?q=hello%20world');
assert.equal(core.resolveInput('javascript:alert(1)', 'duckduckgo')[0], 'search');
assert.equal(core.resolveInput('bücher.de', 'google')[1], 'https://xn--bcher-kva.de/');
assert.equal(core.errorKindForNetError(-105), 'dns');
assert.equal(core.formatBytes(1536), '1.5 KB');
assert.equal(core.isPotentiallyDangerous('setup.exe', ''), true);
assert.equal(core.bookmarkMoveCreatesCycle('1', '3', ['1', '', '2', '1', '3', '2']), true);
assert.ok(core.tokensCss('dark').includes('--accent:#2ec4b6;'));
assert.equal(core.redactUrl('https://a.com/x?token=1'), 'https://a.com/x');
assert.equal(core.searchEngines().length, 4);
console.log('shared JS facade: all checks passed');
