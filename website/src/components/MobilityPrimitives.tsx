import React from 'react';

export const MapGridBackdrop: React.FC<{ dark?: boolean; className?: string }> = ({
  dark = true,
  className = '',
}) => (
  <svg
    aria-hidden="true"
    className={`pointer-events-none absolute inset-0 h-full w-full ${
      dark ? 'text-white/[0.04]' : 'text-slate-900/[0.04]'
    } ${className}`}
  >
    <defs>
      <pattern
        id={dark ? 'fleetx-grid-dark' : 'fleetx-grid-light'}
        width="40"
        height="40"
        patternUnits="userSpaceOnUse"
      >
        <path d="M 40 0 L 0 0 0 40" fill="none" stroke="currentColor" strokeWidth="1" />
      </pattern>
    </defs>
    <rect
      width="100%"
      height="100%"
      fill={`url(#${dark ? 'fleetx-grid-dark' : 'fleetx-grid-light'})`}
    />
  </svg>
);

export const CoordinateTag: React.FC<{ label?: string; className?: string }> = ({
  label = '15.1683° N · 76.8317° E',
  className = '',
}) => (
  <span
    className={`inline-flex items-center gap-1.5 rounded-md border border-white/10 bg-white/[0.04] px-2.5 py-1 font-mono text-[11px] text-fleet-muted ${className}`}
  >
    <span className="h-1.5 w-1.5 rounded-full bg-fleet-cyan" />
    {label}
  </span>
);
