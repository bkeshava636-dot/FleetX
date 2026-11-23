import type {
  GitHubAsset,
  GitHubReleaseRaw,
  ReleaseInfo,
  ReleasesState
} from '../types/github.ts';

const PRIMARY_REPO = 'bkeshava636-dot/FleetX';
const FALLBACK_REPO = 'bkeshava636-dot/college_bus_tracker';
const CACHE_KEY = 'fleetx_releases_cache_v2';
const CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes

/**
 * Format raw byte size into human readable string (e.g. 5.79 MB).
 */
export function formatFileSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 MB';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(2)} MB`;
}

/**
 * Format ISO date string into readable date (e.g. Nov 17, 2026).
 */
export function formatDate(isoString: string): string {
  if (!isoString) return 'Recent';
  try {
    const d = new Date(isoString);
    return d.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  } catch {
    return isoString.split('T')[0] || 'Recent';
  }
}

/**
 * Extract SHA-256 checksum from release markdown body or asset digest.
 */
export function extractSha256(body: string, apkAsset?: GitHubAsset): string | null {
  // 1. Try regex extraction from release markdown body
  if (body) {
    // Matches "SHA-256 Checksum:\n```\n<hash>\n```" or "SHA-256: <hash>"
    const match = body.match(/(?:SHA-256|sha256|Checksum)[^a-fA-F0-9]*([a-fA-F0-9]{64})/i);
    if (match && match[1]) {
      return match[1].toLowerCase();
    }

    // Fallback: search for any standalone 64-char hex string
    const hexMatch = body.match(/\b([a-fA-F0-9]{64})\b/);
    if (hexMatch && hexMatch[1]) {
      return hexMatch[1].toLowerCase();
    }
  }

  // 2. Try asset digest if provided by GitHub API
  if (apkAsset && apkAsset.digest && apkAsset.digest.startsWith('sha256:')) {
    return apkAsset.digest.replace('sha256:', '').toLowerCase();
  }

  return null;
}

/**
 * Extract a concise "What's New" summary from the release notes body.
 */
export function extractWhatsNew(body: string): string {
  if (!body) return 'Performance improvements, stability updates, and general transit tracking optimizations.';

  const lines = body.split('\n');
  const whatsNewIndex = lines.findIndex(l => /what'?s new/i.test(l));

  if (whatsNewIndex !== -1) {
    const collected: string[] = [];
    for (let i = whatsNewIndex + 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('##') || line.startsWith('### Recent Commits') || line.startsWith('### Release Information') || line.startsWith('### Verification') || line.startsWith('**Full Changelog**')) {
        break;
      }
      if (line) {
        collected.push(line.replace(/^[-*•]\s*/, ''));
      }
    }
    if (collected.length > 0) {
      return collected.join(' ');
    }
  }

  // Fallback to first non-empty paragraph
  const firstParagraph = lines.find(l => l.trim().length > 10 && !l.startsWith('#'));
  return firstParagraph || 'Performance improvements, stability updates, and general transit tracking optimizations.';
}

/**
 * Clean version string (e.g., 'v1.0.43' -> '1.0.43').
 */
export function sanitizeVersion(tag: string): string {
  if (!tag) return '1.0.0';
  return tag.replace(/^v/i, '').trim();
}

/**
 * Select the best official APK asset from release assets.
 * Preference: FleetX-*.apk > universal > release > any .apk > null
 */
export function findBestApkAsset(assets: GitHubAsset[]): GitHubAsset | null {
  if (!assets || !Array.isArray(assets) || assets.length === 0) {
    return null;
  }

  const apkAssets = assets.filter(
    a => a.name && a.name.toLowerCase().endsWith('.apk') && a.size > 0
  );

  if (apkAssets.length === 0) return null;
  if (apkAssets.length === 1) return apkAssets[0];

  // Preference matching
  const fleetxMatch = apkAssets.find(a => /fleetx/i.test(a.name));
  if (fleetxMatch) return fleetxMatch;

  const universalMatch = apkAssets.find(a => /universal/i.test(a.name));
  if (universalMatch) return universalMatch;

  const releaseMatch = apkAssets.find(a => /release/i.test(a.name));
  if (releaseMatch) return releaseMatch;

  return apkAssets[0];
}

/**
 * Parse raw GitHub release into structured ReleaseInfo.
 */
