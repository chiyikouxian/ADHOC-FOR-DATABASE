import { animate, stagger, createTimeline } from 'animejs'

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function useEntrance(selector, options = {}) {
  if (prefersReducedMotion) {
    document.querySelectorAll(selector).forEach(el => { el.style.opacity = '1' })
    return
  }
  animate(selector, {
    opacity: [0, 1],
    translateY: [options.y ?? 20, 0],
    delay: stagger(options.staggerMs ?? 60),
    duration: options.duration ?? 500,
    ease: 'outExpo'
  })
}

export function useTimelineEntrance(steps) {
  if (prefersReducedMotion) {
    steps.forEach(s => {
      document.querySelectorAll(s.target).forEach(el => { el.style.opacity = '1' })
    })
    return
  }
  const tl = createTimeline({ defaults: { duration: 500, ease: 'outExpo' } })
  steps.forEach((s, i) => {
    tl.add(s.target, {
      opacity: [0, 1],
      translateY: [s.y ?? 20, 0],
      scale: [s.scale ?? 1, 1],
      delay: s.stagger ? stagger(s.stagger) : 0,
    }, i === 0 ? 0 : '-=300')
  })
  return tl
}

export function useHoverScale(el) {
  if (prefersReducedMotion) return
  el.addEventListener('mouseenter', () => {
    animate(el, { scale: 1.03, duration: 250, ease: 'outBack' })
  })
  el.addEventListener('mouseleave', () => {
    animate(el, { scale: 1, duration: 300, ease: 'outExpo' })
  })
}

export function usePulse(selector) {
  if (prefersReducedMotion) return
  animate(selector, {
    scale: [1, 1.4, 1],
    opacity: [1, 0.6, 1],
    duration: 1500,
    loop: true,
    ease: 'inOutSine'
  })
}

export { animate, stagger, createTimeline }
