import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Download, Menu, X, MapPin, Shield } from 'lucide-react';
import logoUrl from '../assets/logo/fleetx-logo.png';
import { ReleaseInfo } from '../types/github';
import { Button } from './ui/button';

interface NavbarProps {
  latestRelease: ReleaseInfo | null;
}

export const Navbar: React.FC<NavbarProps> = ({ latestRelease }) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 16);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { label: 'Home', href: '#home' },
    { label: 'Features', href: '#features' },
    { label: 'How It Works', href: '#how-it-works' },
    { label: 'Preview', href: '#preview' },
    { label: 'About', href: '#about' },
  ];

  const closeMenu = () => setMobileMenuOpen(false);

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-200 ${
        isScrolled
          ? 'bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-sm py-2.5'
          : 'bg-college-surface/90 backdrop-blur-sm border-b border-transparent py-3.5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between gap-4">
          {/* Brand */}
          <a
            href="#home"
            className="flex items-center gap-2.5 group focus:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl shrink-0"
          >
            <img
              src={logoUrl}
              alt="FleetX Logo"
              className="w-9 h-9 rounded-xl border border-slate-200/80 bg-white object-cover transition-transform duration-150 group-hover:scale-105 motion-reduce:group-hover:scale-100"
            />
            <div className="flex flex-col">
              <span className="text-lg font-extrabold tracking-tight text-slate-900 leading-none">
                Fleet<span className="text-college-primary">X</span>
              </span>
              <span className="text-[11px] font-medium text-slate-500 mt-0.5">
                Campus Transit
              </span>
            </div>
          </a>

          {/* Desktop Navigation */}
          <nav aria-label="Primary" className="hidden lg:flex items-center gap-1">
            {navLinks.map((link) => (
              <a
                key={link.label}
                href={link.href}
                className="px-3 py-2 text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 rounded-lg transition-colors duration-150"
              >
                {link.label}
              </a>
            ))}
          </nav>

          {/* Desktop Actions */}
          <div className="hidden md:flex items-center gap-2.5 shrink-0">
            <Button variant="outline" size="sm" asChild>
              <Link to="/submit-stop">
                <MapPin className="w-3.5 h-3.5 text-college-primary" />
                Submit Stop
              </Link>
            </Button>

            <Button size="sm" asChild>
              <a href="#download">
                <Download className="w-3.5 h-3.5" />
                <span>Download App</span>
                {latestRelease && (
                  <span className="text-[11px] bg-white/20 px-1.5 py-0.5 rounded font-mono">
                    v{latestRelease.version}
                  </span>
                )}
              </a>
            </Button>
          </div>

          {/* Mobile & Tablet Controls */}
          <div className="flex lg:hidden items-center gap-2">
            <Button size="icon" variant="default" asChild aria-label="Download App" className="md:hidden">
              <a href="#download">
                <Download className="w-4 h-4" />
              </a>
            </Button>
            <button
              type="button"
              onClick={() => setMobileMenuOpen((v) => !v)}
              aria-expanded={mobileMenuOpen}
              aria-label="Toggle navigation menu"
              className="p-2 rounded-xl text-slate-700 hover:bg-slate-100 transition-colors"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* Mobile & Tablet Drawer */}
        {mobileMenuOpen && (
          <nav
            aria-label="Mobile navigation"
            className="lg:hidden mt-3 pt-3 pb-4 border-t border-slate-200 animate-in fade-in slide-in-from-top-2 duration-200 motion-reduce:animate-none"
          >
            <div className="flex flex-col gap-1">
              {navLinks.map((link) => (
                <a
                  key={link.label}
                  href={link.href}
                  onClick={closeMenu}
                  className="px-3.5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
                >
                  {link.label}
                </a>
              ))}
              <div className="my-2 border-t border-slate-200/80 pt-2 flex flex-col gap-1.5">
                <Link
                  to="/submit-stop"
                  onClick={closeMenu}
                  className="flex items-center gap-2 px-3.5 py-2.5 text-sm font-semibold text-college-primary hover:bg-blue-50 rounded-xl transition-colors"
                >
                  <MapPin className="w-4 h-4" />
                  Submit Stop Location
                </Link>
                <Link
                  to="/admin/login"
                  onClick={closeMenu}
                  className="flex items-center gap-2 px-3.5 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-xl transition-colors"
                >
                  <Shield className="w-4 h-4" />
                  Admin Console
                </Link>
              </div>
              <Button className="w-full mt-1" asChild>
                <a href="#download" onClick={closeMenu}>
                  <Download className="w-4 h-4" />
                  Download Latest APK {latestRelease ? `(v${latestRelease.version})` : ''}
                </a>
              </Button>
            </div>
          </nav>
        )}
      </div>
    </header>
  );
};
