import React, { Suspense, lazy, useEffect, useState } from 'react';
import { Navbar, type LandingTheme } from '../components/Navbar';
import { Hero } from '../components/Hero';
import { KeyBenefits } from '../components/KeyBenefits';
import { getFleetXReleases } from '../services/githubReleaseService';
import { ReleasesState } from '../types/github';

const HowItWorks = lazy(() => import('../components/HowItWorks').then((m) => ({ default: m.HowItWorks })));
const Features = lazy(() => import('../components/Features').then((m) => ({ default: m.Features })));
const AppPreview = lazy(() => import('../components/AppPreview').then((m) => ({ default: m.AppPreview })));
const WhyFleetX = lazy(() => import('../components/WhyFleetX').then((m) => ({ default: m.WhyFleetX })));
const DownloadSection = lazy(() => import('../components/DownloadSection').then((m) => ({ default: m.DownloadSection })));
const ReleaseHistory = lazy(() => import('../components/ReleaseHistory').then((m) => ({ default: m.ReleaseHistory })));
const FaqSection = lazy(() => import('../components/FaqSection').then((m) => ({ default: m.FaqSection })));
const FinalCta = lazy(() => import('../components/FinalCta').then((m) => ({ default: m.FinalCta })));
const Footer = lazy(() => import('../components/Footer').then((m) => ({ default: m.Footer })));

export const Home: React.FC = () => {
  const [theme, setTheme] = useState<LandingTheme>(() => {
    try {
      const saved = localStorage.getItem('fleetx_landing_theme');
      return saved === 'light-bw' ? 'light-bw' : 'dark';
    } catch {
      return 'dark';
    }
  });

  const toggleTheme = () => {
    setTheme((prev) => {
      const next: LandingTheme = prev === 'dark' ? 'light-bw' : 'dark';
      try {
        localStorage.setItem('fleetx_landing_theme', next);
      } catch {
        // ignore storage errors
      }
      return next;
    });
  };

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
    <div
      className={`min-h-screen flex flex-col bg-fleet-offwhite transition-colors duration-200 ${
        theme === 'light-bw' ? 'landing-theme-bw' : ''
      }`}
    >
      <Navbar
        latestRelease={releasesState.latestRelease}
        theme={theme}
        onToggleTheme={toggleTheme}
      />

      <main className="flex-grow">
        <Hero latestRelease={releasesState.latestRelease} onDownloadClick={scrollToDownload} />
        <KeyBenefits />
        <Suspense fallback={null}>
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
        </Suspense>
      </main>

      <Suspense fallback={null}>
        <Footer latestRelease={releasesState.latestRelease} />
      </Suspense>
    </div>
  );
};
