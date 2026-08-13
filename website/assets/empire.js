/* /empire — a concept world. Held cells light up, then links draw between the
   ones that touch, knitting scattered ground into connected domains. Capitals
   rise where clusters meet. Deliberately a sketch: labelled a concept in the UI,
   never presented as a live feature. */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progress = document.getElementById("progress");
  var stateEl = document.getElementById("ro-state");

  function onProg() { var h = document.documentElement; if (progress) progress.style.width = (h.scrollTop / (h.scrollHeight - h.clientHeight || 1) * 100).toFixed(2) + "%"; }
  window.addEventListener("scroll", onProg, { passive: true }); onProg();
  var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }); }, { threshold: 0.18 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function smooth(a, b, x) { var t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
  function rnd(a, b) { var s = Math.sin(a * 127.1 + b * 311.7) * 43758.5453; return s - Math.floor(s); }
  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }
  if (reduce || !webglOK() || typeof THREE === "undefined") { stage.classList.add("fallback"); return; }

  var W = window.innerWidth, H = window.innerHeight;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(W, H); stage.appendChild(renderer.domElement);
  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 20, 52);
  var camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 140);
  scene.add(new THREE.AmbientLight(0x28324c, 1.1));
  var keyL = new THREE.DirectionalLight(0xbcd4ff, 1.2); keyL.position.set(-6, 15, 8); scene.add(keyL);
  var warm = new THREE.PointLight(0xffb066, 10, 44); warm.position.set(2, 6, 4); scene.add(warm);

  // hex field
  var N = 9, dir = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
  var cells = [], index = {};
  for (var q = -N; q <= N; q++) for (var r = Math.max(-N, -q - N); r <= Math.min(N, -q + N); r++) {
    index[q + "," + r] = cells.length;
    cells.push({ q: q, r: r, x: Math.sqrt(3) * (q + r / 2), z: 1.5 * r });
  }
  var count = cells.length;

  // grow owned clusters from a few capital seeds
  var seeds = [index["-4,1"], index["3,-2"], index["0,3"], index["-2,-3"]].filter(function (v) { return v !== undefined; });
  var own = new Uint8Array(count), cap = new Uint8Array(count);
  var frontier = [];
  seeds.forEach(function (s) { own[s] = 1; cap[s] = 1; frontier.push(s); });
  var target = Math.floor(count * 0.4), owned = seeds.length, guard = 0;
  while (owned < target && frontier.length && guard++ < 4000) {
    var fi = Math.floor(rnd(guard, 7.3) * frontier.length), ci = frontier[fi], c = cells[ci];
    var d = Math.floor(rnd(ci, guard) * 6), nb = index[(c.q + dir[d][0]) + "," + (c.r + dir[d][1])];
    if (nb !== undefined && !own[nb]) { own[nb] = 1; owned++; frontier.push(nb); }
    if (rnd(guard, ci) < 0.25) frontier.splice(fi, 1);
  }

  // hex prisms
  var geo = new THREE.CylinderGeometry(0.9, 0.9, 0.6, 6); geo.rotateY(Math.PI / 6);
  var mesh = new THREE.InstancedMesh(geo, new THREE.MeshStandardMaterial({ metalness: 0.2, roughness: 0.7 }), count); scene.add(mesh);
  var C_OPEN = new THREE.Color(0x141b28), C_OPEN2 = new THREE.Color(0x1c2740), C_OWN = new THREE.Color(0xcf7530), C_CAP = new THREE.Color(0xffc079);
  var dummy = new THREE.Object3D();
  function paintCells(rise) {
    for (var i = 0; i < count; i++) {
      var c = cells[i], lift, col;
      if (own[i]) { lift = (cap[i] ? 1.5 : 0.55) * rise; col = cap[i] ? C_CAP : C_OWN; }
      else { lift = 0; col = ((Math.abs(c.q) + Math.abs(c.r)) % 3 === 0) ? C_OPEN2 : C_OPEN; }
      dummy.position.set(c.x, lift, c.z); dummy.scale.set(1, 1 + lift * 1.4, 1); dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix); mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true; if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }

  // links between adjacent owned cells (each undirected pair once)
  var lp = [];
  for (var i = 0; i < count; i++) {
    if (!own[i]) continue; var c = cells[i];
    for (var d2 = 0; d2 < 6; d2++) {
      var nb2 = index[(c.q + dir[d2][0]) + "," + (c.r + dir[d2][1])];
      if (nb2 !== undefined && own[nb2] && nb2 > i) {
        var yA = (cap[i] ? 1.5 : 0.55), yB = (cap[nb2] ? 1.5 : 0.55);
        lp.push(c.x, yA + 0.32, c.z, cells[nb2].x, yB + 0.32, cells[nb2].z);
      }
    }
  }
  var linkGeo = new THREE.BufferGeometry();
  linkGeo.setAttribute("position", new THREE.Float32BufferAttribute(lp, 3));
  var links = new THREE.LineSegments(linkGeo, new THREE.LineBasicMaterial({ color: 0xffb570, transparent: true, opacity: 0.0, blending: THREE.AdditiveBlending, depthWrite: false }));
  scene.add(links);
  var linkVerts = lp.length / 3;

  function render(p) {
    var rise = smooth(0.02, 0.4, p);
    paintCells(rise);
    var lk = smooth(0.28, 0.82, p);
    links.geometry.setDrawRange(0, Math.floor(lk * linkVerts / 2) * 2);
    links.material.opacity = 0.15 + lk * 0.55;
    warm.intensity = 6 + rise * 10;
    if (stateEl) stateEl.textContent = p < 0.24 ? "not in the app yet" : (lk < 0.85 ? "connecting the sketch" : "a connected domain");
    // slow orbit that drifts in with scroll
    var ang = 0.5 + p * 0.7, radc = 26 - p * 6, hy = 15 - p * 4;
    camera.position.set(Math.sin(ang) * radc, hy, Math.cos(ang) * radc);
    camera.lookAt(0, 0.6, 0);
  }
  render(0.0);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6, onUpdate: function (s) { render(s.progress); } });
  }

  var running = true;
  function frame() { if (!running) return; renderer.render(scene, camera); requestAnimationFrame(frame); }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = window.innerWidth; H = window.innerHeight; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