export function parseRelease(raw: GitHubReleaseRaw, isLatest = false): ReleaseInfo {
  const apk = findBestApkAsset(raw.assets);
  const version = sanitizeVersion(raw.tag_name);

  return {
    id: raw.id,
    tagName: raw.tag_name,
    version,
    name: raw.name || `FleetX v${version}`,
    publishedAt: raw.published_at || raw.created_at,
    formattedDate: formatDate(raw.published_at || raw.created_at),
    apkAsset: apk
      ? {
          name: apk.name,
          downloadUrl: apk.browser_download_url,
          sizeBytes: apk.size,
          formattedSize: formatFileSize(apk.size)
        }
      : null,
    sha256: extractSha256(raw.body, apk || undefined),
    minAndroid: 'Android 8.0 (Oreo, API 26)+',
    releaseNotes: raw.body || '',
    whatsNewSummary: extractWhatsNew(raw.body),
    htmlUrl: raw.html_url,
    isLatest
  };
}

/**
 * Fetch with timeout helper.
 */
async function fetchWithTimeout(url: string, timeoutMs = 8000): Promise<Response> {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: {
        Accept: 'application/vnd.github.v3+json'
      }
    });
    return res;
  } finally {
    clearTimeout(id);
  }
}

/**
 * Fetch GitHub releases for a given repository.
 */
async function fetchFromRepo(repoPath: string): Promise<GitHubReleaseRaw[]> {
  const url = `https://api.github.com/repos/${repoPath}/releases?per_page=15`;
  const res = await fetchWithTimeout(url);

  if (!res.ok) {
    throw new Error(`GitHub API returned status ${res.status} for ${repoPath}`);
  }

  const data = await res.json();
  if (!Array.isArray(data)) {
    throw new Error('Expected array of releases from GitHub API');
  }

  return data;
}

/**
 * Main service method: retrieves latest and previous releases with caching and fallbacks.
 */
export async function getFleetXReleases(): Promise<ReleasesState> {
  // 1. Try reading cache
  let cachedData: ReleasesState | null = null;
  try {
    const rawCache = localStorage.getItem(CACHE_KEY);
    if (rawCache) {
      const parsed = JSON.parse(rawCache);
      if (parsed && parsed.lastFetchedAt && Date.now() - parsed.lastFetchedAt < CACHE_TTL_MS) {
        cachedData = { ...parsed, fromCache: true, isLoading: false };
      }
    }
  } catch (err) {
    console.warn('[githubReleaseService] Cache read error:', err);
  }

  // If cache is fresh, return it
  if (cachedData && cachedData.latestRelease) {
    return cachedData;
  }

  // 2. Network fetch
  try {
    let releasesRaw: GitHubReleaseRaw[] = [];

    // Try primary repo first
    try {
      releasesRaw = await fetchFromRepo(PRIMARY_REPO);
    } catch (primaryErr) {
      console.warn(`[githubReleaseService] Primary repo ${PRIMARY_REPO} failed, trying fallback:`, primaryErr);
      releasesRaw = await fetchFromRepo(FALLBACK_REPO);
    }

    // Filter out drafts and pre-releases for stable release stream
    const stableReleases = releasesRaw.filter(r => !r.draft && !r.prerelease);

    if (stableReleases.length === 0) {
      throw new Error('No public stable releases found in repository.');
    }

    const latestParsed = parseRelease(stableReleases[0], true);
    const previousParsed = stableReleases.slice(1).map(r => parseRelease(r, false));

    const state: ReleasesState = {
      latestRelease: latestParsed,
      previousReleases: previousParsed,
      isLoading: false,
      isError: false,
      errorMessage: null,
      lastFetchedAt: Date.now(),
      fromCache: false
    };

    // Save to cache
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(state));
    } catch (e) {
      console.warn('[githubReleaseService] Cache write error:', e);
    }

    return state;
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to fetch release information';
    console.error('[githubReleaseService] Error fetching releases:', errorMsg);

    // If we have stale cache, use it instead of failing completely
    if (cachedData && cachedData.latestRelease) {
      return {
        ...cachedData,
        isLoading: false,
        isError: false,
        fromCache: true
      };
    }

    return {
      latestRelease: null,
      previousReleases: [],
      isLoading: false,
      isError: true,
      errorMessage: errorMsg,
      lastFetchedAt: null,
      fromCache: false
    };
  }
}
