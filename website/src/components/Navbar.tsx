import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Download, Menu, X, ArrowUpRight, MapPin, Shield } from 'lucide-react';
import logoUrl from '../assets/logo/fleetx-logo.png';
import { ReleaseInfo } from '../types/github';

interface NavbarProps {
  latestRelease: ReleaseInfo | null;
}

export const Navbar: React.FC<NavbarProps> = ({ latestRelease }) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { label: 'Home', href: '#home' },
    { label: 'Features', href: '#features' },
    { label: 'How It Works', href: '#how-it-works' },
    { label: 'App Preview', href: '#preview' },
    { label: 'Download', href: '#download' },
    { label: 'Releases', href: '#releases' },
    { label: 'About', href: '#about' },
  ];

  const handleNavClick = () => {
    setMobileMenuOpen(false);
  };

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        isScrolled
          ? 'bg-white/95 backdrop-blur-md shadow-sm border-b border-slate-200/80 py-3'
          : 'bg-college-surface/90 backdrop-blur-sm py-4'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <a
            href="#home"
            className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-college-primary rounded-xl"
          >
            <img
              src={logoUrl}
              alt="FleetX Logo"
              className="w-10 h-10 rounded-xl shadow-sm border border-slate-200/60 object-cover group-hover:scale-105 transition-transform duration-200"
            />
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <span className="text-xl font-bold tracking-tight text-college-on-surface">
                  Fleet<span className="text-college-primary">X</span>
                </span>
                <span className="text-[10px] font-semibold uppercase tracking-wider bg-college-primary-container text-college-primary-dark px-2 py-0.5 rounded-full">
                  Official
                </span>
              </div>
              <span className="text-xs text-college-on-surface-variant font-medium">
                College Bus Tracker
              </span>
            </div>
          </a>

          {/* Desktop Nav Items */}
          <nav className="hidden md:flex items-center gap-1 lg:gap-2">
            {navLinks.map(link => (
              <a
                key={link.label}
                href={link.href}
                className="px-3 py-2 text-sm font-medium text-college-on-surface-variant hover:text-college-primary hover:bg-slate-100/70 rounded-lg transition-colors"
              >
                {link.label}
              </a>
            ))}
          </nav>

          {/* Actions & Download CTA */}
          <div className="hidden sm:flex items-center gap-2 lg:gap-3">
            <Link
              to="/submit-stop"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-college-primary hover:text-college-primary-dark hover:bg-blue-50/80 px-3 py-2 rounded-xl border border-blue-200/60 transition-colors"
            >
              <MapPin className="w-3.5 h-3.5" />
              <span>Submit Stop</span>
            </Link>

            <a
              href="#download"
              className="inline-flex items-center gap-2 bg-college-primary hover:bg-college-primary-dark active:bg-blue-800 text-white font-medium text-sm px-4 py-2.5 rounded-xl shadow-sm hover:shadow transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-college-primary"
            >
              <Download className="w-4 h-4" />
              <span>Download App</span>
              {latestRelease && (
                <span className="text-[11px] bg-white/20 text-white px-1.5 py-0.5 rounded-md font-mono">
                  v{latestRelease.version}
                </span>
              )}
            </a>
          </div>

          {/* Mobile Menu Toggle Button */}
          <div className="flex md:hidden items-center gap-2">
            <a
              href="#download"
              className="inline-flex items-center justify-center p-2 rounded-lg bg-college-primary text-white text-sm"
              aria-label="Download App"
            >
              <Download className="w-4 h-4" />
            </a>
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 text-college-on-surface-variant hover:text-college-on-surface hover:bg-slate-100 rounded-lg transition-colors"
              aria-label="Toggle Navigation Menu"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>

        {/* Mobile Dropdown Menu */}
        {mobileMenuOpen && (
          <div className="md:hidden mt-3 pt-3 border-t border-slate-200 pb-4 animate-in fade-in duration-200">
            <div className="flex flex-col gap-1">
              {navLinks.map(link => (
                <a
                  key={link.label}
                  href={link.href}
                  onClick={handleNavClick}
                  className="px-4 py-2.5 text-base font-medium text-college-on-surface hover:bg-slate-100 rounded-lg transition-colors"
                >
                  {link.label}
                </a>
              ))}
              <div className="pt-2 border-t border-slate-200/80 flex flex-col gap-1.5">
                <Link
                  to="/submit-stop"
                  onClick={handleNavClick}
                  className="flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-college-primary hover:bg-blue-50 rounded-lg transition-colors"
                >
                  <MapPin className="w-4 h-4" />
                  <span>Submit Stop Location</span>
                </Link>
                <Link
                  to="/admin/login"
                  onClick={handleNavClick}
                  className="flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  <Shield className="w-4 h-4" />
                  <span>Admin Console</span>
                </Link>
              </div>

              <div className="mt-2 px-3">
                <a
                  href="#download"
                  onClick={handleNavClick}
                  className="w-full flex items-center justify-center gap-2 bg-college-primary text-white py-3 rounded-xl font-medium text-sm shadow-sm"
                >
                  <Download className="w-4 h-4" />
                  <span>Download Latest APK (v{latestRelease?.version || '1.0'})</span>
                  <ArrowUpRight className="w-4 h-4 opacity-70" />
                </a>
              </div>
            </div>
          </div>
        )}
      </div>
    </header>
  );
};
