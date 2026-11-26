import { useEffect, useRef } from 'react';
import { animate, set, stagger } from 'animejs';

/**
 * Triggers a smooth, compositor-friendly Anime.js entrance stagger when the container enters the viewport.
 * Respects `prefers-reduced-motion: reduce` by leaving elements immediately visible with no spatial motion.
 */
export function useScrollStagger<T extends HTMLElement>(
  selector = '[data-reveal]',
  delayMs = 55,
  deps: React.DependencyList = []
) {
  const containerRef = useRef<T>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (prefersReduced) return;

    const targets = Array.from(container.querySelectorAll<HTMLElement>(selector));
    if (targets.length === 0) return;

    // Pre-hide targets only when motion is enabled so scroll reveals are clearly visible
    set(targets, { opacity: 0, translateY: 18, scale: 0.98 });

    let hasAnimated = false;
    const triggerReveal = () => {
      if (hasAnimated) return;
      hasAnimated = true;
      animate(targets, {
        opacity: [0, 1],
        translateY: [18, 0],
        scale: [0.98, 1],
        duration: 380,
        delay: stagger(delayMs),
        ease: 'outExpo',
      });
    };

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            triggerReveal();
            observer.disconnect();
            break;
          }
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -24px 0px' }
    );

    observer.observe(container);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selector, delayMs, ...deps]);

  return containerRef;
}
