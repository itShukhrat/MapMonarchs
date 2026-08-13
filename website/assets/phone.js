/* Real-app phone showcase — a 3D device rendered in the site's board grammar.
   The phone floats and turns with scroll; real closed-testing screens (status
   bar cropped out) crossfade on its screen with a light-sweep on each change.
   No mockups. Falls back to a clean still image when 3D isn't available. */
(function () {
  "use strict";
  var pin = document.getElementById("showpin");
  if (!pin) return;

  var phoneEl = pin.querySelector(".phone");
  var imgs = Array.prototype.slice.call(pin.querySelectorAll(".screen img"));
  var steps = Array.prototype.slice.call(pin.querySelectorAll(".showstep"));
  var N = steps.length;
  var srcs = imgs.map(function (im) { return im.getAttribute("src"); });

  function markSteps(i) {
    steps.forEach(function (s) { s.classList.toggle("on", +s.getAttribute("data-i") === i); });
  }

  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }

  /* -------- fallback: clean DOM crossfade of the cropped stills -------- */
  function domFallback() {
    function show(i) {
      imgs.forEach(function (im) { im.classList.toggle("on", +im.getAttribute("data-i") === i); });
      markSteps(i);
    }
    show(0);
    if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
      gsap.registerPlugin(ScrollTrigger);
      ScrollTrigger.create({ trigger: pin, start: "top top", end: "bottom bottom",
        onUpdate: function (s) { show(Math.min(N - 1, Math.floor(s.progress * (N - 0.001)))); } });
    }
  }

  if (reduce || !webglOK() || typeof THREE === "undefined" || !phoneEl) { domFallback(); return; }

  /* -------- 3D device -------- */
  phoneEl.classList.add("gl");
  var host = document.createElement("div");
  host.className = "phone-gl";
  phoneEl.appendChild(host);

  var ASPECT = 1080 / 2214;            // cropped screenshot ratio (w/h)
  var Ws = 2.0, Hs = Ws / ASPECT;      // screen size in world units
  var frame = 0.11, depth = 0.17, rad = 0.30;
  var Wb = Ws + frame * 2, Hb = Hs + frame * 2;

  function roundedRect(w, h, r) {
    var s = new THREE.Shape(), x = -w / 2, y = -h / 2;
    s.moveTo(x + r, y);
    s.lineTo(x + w - r, y); s.quadraticCurveTo(x + w, y, x + w, y + r);
    s.lineTo(x + w, y + h - r); s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    s.lineTo(x + r, y + h); s.quadraticCurveTo(x, y + h, x, y + h - r);
    s.lineTo(x, y + r); s.quadraticCurveTo(x, y, x + r, y);
    return s;
  }
  function normalizeUV(geo, w, h) {
    var p = geo.attributes.position, uv = new Float32Array(p.count * 2);
    for (var i = 0; i < p.count; i++) {
      uv[i * 2] = (p.getX(i) + w / 2) / w;
      uv[i * 2 + 1] = (p.getY(i) + h / 2) / h;
    }
    geo.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
  }

  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  var camera = new THREE.PerspectiveCamera(30, 1, 0.1, 100);
  camera.position.set(0, 0, 9.6);

  scene.add(new THREE.AmbientLight(0x3a4256, 1.0));
  var key = new THREE.DirectionalLight(0xbcd4ff, 1.5); key.position.set(-4, 6, 8); scene.add(key);
  var warm = new THREE.PointLight(0xffb066, 6, 30); warm.position.set(3.5, -2, 4); scene.add(warm);
  var rim = new THREE.DirectionalLight(0x6f8cff, 0.8); rim.position.set(5, -3, -6); scene.add(rim);

  var group = new THREE.Group(); scene.add(group);

  // device body
  var bodyGeo = new THREE.ExtrudeGeometry(roundedRect(Wb, Hb, rad),
    { depth: depth, bevelEnabled: true, bevelThickness: 0.05, bevelSize: 0.05, bevelSegments: 3, curveSegments: 16 });
  bodyGeo.translate(0, 0, -depth / 2);
  var body = new THREE.Mesh(bodyGeo, new THREE.MeshStandardMaterial({ color: 0x0a0d13, metalness: 0.72, roughness: 0.34 }));
  group.add(body);

  // screens (A = current, B = incoming for crossfade)
  var loader = new THREE.TextureLoader();
  var maxAniso = renderer.capabilities.getMaxAnisotropy();
  var tex = srcs.map(function (src) {
    var t = loader.load(src); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = maxAniso;
    t.minFilter = THREE.LinearMipmapLinearFilter; t.generateMipmaps = true; return t;
  });
  var scrGeo = new THREE.ShapeGeometry(roundedRect(Ws, Hs, rad - frame), 16); normalizeUV(scrGeo, Ws, Hs);
  var scrA = new THREE.Mesh(scrGeo, new THREE.MeshBasicMaterial({ map: tex[0], side: THREE.DoubleSide }));
  var scrB = new THREE.Mesh(scrGeo, new THREE.MeshBasicMaterial({ map: tex[0], transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false }));
  scrA.position.z = depth / 2 + 0.05; scrB.position.z = depth / 2 + 0.058;
  group.add(scrA); group.add(scrB);

  // camera dot on the bezel (subtle, replaces the removed status bar)
  var dot = new THREE.Mesh(new THREE.CircleGeometry(0.035, 24), new THREE.MeshStandardMaterial({ color: 0x05070b, metalness: 0.5, roughness: 0.3 }));
  dot.position.set(0, Hs / 2 + frame * 0.4, depth / 2 + 0.008); group.add(dot);

  // light-sweep bar (board grammar: a scan crossing the screen on each change)
  var sweep = new THREE.Mesh(new THREE.PlaneGeometry(0.5, Hs * 1.05),
    new THREE.MeshBasicMaterial({ color: 0x8fb4ff, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false }));
  sweep.position.z = depth / 2 + 0.075; sweep.rotation.z = 0.14; group.add(sweep);

  function resize() {
    var w = phoneEl.clientWidth, h = phoneEl.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  resize();
  window.addEventListener("resize", resize, { passive: true });

  // transition state
  var cur = 0, xfade = 0, xactive = false, sweepT = 0;
  function goto(i) {
    if (i === cur) return;
    cur = i; markSteps(i);
    scrB.material.map = tex[i]; scrB.material.needsUpdate = true;
    xfade = 0; xactive = true; sweepT = 0;
  }
  markSteps(0);

  var scrollYaw = 0, targetYaw = 0;
  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: pin, start: "top top", end: "bottom bottom",
      onUpdate: function (s) {
        goto(Math.min(N - 1, Math.floor(s.progress * (N - 0.001))));
        targetYaw = (s.progress - 0.5) * 0.55;    // gentle turntable across the pin
      } });
  }

  var running = true, t0 = performance.now(), last = t0;
  function loop(now) {
    if (!running) return;
    var t = (now - t0) / 1000, dt = Math.min(0.05, (now - last) / 1000); last = now;

    scrollYaw += (targetYaw - scrollYaw) * 0.06;
    group.rotation.y = scrollYaw + Math.sin(t * 0.5) * 0.10;
    group.rotation.x = -0.04 + Math.sin(t * 0.42) * 0.026;
    group.position.y = Math.sin(t * 0.8) * 0.07;

    if (xactive) {
      xfade += dt / 0.5;                 // ~0.5s crossfade
      scrB.material.opacity = xfade < 1 ? xfade : 1;
      sweepT += dt / 0.62;
      var st = sweepT < 1 ? sweepT : 1;
      sweep.position.x = -Ws * 0.75 + st * Ws * 1.5;
      sweep.material.opacity = Math.sin(st * Math.PI) * 0.5;
      var pulse = 1 + Math.sin(Math.min(1, xfade) * Math.PI) * 0.02;
      group.scale.setScalar(pulse);
      if (xfade >= 1 && sweepT >= 1) {
        scrA.material.map = tex[cur]; scrA.material.needsUpdate = true;
        scrB.material.opacity = 0; sweep.material.opacity = 0;
        group.scale.setScalar(1); xactive = false;
      }
    }
    renderer.render(scene, camera);
    requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);

  // pause when tab hidden or section off-screen (perf; two WebGL contexts on page)
  function setRun(v) { if (v && !running) { running = true; t0 = performance.now() - 0; last = performance.now(); requestAnimationFrame(loop); } else if (!v) { running = false; } }
  document.addEventListener("visibilitychange", function () { setRun(!document.hidden); });
  if ("IntersectionObserver" in window) {
    new IntersectionObserver(function (es) { setRun(es[0].isIntersecting && !document.hidden); }, { rootMargin: "200px" }).observe(pin);
  }
})();
