/* /identity — a crown crest that ignites into the player's colour, and ground
   that lights beneath it. The metaphor: identity isn't a profile, it's a colour
   and the ground it wears. Pointer tilts the crest; scroll ignites it. */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progress = document.getElementById("progress");

  function onProg() { var h = document.documentElement; progress.style.width = (h.scrollTop / (h.scrollHeight - h.clientHeight || 1) * 100).toFixed(2) + "%"; }
  window.addEventListener("scroll", onProg, { passive: true }); onProg();
  var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }); }, { threshold: 0.18 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function smooth(a, b, x) { var t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }
  if (reduce || !webglOK() || typeof THREE === "undefined") { stage.classList.add("fallback"); return; }

  var W = window.innerWidth, H = window.innerHeight;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(W, H);
  if ("outputColorSpace" in renderer) renderer.outputColorSpace = THREE.SRGBColorSpace;
  stage.appendChild(renderer.domElement);

  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 12, 34);
  var camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 100);
  // On desktop the copy sits on the right, so frame the crest to the left third.
  var OFF = W > 760 ? 3.0 : 0;   // copy stays on the left; frame the crest right
  camera.position.set(0, 1.2, 9.6); camera.lookAt(OFF * 0.55, 0.4, 0);
  scene.add(new THREE.AmbientLight(0x2a3450, 1.1));
  var keyLight = new THREE.DirectionalLight(0xcfe0ff, 1.2); keyLight.position.set(-5, 8, 8); scene.add(keyLight);
  var CREST = new THREE.Color(0xe68a3d);
  var glow = new THREE.PointLight(0xe68a3d, 0, 24); glow.position.set(0, 1.2, 3); scene.add(glow);

  // crest group (tilts to pointer)
  var crest = new THREE.Group(); crest.position.set(OFF, 1.15, 0); scene.add(crest);
  // medallion: a hexagonal plate facing camera
  var plateGeo = new THREE.CylinderGeometry(2.05, 2.05, 0.34, 6); plateGeo.rotateX(Math.PI / 2); plateGeo.rotateZ(Math.PI / 6);
  var plateMat = new THREE.MeshStandardMaterial({ color: 0x111826, metalness: 0.35, roughness: 0.55, emissive: 0x000000 });
  var plate = new THREE.Mesh(plateGeo, plateMat); crest.add(plate);
  // rim
  var rim = new THREE.Mesh(new THREE.TorusGeometry(2.06, 0.05, 8, 6), new THREE.MeshBasicMaterial({ color: 0xe68a3d, transparent: true, opacity: 0.0 }));
  rim.rotation.z = Math.PI / 6; crest.add(rim);
  // crown mark (real logo) as a plane in front of the plate
  var crown = null;
  new THREE.TextureLoader().load("/assets/mapmonarchs-crown.png", function (tex) {
    if ("colorSpace" in tex) tex.colorSpace = THREE.SRGBColorSpace;
    var pw = 2.5, ph = pw * (344 / 423);
    crown = new THREE.Mesh(new THREE.PlaneGeometry(pw, ph), new THREE.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.96 }));
    crown.position.set(0, 0.05, 0.24); crest.add(crown);
  });

  // presence: a small cluster of flat hexes on the ground below the crest
  var hexGeo = new THREE.CylinderGeometry(0.5, 0.5, 0.36, 6); hexGeo.rotateY(Math.PI / 6);
  var ground = [], GN = 2;
  for (var q = -GN; q <= GN; q++) for (var r = Math.max(-GN, -q - GN); r <= Math.min(GN, -q + GN); r++) {
    var m = new THREE.Mesh(hexGeo, new THREE.MeshStandardMaterial({ color: 0x141b28, metalness: 0.2, roughness: 0.72 }));
    m.position.set(OFF + Math.sqrt(3) * 0.56 * (q + r / 2), -2.35, 1.5 * 0.56 * r - 0.4);
    var dist = Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r));
    m.userData = { rank: dist + Math.abs(Math.sin(q * 9.7 + r * 4.3)) * 0.8 };
    scene.add(m); ground.push(m);
  }

  var pointer = { x: 0, y: 0 }, tilt = { x: 0, y: 0 };
  window.addEventListener("pointermove", function (e) { pointer.x = (e.clientX / window.innerWidth) * 2 - 1; pointer.y = (e.clientY / window.innerHeight) * 2 - 1; }, { passive: true });

  var scroll = 0;
  function apply(p) {
    scroll = p;
    var ignite = smooth(0.22, 0.5, p);      // colour stage
    var presence = smooth(0.52, 0.82, p);   // ground lights
    plateMat.emissive.copy(new THREE.Color(0x000000)).lerp(new THREE.Color(0x5a2f10), ignite);
    plateMat.color.copy(new THREE.Color(0x111826)).lerp(new THREE.Color(0x35240f), ignite);
    rim.material.opacity = ignite * 0.9;
    glow.intensity = ignite * 16;
    for (var i = 0; i < ground.length; i++) {
      var g = ground[i], f = smooth(0.52 + g.userData.rank * 0.05, 0.82 + g.userData.rank * 0.05, p);
      g.material.color.copy(new THREE.Color(0x141b28)).lerp(CREST, f * 0.92);
      g.material.emissive = g.material.emissive || new THREE.Color(0x000000);
      g.scale.y = 1 + f * 0.8; g.position.y = -2.35 + f * 0.18;
    }
    // subtle rise of the whole crest as it ignites
    crest.position.y = 1.15 + ignite * 0.12;
  }
  apply(0.02);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6, onUpdate: function (s) { apply(s.progress); } });
  }

  var t0 = performance.now(), running = true;
  function frame(now) {
    if (!running) return; var t = (now - t0) / 1000;
    // pointer tilt (eased), plus a slow idle drift
    tilt.x += ((-pointer.y * 0.28 + Math.sin(t * 0.4) * 0.04) - tilt.x) * 0.06;
    tilt.y += ((pointer.x * 0.4 + Math.sin(t * 0.3) * 0.05) - tilt.y) * 0.06;
    crest.rotation.x = tilt.x; crest.rotation.y = tilt.y;
    if (crown) crown.material.opacity = 0.9 + Math.sin(t * 1.6) * 0.06;
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; t0 = performance.now(); requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = window.innerWidth; H = window.innerHeight; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
