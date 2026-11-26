import React, { useEffect, useState } from 'react';
import { Navbar } from '../components/Navbar';
import { Hero } from '../components/Hero';
import { KeyBenefits } from '../components/KeyBenefits';
import { HowItWorks } from '../components/HowItWorks';
import { Features } from '../components/Features';
import { AppPreview } from '../components/AppPreview';
import { WhyFleetX } from '../components/WhyFleetX';
import { DownloadSection } from '../components/DownloadSection';
import { ReleaseHistory } from '../components/ReleaseHistory';
import { FaqSection } from '../components/FaqSection';
import { FinalCta } from '../components/FinalCta';
import { Footer } from '../components/Footer';
import { getFleetXReleases } from '../services/githubReleaseService';
import { ReleasesState } from '../types/github';

export const Home: React.FC = () => {
  const [releasesState, setReleasesState] = useState<ReleasesState>({
    latestRelease: null,
    previousReleases: [],
    isLoading: true,
    isError: false,
    errorMessage: null,
    lastFetchedAt: null,
    fromCache: false,
  });

  useEffect(() => {
    let isMounted = true;

    async function loadReleases() {
      try {
        const data = await getFleetXReleases();
        if (isMounted) {
          setReleasesState(data);
        }
      } catch (err: unknown) {
        if (isMounted) {
          setReleasesState((prev) => ({
            ...prev,
            isLoading: false,
            isError: true,
            errorMessage: err instanceof Error ? err.message : 'Unknown release error',
          }));
        }
      }
    }

    loadReleases();

    return () => {
      isMounted = false;
    };
  }, []);

  const scrollToDownload = () => {
    const el = document.getElementById('download');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-college-surface">
      <Navbar latestRelease={releasesState.latestRelease} />

      <main className="flex-grow">
        <Hero latestRelease={releasesState.latestRelease} onDownloadClick={scrollToDownload} />
        <KeyBenefits />
        <HowItWorks />
        <Features />
        <AppPreview />
        <WhyFleetX />
        <DownloadSection
          latestRelease={releasesState.latestRelease}
          isLoading={releasesState.isLoading}
          isError={releasesState.isError}
          errorMessage={releasesState.errorMessage}
        />
        <ReleaseHistory
          previousReleases={releasesState.previousReleases}
          isLoading={releasesState.isLoading}
        />
        <FaqSection />
        <FinalCta onExploreClick={scrollToDownload} />
      </main>

      <Footer latestRelease={releasesState.latestRelease} />
    </div>
  );
};
