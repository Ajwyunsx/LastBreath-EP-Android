(() => {
  'use strict';

  const root = document.createElement('div');
  root.id = 'mobile-controls';
  root.setAttribute('aria-label', 'Touch controls');
  root.innerHTML = `
    <div id="mc-joystick" aria-label="Move">
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <polygon points="30,2 70,2 70,6 86,6 86,14 94,14 94,30 98,30 98,70 94,70 94,86 86,86 86,94 70,94 70,98 30,98 30,94 14,94 14,86 6,86 6,70 2,70 2,30 6,30 6,14 14,14 14,6 30,6"
          fill="none" stroke="rgba(255,255,255,0.9)" stroke-width="1.5" shape-rendering="crispEdges"/>
      </svg>
      <div id="mc-knob"></div>
    </div>
    <div id="mc-actions">
      <button class="mc-button" data-key="c" aria-label="Menu">C</button>
      <button class="mc-button" data-key="x" aria-label="Cancel">X</button>
      <button class="mc-button" data-key="z" aria-label="Confirm">Z</button>
    </div>`;
  document.body.appendChild(root);

  const joystick = root.querySelector('#mc-joystick');
  const knob = root.querySelector('#mc-knob');
  const pressed = new Set();
  const directionPointers = new Map();
  const buttonPointers = new Map();

  const aliases = {
    ArrowUp: ['ArrowUp', 'w'], ArrowDown: ['ArrowDown', 's'],
    ArrowLeft: ['ArrowLeft', 'a'], ArrowRight: ['ArrowRight', 'd'],
    z: ['z', 'space']
  };
  const names = {
    ArrowUp: ['ArrowUp', 38], ArrowDown: ['ArrowDown', 40],
    ArrowLeft: ['ArrowLeft', 37], ArrowRight: ['ArrowRight', 39],
    w: ['KeyW', 87], a: ['KeyA', 65], s: ['KeyS', 83], d: ['KeyD', 68],
    z: ['KeyZ', 90], x: ['KeyX', 88], c: ['KeyC', 67], space: ['Space', 32]
  };

  const postKey = (key, isDown) => {
    if (isDown === pressed.has(key)) return;
    if (isDown) pressed.add(key);
    else pressed.delete(key);

    for (const mappedKey of aliases[key] || [key]) {
      const event = new KeyboardEvent(isDown ? 'keydown' : 'keyup', {
        key: mappedKey === 'space' ? ' ' : mappedKey,
        code: names[mappedKey][0],
        keyCode: names[mappedKey][1],
        which: names[mappedKey][1],
        bubbles: true,
        cancelable: true
      });
      Object.defineProperty(event, 'keyCode', { value: names[mappedKey][1] });
      Object.defineProperty(event, 'which', { value: names[mappedKey][1] });
      document.dispatchEvent(event);
    }
  };

  const releaseAll = () => {
    for (const key of Array.from(pressed)) postKey(key, false);
    directionPointers.clear();
    buttonPointers.clear();
    knob.style.transform = 'translate(-50%, -50%)';
    root.querySelectorAll('.mc-active').forEach(button => button.classList.remove('mc-active'));
  };

  const updateJoystick = event => {
    const rect = joystick.getBoundingClientRect();
    const radius = rect.width * 0.32;
    let dx = event.clientX - (rect.left + rect.width / 2);
    let dy = event.clientY - (rect.top + rect.height / 2);
    const distance = Math.hypot(dx, dy);
    if (distance > radius) {
      dx *= radius / distance;
      dy *= radius / distance;
    }
    knob.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;

    const threshold = radius * 0.32;
    const active = directionPointers.get(event.pointerId);
    active.keys = {
      ArrowLeft: dx < -threshold,
      ArrowRight: dx > threshold,
      ArrowUp: dy < -threshold,
      ArrowDown: dy > threshold
    };
    updateDirections();
  };

  const updateDirections = () => {
    for (const key of ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown']) {
      let down = false;
      for (const pointer of directionPointers.values()) {
        if (pointer.keys[key]) down = true;
      }
      postKey(key, down);
    }
  };

  joystick.addEventListener('pointerdown', event => {
    event.preventDefault();
    joystick.setPointerCapture(event.pointerId);
    directionPointers.set(event.pointerId, { keys: {} });
    updateJoystick(event);
  });
  joystick.addEventListener('pointermove', event => {
    if (!directionPointers.has(event.pointerId)) return;
    event.preventDefault();
    updateJoystick(event);
  });

  const releaseJoystick = event => {
    if (!directionPointers.delete(event.pointerId)) return;
    updateDirections();
    if (directionPointers.size === 0) knob.style.transform = 'translate(-50%, -50%)';
  };
  joystick.addEventListener('pointerup', releaseJoystick);
  joystick.addEventListener('pointercancel', releaseJoystick);
  joystick.addEventListener('lostpointercapture', releaseJoystick);

  root.querySelectorAll('.mc-button').forEach(button => {
    button.addEventListener('pointerdown', event => {
      event.preventDefault();
      button.setPointerCapture(event.pointerId);
      buttonPointers.set(event.pointerId, button.dataset.key);
      button.classList.add('mc-active');
      postKey(button.dataset.key, true);
    });
    const release = event => {
      const key = buttonPointers.get(event.pointerId);
      if (!key) return;
      buttonPointers.delete(event.pointerId);
      if (!Array.from(buttonPointers.values()).includes(key)) postKey(key, false);
      button.classList.toggle('mc-active', Array.from(buttonPointers.values()).includes(key));
    };
    button.addEventListener('pointerup', release);
    button.addEventListener('pointercancel', release);
    button.addEventListener('lostpointercapture', release);
    button.addEventListener('contextmenu', event => event.preventDefault());
  });

  window.addEventListener('blur', releaseAll);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) releaseAll();
  });
  window.mobileControls = { postKey, releaseAll, pressed };
})();
