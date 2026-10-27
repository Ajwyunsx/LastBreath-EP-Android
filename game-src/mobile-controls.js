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
    </div>
    <button id="mc-keyboard" type="button" aria-label="Open keyboard" title="Keyboard" hidden>
      <svg viewBox="0 0 32 24" aria-hidden="true" shape-rendering="crispEdges">
        <path d="M2 3h28v18H2z" fill="none" stroke="currentColor" stroke-width="2"/>
        <path d="M6 7h2v2H6zm5 0h2v2h-2zm5 0h2v2h-2zm5 0h2v2h-2zm5 0h2v2h-2zM6 12h2v2H6zm5 0h2v2h-2zm5 0h2v2h-2zm5 0h2v2h-2zm5 0h2v2h-2zM10 17h12v2H10z" fill="currentColor"/>
      </svg>
    </button>
    <button class="mc-button" id="mc-enter" type="button" data-key="Enter" aria-label="Confirm name" hidden>ENTER</button>
    <input id="mc-keyboard-input" type="text" inputmode="text" enterkeyhint="done" autocomplete="off" autocorrect="off" autocapitalize="characters" spellcheck="false" tabindex="-1" aria-hidden="true">`;
  document.body.appendChild(root);

  const joystick = root.querySelector('#mc-joystick');
  const knob = root.querySelector('#mc-knob');
  const keyboardButton = root.querySelector('#mc-keyboard');
  const enterButton = root.querySelector('#mc-enter');
  const keyboardInput = root.querySelector('#mc-keyboard-input');
  const pressed = new Set();
  const directionPointers = new Map();
  const buttonPointers = new Map();
  const inputReleaseTimers = new Map();

  const aliases = {
    ArrowUp: ['ArrowUp', 'w'], ArrowDown: ['ArrowDown', 's'],
    ArrowLeft: ['ArrowLeft', 'a'], ArrowRight: ['ArrowRight', 'd'],
    z: ['z', 'space']
  };
  const names = {
    ArrowUp: ['ArrowUp', 38], ArrowDown: ['ArrowDown', 40],
    ArrowLeft: ['ArrowLeft', 37], ArrowRight: ['ArrowRight', 39],
    Backspace: ['Backspace', 8], Enter: ['Enter', 13],
    w: ['KeyW', 87], a: ['KeyA', 65], s: ['KeyS', 83], d: ['KeyD', 68],
    z: ['KeyZ', 90], x: ['KeyX', 88], c: ['KeyC', 67], space: ['Space', 32]
  };

  const keyDetails = key => {
    if (names[key]) return names[key];
    if (/^[a-z]$/i.test(key)) return [`Key${key.toUpperCase()}`, key.toUpperCase().charCodeAt(0)];
    if (/^[0-9]$/.test(key)) return [`Digit${key}`, key.charCodeAt(0)];
    return [key, 0];
  };

  const dispatchKey = (key, isDown) => {
    if (isDown === pressed.has(key)) return;
    if (isDown) pressed.add(key);
    else pressed.delete(key);

    const details = keyDetails(key);
    const event = new KeyboardEvent(isDown ? 'keydown' : 'keyup', {
      key: key === 'space' ? ' ' : key,
      code: details[0],
      keyCode: details[1],
      which: details[1],
      bubbles: true,
      cancelable: true
    });
    Object.defineProperty(event, 'keyCode', { value: details[1] });
    Object.defineProperty(event, 'which', { value: details[1] });
    document.dispatchEvent(event);
  };

  const postKey = (key, isDown) => {
    for (const mappedKey of aliases[key] || [key]) dispatchKey(mappedKey, isDown);
  };

  const postKeyboardInput = key => {
    if (inputReleaseTimers.has(key)) {
      clearTimeout(inputReleaseTimers.get(key));
      dispatchKey(key, false);
    }
    dispatchKey(key, true);
    inputReleaseTimers.set(key, setTimeout(() => {
      dispatchKey(key, false);
      inputReleaseTimers.delete(key);
    }, 80));
  };

  const releaseAll = () => {
    for (const timer of inputReleaseTimers.values()) clearTimeout(timer);
    inputReleaseTimers.clear();
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

  const hideKeyboard = () => {
    keyboardInput.value = '';
    if (document.activeElement === keyboardInput) keyboardInput.blur();
    if (window.AndroidKeyboard) window.AndroidKeyboard.hide();
  };

  keyboardButton.addEventListener('pointerdown', () => keyboardButton.classList.add('mc-active'));
  for (const type of ['pointerup', 'pointercancel', 'pointerleave']) {
    keyboardButton.addEventListener(type, () => keyboardButton.classList.remove('mc-active'));
  }
  keyboardButton.addEventListener('click', event => {
    event.preventDefault();
    keyboardInput.value = '';
    keyboardInput.focus({ preventScroll: true });
    if (window.AndroidKeyboard) window.AndroidKeyboard.show();
  });
  keyboardButton.addEventListener('contextmenu', event => event.preventDefault());
  keyboardInput.addEventListener('blur', () => {
    if (window.AndroidKeyboard) window.AndroidKeyboard.hide();
  });

  let recentKey = '';
  let recentKeyTime = 0;
  keyboardInput.addEventListener('keydown', event => {
    if (event.key === 'Backspace' || event.key === 'Enter') event.preventDefault();
    if (event.key && event.key.length === 1) {
      recentKey = event.key;
      recentKeyTime = performance.now();
    } else {
      recentKey = '';
    }
  });

  const insertText = text => {
    if (!text) return;
    if (text.length === 1 && text.toLowerCase() === recentKey.toLowerCase() && performance.now() - recentKeyTime < 300) {
      recentKey = '';
      return;
    }
    recentKey = '';
    for (const character of text) postKeyboardInput(character);
  };

  keyboardInput.addEventListener('beforeinput', event => {
    event.preventDefault();
    if (event.inputType === 'insertText') insertText(event.data);
    else if (event.inputType === 'deleteContentBackward') postKeyboardInput('Backspace');
    else if (event.inputType === 'insertLineBreak') postKeyboardInput('Enter');
  });
  keyboardInput.addEventListener('input', () => {
    insertText(keyboardInput.value);
    keyboardInput.value = '';
  });

  let namingVisible = false;
  const updateKeyboardButton = () => {
    const targets = window.vm && window.vm.runtime ? window.vm.runtime.targets : [];
    const visible = targets.some(target => target.sprite && target.sprite.name === '게임//이름짓기' && target.visible);
    if (visible === namingVisible) return;
    namingVisible = visible;
    keyboardButton.hidden = !visible;
    enterButton.hidden = !visible;
    if (!visible) {
      releaseAll();
      hideKeyboard();
    }
  };
  setInterval(updateKeyboardButton, 200);
  updateKeyboardButton();

  window.addEventListener('blur', releaseAll);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      releaseAll();
      hideKeyboard();
    }
  });
  window.mobileControls = { postKey, releaseAll, pressed };
})();
