import { useEffect, useRef } from 'react';
import { animate, set, stagger } from 'animejs';

/**
 * Triggers a smooth, compositor-friendly Anime.js entrance stagger when the container enters the viewport.
 * Respects `prefers-reduced-motion: reduce`.
 */
export function useScrollStagger<T extends HTMLElement>(
  selector = '[data-reveal]',
  delayMs = 50,
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

    set(targets, { opacity: 0, translateY: 16, scale: 0.985 });

    let hasAnimated = false;
    const triggerReveal = () => {
      if (hasAnimated) return;
      hasAnimated = true;
      animate(targets, {
        opacity: [0, 1],
        translateY: [16, 0],
        scale: [0.985, 1],
        duration: 360,
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
      { threshold: 0.08, rootMargin: '0px 0px -20px 0px' }
    );

    observer.observe(container);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selector, delayMs, ...deps]);

  return containerRef;
}

/**
 * Draws an SVG route path smoothly on mount / viewport entry.
 */
export function useRoutePathDraw(pathRef: React.RefObject<SVGPathElement | null>) {
  useEffect(() => {
    const path = pathRef.current;
    if (!path) return;

    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) return;

    const length = path.getTotalLength();
    path.style.strokeDasharray = `${length}`;
    path.style.strokeDashoffset = `${length}`;

    animate(path, {
      strokeDashoffset: [length, 0],
      duration: 900,
      ease: 'outExpo',
      delay: 150,
    });
  }, [pathRef]);
}
