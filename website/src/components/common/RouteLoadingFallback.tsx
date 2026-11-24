import React from 'react';

export const RouteLoadingFallback: React.FC<{ message?: string }> = ({
  message = 'Loading FleetX module...',
}) => {
  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-6 text-center select-none">
      <div className="w-10 h-10 border-3 border-blue-500/20 border-t-blue-500 rounded-full animate-spin mb-3.5" />
      <span className="text-xs font-semibold tracking-wider text-slate-400 font-mono">
        {message}
      </span>
    </div>
  );
};

export default RouteLoadingFallback;
