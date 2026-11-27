import React from 'react';
import { ChevronDown } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';

const FAQS = [
  {
    question: 'What is FleetX?',
    answer:
      'FleetX is a college transportation management platform connecting an Android app for students and drivers with a web-based Admin Console for route, fleet, and schedule management.',
  },
  {
    question: 'Who can use FleetX?',
    answer:
      'College students can use FleetX to view routes, stops, announcements, and live bus locations, while verified drivers and transport administrators manage trips and fleet operations.',
  },
  {
    question: 'Can students track their buses?',
    answer:
      'Yes. When a driver starts tracking on their assigned route, live GPS coordinates stream to the FleetX Android app so students can follow the bus on the map.',
  },
  {
    question: 'Does FleetX support multiple buses?',
    answer:
      'Yes. FleetX manages multiple buses, routes, driver assignments, temporary driver replacements, and bus swaps simultaneously.',
  },
  {
    question: 'How does live tracking work?',
    answer:
      'The driver application sends periodic location updates to Firebase Realtime Database during active transit windows, and the student app renders the bus position with freshness indicators.',
  },
];

export const FaqSection: React.FC = () => {
  const faqRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 45);

  return (
    <section
      id="faq"
      aria-labelledby="faq-heading"
      className="fleetx-faq py-16 sm:py-24 bg-fleet-offwhite border-t border-slate-200/80"
    >
      <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-10">
          <Badge variant="secondary" className="mb-3 font-mono uppercase tracking-wider text-[11px]">
            FAQ
          </Badge>
          <h2 id="faq-heading" className="font-display text-3xl sm:text-4xl font-bold text-fleet-navy tracking-tight">
            Frequently Asked Questions
          </h2>
          <p className="mt-2.5 text-sm text-slate-600">
            Clear answers about how FleetX operates for students and campus transit teams.
          </p>
        </div>

        <div ref={faqRef} className="space-y-3">
          {FAQS.map((item) => (
            <details
              key={item.question}
              data-reveal
              className="group rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden transition-colors hover:border-slate-300"
            >
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 font-display text-sm sm:text-base font-bold text-fleet-navy hover:bg-slate-50/80 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
                <span>{item.question}</span>
                <ChevronDown className="w-4 h-4 text-slate-500 shrink-0 transition-transform duration-200 group-open:rotate-180" />
              </summary>
              <div className="px-5 pb-4 pt-1 text-sm text-slate-600 leading-relaxed border-t border-slate-100">
                {item.answer}
              </div>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
};
