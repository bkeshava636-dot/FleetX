import React from 'react';
import { Menu, Moon, ShieldCheck, Sun, Wifi, WifiOff } from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';

interface AdminHeaderProps {
  onMenuClick: () => void;
  title?: string;
  theme?: 'dark' | 'light-bw';
  onToggleTheme?: () => void;
}

export const AdminHeader: React.FC<AdminHeaderProps> = ({
  onMenuClick,
  title = 'Administration Console',
  theme = 'dark',
  onToggleTheme,
}) => {
  const { profile } = useAdminAuth();
  const [isOnline, setIsOnline] = React.useState(navigator.onLine);

  React.useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return (
    <header className="h-16 bg-fleet-navy/90 backdrop-blur-md border-b border-white/10 sticky top-0 z-30 px-4 sm:px-6 flex items-center justify-between">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          className="lg:hidden p-2 rounded-lg text-fleet-muted hover:text-fleet-offwhite hover:bg-white/[0.06]"
          aria-label="Open sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div>
          <h1 className="font-display text-base sm:text-lg font-bold text-fleet-offwhite tracking-tight">{title}</h1>
        </div>
      </div>

      <div className="flex items-center gap-2.5 sm:gap-3">
        {onToggleTheme && (
          <button
            type="button"
            onClick={onToggleTheme}
            aria-label={theme === 'dark' ? 'Switch to Light Black & White mode' : 'Switch to Dark mode'}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-fleet-deep px-2.5 py-1 text-[11px] font-semibold text-fleet-offwhite hover:border-fleet-cyan/40 transition-colors"
          >
            {theme === 'dark' ? (
              <>
                <Sun className="w-3.5 h-3.5 text-fleet-lime" />
                <span className="hidden sm:inline">B&amp;W</span>
              </>
            ) : (
              <>
                <Moon className="w-3.5 h-3.5 text-fleet-cyan" />
                <span className="hidden sm:inline">Dark</span>
              </>
            )}
          </button>
        )}

        {/* Network status indicator */}
        <div className="hidden sm:flex items-center gap-1.5 font-mono text-[11px] font-medium text-fleet-offwhite bg-fleet-deep px-2.5 py-1 rounded-full border border-white/10">
          {isOnline ? (
            <>
              <Wifi className="w-3.5 h-3.5 text-fleet-lime" />
              <span>Live Firebase</span>
            </>
          ) : (
            <>
              <WifiOff className="w-3.5 h-3.5 text-amber-400" />
              <span className="text-amber-300">Offline Cache</span>
            </>
          )}
        </div>

        {/* Verified Admin Badge */}
        <div className="flex items-center gap-2 pl-2 border-l border-white/10">
          <div className="flex items-center gap-1.5 font-mono text-xs font-semibold text-fleet-cyan bg-fleet-cyan/10 border border-fleet-cyan/30 px-2.5 py-1 rounded-lg">
            <ShieldCheck className="w-4 h-4 text-fleet-cyan" />
            <span className="hidden sm:inline">{profile?.customId || profile?.name || 'Admin'}</span>
          </div>
        </div>
      </div>
    </header>
  );
};
