import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  label?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  label = 'Loading operational data...',
  size = 'md',
  className = '',
}) => {
  const sizeMap = {
    sm: 'w-4 h-4',
    md: 'w-6 h-6',
    lg: 'w-8 h-8',
  };

  return (
    <div className={`flex flex-col items-center justify-center p-8 gap-3 text-fleet-muted ${className}`}>
      <Loader2 className={`${sizeMap[size]} animate-spin text-fleet-cyan`} />
      {label && <p className="text-xs font-medium text-fleet-muted">{label}</p>}
    </div>
  );
};
