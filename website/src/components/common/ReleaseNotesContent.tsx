import React from 'react';

interface ReleaseNotesContentProps {
  markdown: string;
  className?: string;
}

function renderInline(text: string): React.ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, idx) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return (
        <strong key={idx} className="font-semibold text-fleet-navy">
          {part.slice(2, -2)}
        </strong>
      );
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return (
        <code
          key={idx}
          className="rounded bg-slate-200/80 px-1.5 py-0.5 font-mono text-[11px] text-slate-800"
        >
          {part.slice(1, -1)}
        </code>
      );
    }
    return <React.Fragment key={idx}>{part}</React.Fragment>;
  });
}

export const ReleaseNotesContent: React.FC<ReleaseNotesContentProps> = ({
  markdown,
  className = '',
}) => {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const blocks: React.ReactNode[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) {
      i += 1;
      continue;
    }

    if (trimmed.startsWith('```')) {
      const codeLines: string[] = [];
      i += 1;
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(lines[i]);
        i += 1;
      }
      i += 1;
      blocks.push(
        <pre
          key={`code-${i}`}
          className="rounded-lg bg-slate-900 p-3 font-mono text-[11px] text-slate-100 overflow-x-auto"
        >
          {codeLines.join('\n')}
        </pre>
      );
      continue;
    }

    if (/^#{1,4}\s+/.test(trimmed)) {
      const headingText = trimmed.replace(/^#{1,4}\s+/, '');
      blocks.push(
        <h4
          key={`h-${i}`}
          className="font-display text-xs sm:text-sm font-bold text-fleet-navy pt-1"
        >
          {renderInline(headingText)}
        </h4>
      );
      i += 1;
      continue;
    }

    if (/^[-*]\s+/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''));
        i += 1;
      }
      blocks.push(
        <ul key={`ul-${i}`} className="list-disc pl-5 space-y-1 text-xs text-slate-700">
          {items.map((item, idx) => (
            <li key={idx}>{renderInline(item)}</li>
          ))}
        </ul>
      );
      continue;
    }

    blocks.push(
      <p key={`p-${i}`} className="text-xs text-slate-700 leading-relaxed">
        {renderInline(trimmed)}
      </p>
    );
    i += 1;
  }

  return (
    <div
      className={`space-y-2.5 rounded-xl border border-slate-200 bg-slate-50 p-4 max-h-64 overflow-y-auto ${className}`}
    >
      {blocks}
    </div>
  );
};
