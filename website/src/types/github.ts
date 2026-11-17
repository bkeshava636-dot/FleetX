export interface GitHubAsset {
  id: number;
  name: string;
  size: number;
  download_count: number;
  browser_download_url: string;
  content_type: string;
  updated_at: string;
  digest?: string;
}

export interface GitHubReleaseRaw {
  id: number;
  tag_name: string;
  name: string;
  draft: boolean;
  prerelease: boolean;
  created_at: string;
  published_at: string;
  body: string;
  html_url: string;
  assets: GitHubAsset[];
}

export interface ApkAssetInfo {
  name: string;
  downloadUrl: string;
  sizeBytes: number;
  formattedSize: string;
}

export interface ReleaseInfo {
  id: number;
  tagName: string;
  version: string;
  name: string;
  publishedAt: string;
  formattedDate: string;
  apkAsset: ApkAssetInfo | null;
  sha256: string | null;
  minAndroid: string;
  releaseNotes: string;
  whatsNewSummary: string;
  htmlUrl: string;
  isLatest: boolean;
}

export interface ReleasesState {
  latestRelease: ReleaseInfo | null;
  previousReleases: ReleaseInfo[];
  isLoading: boolean;
  isError: boolean;
  errorMessage: string | null;
  lastFetchedAt: number | null;
  fromCache: boolean;
}
