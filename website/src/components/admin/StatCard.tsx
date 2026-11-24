import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight } from 'lucide-react';

export type StatCardColor = 'blue' | 'emerald' | 'amber' | 'indigo' | 'rose' | 'purple';

interface StatCardProps {
  title: string;
  value: number | string;
  subtitle?: string;
  icon: React.ComponentType<{ className?: string }>;
  color?: StatCardColor;
  linkTo?: string;
  linkLabel?: string;
  isLoading?: boolean;
}

const colorStyles: Record<StatCardColor, { bg: string; iconBg: string; text: string; ring: string }> = {
  blue: {
    bg: 'bg-white',
    iconBg: 'bg-blue-50 text-blue-600',
    text: 'text-blue-600',
    ring: 'hover:border-blue-300',
  },
  emerald: {
    bg: 'bg-white',
    iconBg: 'bg-emerald-50 text-emerald-600',
    text: 'text-emerald-600',
    ring: 'hover:border-emerald-300',
  },
  amber: {
    bg: 'bg-white',
    iconBg: 'bg-amber-50 text-amber-600',
    text: 'text-amber-600',
    ring: 'hover:border-amber-300',
  },
  indigo: {
    bg: 'bg-white',
    iconBg: 'bg-indigo-50 text-indigo-600',
    text: 'text-indigo-600',
    ring: 'hover:border-indigo-300',
  },
  rose: {
    bg: 'bg-white',
    iconBg: 'bg-rose-50 text-rose-600',
    text: 'text-rose-600',
    ring: 'hover:border-rose-300',
  },
  purple: {
    bg: 'bg-white',
    iconBg: 'bg-purple-50 text-purple-600',
    text: 'text-purple-600',
    ring: 'hover:border-purple-300',
  },
};

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  color = 'blue',
  linkTo,
  linkLabel,
  isLoading = false,
}) => {
  const styles = colorStyles[color];

  const content = (
    <div
      className={`relative p-5 rounded-2xl border border-slate-200/80 shadow-sm transition-all duration-200 ${styles.bg} ${
        linkTo ? `group hover:shadow-md ${styles.ring}` : ''
      }`}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            {title}
          </p>
          {isLoading ? (
            <div className="h-8 w-16 bg-slate-200 rounded-lg animate-pulse my-1" />
          ) : (
            <h3 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
              {value}
            </h3>
          )}
          {subtitle && (
            <p className="text-xs text-slate-500 font-medium">
              {subtitle}
            </p>
          )}
        </div>

        <div className={`p-3 rounded-2xl ${styles.iconBg} transition-transform group-hover:scale-105`}>
          <Icon className="w-5 h-5" />
        </div>
      </div>

      {linkTo && (
        <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs font-semibold text-slate-600 group-hover:text-blue-600 transition-colors">
          <span>{linkLabel || 'View details'}</span>
          <ArrowUpRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform" />
        </div>
      )}
    </div>
  );

  if (linkTo) {
    return <Link to={linkTo} className="block">{content}</Link>;
  }

  return content;
};
