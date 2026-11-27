import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Download, Menu, X, MapPin, Shield, Sun, Moon } from 'lucide-react';
import logoUrl from '../assets/logo/fleetx-logo.png';
import { ReleaseInfo } from '../types/github';

export type LandingTheme = 'dark' | 'light-bw';

interface NavbarProps {
  latestRelease: ReleaseInfo | null;
  theme?: LandingTheme;
  onToggleTheme?: () => void;
}

const NAV_LINKS = [
  { id: 'home', label: 'Home', href: '#home' },
  { id: 'benefits', label: 'Benefits', href: '#benefits' },
  { id: 'how-it-works', label: 'Workflow', href: '#how-it-works' },
  { id: 'features', label: 'Capabilities', href: '#features' },
  { id: 'preview', label: 'Showcase', href: '#preview' },
  { id: 'faq', label: 'FAQ', href: '#faq' },
];

export const Navbar: React.FC<NavbarProps> = ({
  latestRelease,
  theme = 'dark',
  onToggleTheme,
}) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [scrollProgress, setScrollProgress] = useState(0);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('home');

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 16);
      const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
      setScrollProgress(maxScroll > 0 ? Math.min(1, Math.max(0, window.scrollY / maxScroll)) : 0);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const sections = NAV_LINKS.map((l) => document.getElementById(l.id)).filter(
      (el): el is HTMLElement => el !== null
    );
    if (sections.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveSection(entry.target.id);
          }
        }
      },
      { rootMargin: '-35% 0px -55% 0px', threshold: 0.01 }
    );

    sections.forEach((sec) => observer.observe(sec));
    return () => observer.disconnect();
  }, []);

  const closeMenu = () => setMobileMenuOpen(false);

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-200 ${
        isScrolled
          ? 'bg-fleet-navy/95 backdrop-blur-md border-b border-white/10 shadow-lg py-2.5'
          : 'bg-fleet-navy/85 backdrop-blur-sm border-b border-white/[0.06] py-3.5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between gap-4">
          {/* Brand */}
          <a
            href="#home"
            className="flex items-center gap-2.5 group focus:outline-none focus-visible:ring-2 focus-visible:ring-fleet-cyan rounded-xl shrink-0"
          >
            <img
              src={logoUrl}
              alt="FleetX Logo"
              className="w-9 h-9 rounded-xl border border-white/15 bg-white p-0.5 object-cover transition-transform duration-150 group-hover:scale-105 motion-reduce:group-hover:scale-100"
            />
            <div className="flex flex-col">
              <span className="font-display text-lg font-bold tracking-tight text-fleet-offwhite leading-none">
                Fleet<span className="text-fleet-cyan">X</span>
              </span>
              <span className="font-mono text-[10px] tracking-wider uppercase text-fleet-muted mt-0.5">
                Campus Mobility
              </span>
            </div>
          </a>

          {/* Desktop Navigation with Active Indicator */}
          <nav aria-label="Primary" className="hidden lg:flex items-center gap-1">
            {NAV_LINKS.map((link) => {
              const isActive = activeSection === link.id;
              return (
                <a
                  key={link.id}
                  href={link.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={`relative px-3.5 py-2 text-sm font-medium rounded-lg transition-colors duration-150 ${
                    isActive
                      ? 'text-fleet-offwhite bg-white/[0.08]'
                      : 'text-fleet-muted hover:text-fleet-offwhite hover:bg-white/[0.05]'
                  }`}
                >
                  <span>{link.label}</span>
                  {isActive && (
                    <span
                      aria-hidden="true"
                      className="absolute inset-x-3 bottom-1 h-0.5 rounded-full bg-fleet-cyan"
                    />
                  )}
                </a>
              );
            })}
          </nav>

          {/* Desktop Actions */}
          <div className="hidden md:flex items-center gap-2 shrink-0">
            {onToggleTheme && (
              <button
                type="button"
                onClick={onToggleTheme}
                aria-label={
                  theme === 'dark' ? 'Switch to Light Black & White mode' : 'Switch to Dark mode'
                }
                title={theme === 'dark' ? 'Light (B&W)' : 'Dark Mode'}
                className="inline-flex items-center gap-1.5 rounded-xl border border-white/15 bg-white/[0.04] px-2.5 py-2 text-xs font-semibold text-fleet-offwhite hover:bg-white/10 transition-colors"
              >
                {theme === 'dark' ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
                <span className="hidden xl:inline">{theme === 'dark' ? 'B&W' : 'Dark'}</span>
              </button>
            )}

            <Link
              to="/submit-stop"
              className="inline-flex items-center gap-1.5 rounded-xl border border-white/15 bg-white/[0.04] px-3.5 py-2 text-xs font-semibold text-fleet-offwhite hover:bg-white/10 transition-colors"
            >
              <MapPin className="w-3.5 h-3.5 text-fleet-cyan" />
              <span>Submit Stop</span>
            </Link>

            <a
              href="#download"
              className="inline-flex items-center gap-2 rounded-xl bg-fleet-lime px-4 py-2 text-xs font-bold text-fleet-navy shadow-sm hover:bg-fleet-lime/90 transition-all duration-150 active:scale-[0.98]"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download App</span>
              {latestRelease && (
                <span className="rounded bg-fleet-navy/15 px-1.5 py-0.5 font-mono text-[10px]">
                  v{latestRelease.version}
                </span>
              )}
            </a>
          </div>

          {/* Mobile & Tablet Controls */}
          <div className="flex lg:hidden items-center gap-2">
            {onToggleTheme && (
              <button
                type="button"
                onClick={onToggleTheme}
                aria-label={
                  theme === 'dark' ? 'Switch to Light Black & White mode' : 'Switch to Dark mode'
                }
                className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-white/15 bg-white/[0.04] text-fleet-offwhite hover:bg-white/10 transition-colors"
              >
                {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
              </button>
            )}
            <a
              href="#download"
              aria-label="Download App"
              className="md:hidden inline-flex h-9 w-9 items-center justify-center rounded-xl bg-fleet-lime text-fleet-navy"
            >
              <Download className="w-4 h-4" />
            </a>
            <button
              type="button"
              onClick={() => setMobileMenuOpen((v) => !v)}
              aria-expanded={mobileMenuOpen}
              aria-label="Toggle navigation menu"
              className="p-2 rounded-xl text-fleet-offwhite hover:bg-white/10 transition-colors"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* Mobile & Tablet Drawer */}
        {mobileMenuOpen && (
          <nav
            aria-label="Mobile navigation"
            className="lg:hidden mt-3 rounded-2xl border border-white/15 bg-[#0B1220] p-3 shadow-2xl animate-in fade-in slide-in-from-top-2 duration-200 motion-reduce:animate-none"
          >
            <div className="flex flex-col gap-1">
              {NAV_LINKS.map((link) => {
                const isActive = activeSection === link.id;
                return (
                  <a
                    key={link.id}
                    href={link.href}
                    onClick={closeMenu}
                    className={`px-3.5 py-2.5 text-sm font-medium rounded-xl transition-colors ${
                      isActive
                        ? 'bg-white/10 text-fleet-cyan font-semibold'
                        : 'text-fleet-offwhite/90 hover:bg-white/[0.06]'
                    }`}
                  >
                    {link.label}
                  </a>
                );
              })}
              <div className="my-2 border-t border-white/10 pt-2 flex flex-col gap-1.5">
                <Link
                  to="/submit-stop"
                  onClick={closeMenu}
                  className="flex items-center gap-2 px-3.5 py-2.5 text-sm font-semibold text-fleet-cyan hover:bg-white/[0.06] rounded-xl transition-colors"
                >
                  <MapPin className="w-4 h-4" />
                  Submit Stop Location
                </Link>
                <Link
                  to="/admin/login"
                  onClick={closeMenu}
                  className="flex items-center gap-2 px-3.5 py-2.5 text-sm font-medium text-fleet-muted hover:bg-white/[0.06] rounded-xl transition-colors"
                >
                  <Shield className="w-4 h-4" />
                  Admin Console
                </Link>
              </div>
              <a
                href="#download"
                onClick={closeMenu}
                className="mt-1 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-fleet-lime py-3 text-sm font-bold text-fleet-navy"
              >
                <Download className="w-4 h-4" />
                Download Latest APK {latestRelease ? `(v${latestRelease.version})` : ''}
              </a>
            </div>
          </nav>
        )}
      </div>

      {/* Scroll Progress Transit Line */}
      <div
        aria-hidden="true"
        className="absolute bottom-0 left-0 right-0 h-[2px] bg-transparent pointer-events-none"
      >
        <div
          className="h-full origin-left bg-fleet-cyan transition-transform duration-75"
          style={{ transform: `scaleX(${scrollProgress})` }}
        />
      </div>
    </header>
  );
};
