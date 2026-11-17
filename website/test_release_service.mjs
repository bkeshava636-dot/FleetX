import assert from 'assert';
import {
  formatFileSize,
  formatDate,
  extractSha256,
  extractWhatsNew,
  sanitizeVersion,
  findBestApkAsset,
  parseRelease
} from './src/services/githubReleaseService.ts';

console.log('==================================================');
console.log('RUNNING FLEETX WEBSITE RELEASE SERVICE TESTS');
console.log('==================================================\n');

let passed = 0;
let total = 0;

async function it(desc, fn) {
  total++;
  try {
    await fn();
    console.log(`  ✅ PASS: ${desc}`);
    passed++;
  } catch (e) {
    console.error(`  ❌ FAIL: ${desc}`);
    console.error(e);
  }
}

async function run() {

// 1. File Size Formatter
await it('formats file sizes accurately into MB', () => {
  assert.strictEqual(formatFileSize(6074896), '5.79 MB');
  assert.strictEqual(formatFileSize(1048576), '1.00 MB');
  assert.strictEqual(formatFileSize(0), '0 MB');
});

// 2. Date Formatter
await it('formats ISO dates correctly', () => {
  const formatted = formatDate('2026-11-17T12:59:55Z');
  assert.ok(formatted.includes('2026'), 'Date should include year 2026');
  assert.ok(formatted.includes('Nov') || formatted.includes('11'), 'Date should include month');
});

// 3. Version Sanitization
await it('sanitizes version tag names by stripping leading v', () => {
  assert.strictEqual(sanitizeVersion('v1.0.43'), '1.0.43');
  assert.strictEqual(sanitizeVersion('V2.1.0'), '2.1.0');
  assert.strictEqual(sanitizeVersion('1.0.42'), '1.0.42');
});

// 4. SHA-256 Checksum Extraction from Body and Digest
await it('extracts 64-character SHA-256 hash from markdown release body', () => {
  const body = `
### Release Information
- **Version Name:** \`1.0.43\`
- **SHA-256 Checksum:**
\`\`\`
870cc4cea23c7609fcc7669827c98169cb38e022bde5bb108eefaf734d49d825
\`\`\`
  `;
  const sha = extractSha256(body);
  assert.strictEqual(sha, '870cc4cea23c7609fcc7669827c98169cb38e022bde5bb108eefaf734d49d825');
});

await it('extracts SHA-256 hash from asset digest if present', () => {
  const asset = {
    digest: 'sha256:c9be5b6684a8efb8d0a58c8484a6498d92d62ab7442d5f84da2f3e4aca1da66b'
  };
  const sha = extractSha256('', asset);
  assert.strictEqual(sha, 'c9be5b6684a8efb8d0a58c8484a6498d92d62ab7442d5f84da2f3e4aca1da66b');
});

// 5. Best APK Asset Selection
await it('selects the FleetX universal APK from mixed assets list', () => {
  const assets = [
    { name: 'checksums.txt', size: 100, browser_download_url: 'https://github.com/checksums.txt' },
    { name: 'update-manifest.json', size: 200, browser_download_url: 'https://github.com/manifest.json' },
    { name: 'FleetX-v1.0.43.apk', size: 6074896, browser_download_url: 'https://github.com/FleetX-v1.0.43.apk' }
  ];
  const best = findBestApkAsset(assets);
  assert.ok(best !== null);
  assert.strictEqual(best.name, 'FleetX-v1.0.43.apk');
  assert.strictEqual(best.size, 6074896);
});

await it('returns null when no APK is present in release assets', () => {
  const assets = [
    { name: 'checksums.txt', size: 100, browser_download_url: 'https://github.com/checksums.txt' },
    { name: 'source.tar.gz', size: 5000, browser_download_url: 'https://github.com/source.tar.gz' }
  ];
  const best = findBestApkAsset(assets);
  assert.strictEqual(best, null);
});

// 6. What's New Extraction
await it('extracts what is new summary from release notes markdown', () => {
  const body = `
## FleetX Campus Transit Portal - Release v1.0.43

### What's New in this Release
Prevent unintended driver tracking on launch, robust batch clear and audit trail, harden Firestore security rules.

### Recent Commits (v1.0.43)
0b1e0f4 feat: add bus started push notifications
  `;
  const summary = extractWhatsNew(body);
  assert.ok(summary.includes('Prevent unintended driver tracking'));
  assert.ok(!summary.includes('Recent Commits'));
});

// 7. Full ParseRelease Integration
await it('parses complete raw GitHub release into structured ReleaseInfo model', () => {
  const raw = {
    id: 448636614,
    tag_name: 'v1.0.43',
    name: 'FleetX v1.0.43',
    created_at: '2026-11-17T12:55:01Z',
    published_at: '2026-11-17T12:59:55Z',
    body: `
### What's New in this Release
Bus started push notifications.

SHA-256: 870cc4cea23c7609fcc7669827c98169cb38e022bde5bb108eefaf734d49d825
    `,
    html_url: 'https://github.com/bkeshava636-dot/FleetX/releases/tag/v1.0.43',
    assets: [
      { name: 'checksums.txt', size: 85, browser_download_url: 'https://github.com/checksums.txt' },
      { name: 'FleetX-v1.0.43.apk', size: 6074896, browser_download_url: 'https://github.com/FleetX-v1.0.43.apk' }
    ]
  };

  const parsed = parseRelease(raw, true);
  assert.strictEqual(parsed.version, '1.0.43');
  assert.strictEqual(parsed.tagName, 'v1.0.43');
  assert.strictEqual(parsed.isLatest, true);
  assert.ok(parsed.apkAsset !== null);
  assert.strictEqual(parsed.apkAsset.name, 'FleetX-v1.0.43.apk');
  assert.strictEqual(parsed.apkAsset.formattedSize, '5.79 MB');
  assert.strictEqual(parsed.sha256, '870cc4cea23c7609fcc7669827c98169cb38e022bde5bb108eefaf734d49d825');
  assert.strictEqual(parsed.minAndroid, 'Android 8.0 (Oreo, API 26)+');
  assert.ok(parsed.whatsNewSummary.includes('Bus started push notifications'));
});

// 8. Live GitHub API Integration Test against actual repository
await it('fetches and parses live GitHub release from bkeshava636-dot/FleetX', async () => {
  const url = 'https://api.github.com/repos/bkeshava636-dot/FleetX/releases?per_page=5';
  const res = await fetch(url, { headers: { 'User-Agent': 'FleetX-Web-Test' } });
  if (res.status === 403) {
    console.log('    ℹ️ Skipping live fetch assertion due to GitHub rate limit on IP');
    return;
  }
  assert.strictEqual(res.ok, true, `GitHub API response should be OK, got ${res.status}`);
  const data = await res.json();
  assert.ok(Array.isArray(data), 'Releases should be an array');
  assert.ok(data.length > 0, 'Should have at least 1 release');

  const latest = parseRelease(data[0], true);
  console.log(`    ℹ️ Live latest release detected: ${latest.tagName} (${latest.apkAsset?.name}, size: ${latest.apkAsset?.formattedSize})`);
  assert.ok(latest.version.length > 0, 'Version should be non-empty');
  assert.ok(latest.apkAsset !== null, 'Latest release should have an APK asset');
  assert.ok(latest.apkAsset.downloadUrl.includes('FleetX'), 'Download URL should point to FleetX APK');
  assert.ok(latest.sha256 !== null, 'SHA-256 should be extracted from live release');
});

  console.log('\n==================================================');
  console.log(`TEST SUMMARY: ${passed} / ${total} tests passed.`);
  console.log('==================================================');

  if (passed !== total) {
    process.exit(1);
  }
}

run().catch(err => {
  console.error('Test run failed:', err);
  process.exit(1);
});
