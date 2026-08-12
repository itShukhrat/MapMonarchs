/* /how-it-works — one hexagon changing hands, driven by a pinned scroll.
   A distinct scene from the home board: a close-up lifecycle, not a field. */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("howstage");
  var progress = document.getElementById("progress");
  var cards = Array.prototype.slice.call(document.querySelectorAll(".stepcard"));
  var stateEl = document.getElementById("how-state");
  var ownerEl = document.getElementById("how-owner");
  var STATES = ["standing by", "locating cell", "validating fix", "writing ownership", "forming territory", "held"];

  function onProg() { var h = document.documentElement; progress.style.width = (h.scrollTop / (h.scrollHeight - h.clientHeight || 1) * 100).toFixed(2) + "%"; }
  window.addEventListener("scroll", onProg, { passive: true }); onProg();

  var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }); }, { threshold: 0.2 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function showCard(i) { cards.forEach(function (c, k) { c.classList.toggle("on", k === i); }); if (stateEl) stateEl.textContent = STATES[i]; if (ownerEl) ownerEl.textContent = i >= 3 ? "you" : "—"; }
  showCard(0);

  function smooth(e0, e1, x) { var t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }
  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }

  // fallback: still pin + step through cards, no 3D
  if (reduce || !webglOK() || typeof THREE === "undefined") {
    stage.classList.add("fallback");
    if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
      gsap.registerPlugin(ScrollTrigger);
      ScrollTrigger.create({ trigger: "#pin", start: "top top", end: "bottom bottom",
        onUpdate: function (s) { showCard(Math.min(5, Math.floor(s.progress * 5.999))); } });
    }
    return;
  }

  var W = stage.clientWidth, H = stage.clientHeight;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(W, H); stage.appendChild(renderer.domElement);

  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 14, 34);
  var camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 100);
  scene.add(new THREE.AmbientLight(0x2a3752, 1.2));
  var key = new THREE.DirectionalLight(0xbcd4ff, 1.5); key.position.set(-6, 14, 8); scene.add(key);
  var warm = new THREE.PointLight(0xffb066, 0, 30); warm.position.set(0, 4, 3); scene.add(warm); // rises with ownership

  var C_DARK = new THREE.Color(0x1a2232), C_SOV = new THREE.Color(0x4c8dff),
      C_MINE = new THREE.Color(0xe68a3d), C_MINE_HI = new THREE.Color(0xffb066);

  function hex(radius, h) { var g = new THREE.CylinderGeometry(radius, radius, h, 6); g.rotateY(Math.PI / 6); return g; }

  // central cell
  var central = new THREE.Mesh(hex(2.0, 0.5),
    new THREE.MeshStandardMaterial({ color: 0x161d2c, metalness: 0.2, roughness: 0.7, emissive: 0x000000 }));
  scene.add(central);
  // outline ring that lights when the cell is identified
  var outline = new THREE.Mesh(new THREE.TorusGeometry(2.02, 0.045, 8, 6),
    new THREE.MeshBasicMaterial({ color: 0x4c8dff, transparent: true, opacity: 0 }));
  outline.rotation.x = Math.PI / 2; outline.rotation.z = Math.PI / 6; scene.add(outline);

  // six neighbours
  var neighbours = [];
  for (var i = 0; i < 6; i++) {
    var a = Math.PI / 6 + i * Math.PI / 3;
    var d = 2 * Math.sqrt(3) * 1.0; // flat-top neighbour distance
    var m = new THREE.Mesh(hex(1.92, 0.5),
      new THREE.MeshStandardMaterial({ color: 0x141b28, metalness: 0.2, roughness: 0.72 }));
    m.position.set(Math.cos(a) * d, 0, Math.sin(a) * d);
    scene.add(m); neighbours.push(m);
  }

  // beacon
  var beacon = new THREE.Group();
  var core = new THREE.Mesh(new THREE.SphereGeometry(0.34, 24, 24), new THREE.MeshBasicMaterial({ color: 0xcfe0ff }));
  beacon.add(core); beacon.position.set(0, 6, 0); scene.add(beacon);
  // accuracy ring (shrinks during validation)
  var acc = new THREE.Mesh(new THREE.RingGeometry(1.0, 1.08, 48), new THREE.MeshBasicMaterial({ color: 0x4c8dff, transparent: true, opacity: 0.0, side: THREE.DoubleSide }));
  acc.rotation.x = -Math.PI / 2; acc.position.y = 0.32; scene.add(acc);

  function apply(p) {
    var cellGlow = smooth(0.14, 0.30, p);
    var validate = smooth(0.32, 0.50, p);
    var own = smooth(0.50, 0.66, p);
    var terr = smooth(0.66, 0.84, p);
    // outline appears at "cell", brightest through validation, fades once owned
    outline.material.opacity = Math.max(cellGlow * 0.9 - own * 0.9, 0);
    // beacon descends 6 -> 0.9 by validation
    beacon.position.y = 6 - smooth(0.0, 0.50, p) * 5.1;
    core.material.color.set(own > 0.5 ? 0xffd9b0 : 0xcfe0ff);
    // accuracy ring: visible while validating, shrinks 1.0 -> 0.36
    acc.material.opacity = (cellGlow * 0.6) * (1 - own);
    var r = 1.0 - validate * 0.64; acc.scale.set(r, r, r);
    // central cell: fill + rise on ownership
    central.material.color.copy(C_DARK).lerp(C_MINE, own);
    central.material.emissive.copy(new THREE.Color(0x000000)).lerp(new THREE.Color(0x7a3d10), own * 0.9);
    central.scale.y = 1 + own * 1.7; central.position.y = own * 0.42;
    warm.intensity = own * 22;
    // neighbours fill as territory forms, staggered
    for (var i = 0; i < 6; i++) {
      var f = smooth(0.66 + i * 0.02, 0.86 + i * 0.02, p);
      neighbours[i].material.color.copy(C_DARK).lerp(C_MINE, f * 0.92);
      neighbours[i].scale.y = 1 + f * 1.2; neighbours[i].position.y = f * 0.28;
    }
    // camera tilts down + zooms slightly as we progress
    var cy = 9 - p * 3.0, cz = 9.5 - p * 2.2;
    camera.position.set(Math.sin(p * 0.9) * 2.2, cy, cz);
    camera.lookAt(0, 0.4 + own * 0.3, 0);
  }
  apply(0.02);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({
      trigger: "#pin", start: "top top", end: "bottom bottom", scrub: 0.5,
      onUpdate: function (s) { apply(s.progress); showCard(Math.min(5, Math.floor(s.progress * 5.999))); }
    });
  }

  var t0 = performance.now(), running = true;
  function frame(now) {
    if (!running) return;
    var t = (now - t0) / 1000;
    var s = 1 + Math.sin(t * 2.4) * 0.08; core.scale.set(s, s, s);
    acc.rotation.z = t * 0.4;
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; t0 = performance.now(); requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = stage.clientWidth; H = stage.clientHeight; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
