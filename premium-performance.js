(() => {
    const root = document.documentElement;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const touchDevice = window.matchMedia('(pointer: coarse)').matches || navigator.maxTouchPoints > 0;

    root.classList.toggle('reduce-motion', reduceMotion);
    root.classList.toggle('premium-lite', touchDevice || reduceMotion);

    const hero = document.querySelector('.hero');
    if (!hero || reduceMotion || touchDevice) return;

    root.classList.add('premium-pointer');
    let frame = 0;
    let x = 0;
    let y = 0;

    const reset = () => {
        hero.style.setProperty('--mx', '0');
        hero.style.setProperty('--my', '0');
    };

    hero.addEventListener('pointermove', event => {
        x = ((event.clientX / Math.max(window.innerWidth, 1)) - 0.5) * 2;
        y = ((event.clientY / Math.max(window.innerHeight, 1)) - 0.5) * 2;
        if (frame) return;
        frame = requestAnimationFrame(() => {
            frame = 0;
            hero.style.setProperty('--mx', x.toFixed(3));
            hero.style.setProperty('--my', y.toFixed(3));
        });
    }, { passive: true });
    hero.addEventListener('pointerleave', reset, { passive: true });
})();
