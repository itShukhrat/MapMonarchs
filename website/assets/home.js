/* MapMonarchs homepage — a hex territory board that fills as you scroll.
   Procedural Three.js (no external assets). Degrades to CSS + reveals when
   WebGL is missing or the visitor asked for reduced motion. */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progress = document.getElementById("progress");

  // ---- scroll progress bar + section reveals (always on) -------------------
  function onScrollProgress() {
    var h = document.documentElement;
    var p = h.scrollTop / (h.scrollHeight - h.clientHeight || 1);
    progress.style.width = (p * 100).toFixed(2) + "%";
  }
  window.addEventListener("scroll", onScrollProgress, { passive: true });
  onScrollProgress();

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } });
  }, { threshold: 0.18 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function webglOK() {
    try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); }
    catch (e) { return false; }
  }

  if (reduce || !webglOK() || typeof THREE === "undefined") {
    stage.classList.add("fallback");
    return; // honest static board; narrative still reads via reveals
  }

  // ---- three.js hex board --------------------------------------------------
  var W = window.innerWidth, H = window.innerHeight;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(W, H);
  stage.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  var VOID = new THREE.Color(0x06080d);
  scene.fog = new THREE.Fog(0x06080d, 26, 62);

  var camera = new THREE.PerspectiveCamera(46, W / H, 0.1, 200);
  var camState = { y: 30, z: 30, look: 0 };
  function placeCamera() { camera.position.set(0, camState.y, camState.z); camera.lookAt(0, camState.look, -2); }
  placeCamera();

  scene.add(new THREE.AmbientLight(0x24304a, 1.1));
  var key = new THREE.DirectionalLight(0x9fc0ff, 1.4); key.position.set(-8, 20, 10); scene.add(key);
  var rim = new THREE.PointLight(0x4c8dff, 40, 60); rim.position.set(6, 8, 6); scene.add(rim);

  // hex prism geometry (flat board tiles)
  var SIZE = 1.0, GEO_R = 0.92, N = 15;
  var geo = new THREE.CylinderGeometry(GEO_R, GEO_R, 0.6, 6);
  geo.rotateY(Math.PI / 6);
  var mat = new THREE.MeshStandardMaterial({ metalness: 0.15, roughness: 0.72, vertexColors: false });
  mat.onBeforeCompile = function (s) {}; // keep default; per-instance color below

  // build instances within hex radius N
  var cells = [];
  for (var q = -N; q <= N; q++) {
    for (var r = Math.max(-N, -q - N); r <= Math.min(N, -q + N); r++) {
      var x = SIZE * Math.sqrt(3) * (q + r / 2);
      var z = SIZE * 1.5 * r;
      var dist = Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r)); // hex ring
      cells.push({ x: x, z: z, ring: dist, jitter: Math.random() });
    }
  }
  // claim order: spiral out from centre with slight noise; a pocket of rival at one edge
  cells.sort(function (a, b) { return a.ring - b.ring; });
  var count = cells.length;
  var mesh = new THREE.InstancedMesh(geo, mat, count);
  mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
  mesh.setColorAt(0, new THREE.Color(0x1a2233));
  var C_DARK = new THREE.Color(0x161d2c), C_EDGE = new THREE.Color(0x223052),
      C_MINE = new THREE.Color(0xe68a3d), C_MINE_HI = new THREE.Color(0xffb066),
      C_RIVAL = new THREE.Color(0xe5484d), C_BEACON = new THREE.Color(0x4c8dff);
  var dummy = new THREE.Object3D();
  // designate rivals: a cluster offset to +x,+z beyond ring 6
  cells.forEach(function (c, i) {
    c.rival = (c.ring >= 7 && c.x > 5 && c.z > 3 && c.jitter > 0.35);
    c.order = i / count; // 0..1 claim sequence
  });

  function paint(p) {
    // p: 0..1 how much of the board is claimed
    for (var i = 0; i < count; i++) {
      var c = cells[i];
      var owned = !c.rival && c.order <= p * 0.72;
      var rivalOn = c.rival && p > 0.55;
      var col;
      var lift = 0;
      if (owned) {
        var edge = c.order > p * 0.72 - 0.05; // leading edge glows brighter
        col = edge ? C_MINE_HI : C_MINE;
        lift = 0.5 + (edge ? 0.35 : 0.12);
      } else if (rivalOn) {
        col = C_RIVAL; lift = 0.45;
      } else {
        col = (c.ring % 3 === 0) ? C_EDGE : C_DARK; lift = 0;
      }
      dummy.position.set(c.x, lift, c.z);
      dummy.scale.set(1, 1 + lift * 1.6, 1);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
      mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }
  paint(0.14);
  scene.add(mesh);

  // player beacon at centre
  var beacon = new THREE.Group();
  var core = new THREE.Mesh(new THREE.SphereGeometry(0.42, 24, 24),
    new THREE.MeshBasicMaterial({ color: 0x9fc0ff }));
  var halo = new THREE.Mesh(new THREE.RingGeometry(0.7, 0.95, 40),
    new THREE.MeshBasicMaterial({ color: 0x4c8dff, transparent: true, opacity: 0.5, side: THREE.DoubleSide }));
  halo.rotation.x = -Math.PI / 2; halo.position.y = 0.05;
  beacon.add(core); beacon.add(halo); beacon.position.y = 1.1; scene.add(beacon);

  // ---- scroll drives claim + camera ---------------------------------------
  var scroll = 0;
  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({
      trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6,
      onUpdate: function (self) {
        scroll = self.progress;
        paint(0.14 + scroll * 0.86);
        // descend toward the board and tilt as we go
        camState.y = 30 - scroll * 17;      // 30 -> 13
        camState.z = 30 - scroll * 16;      // 30 -> 14
        camState.look = scroll * 1.2;
        var ro = document.getElementById("ro-claim");
        if (ro) { var held = Math.round((0.14 + scroll * 0.86) * 0.72 * count); ro.textContent = held + " cells held"; }
      }
    });
  }

  // ---- render loop (paused when tab hidden or stage off-screen) ------------
  var t0 = performance.now(), running = true;
  function frame(now) {
    if (!running) return;
    var t = (now - t0) / 1000;
    // gentle drift + beacon pulse
    camera.position.x = Math.sin(t * 0.06) * 1.6;
    camState.y += 0; placeCamera();
    var s = 1 + Math.sin(t * 2.2) * 0.06; core.scale.set(s, s, s);
    halo.material.opacity = 0.32 + Math.sin(t * 2.2) * 0.18;
    rim.position.x = Math.sin(t * 0.3) * 8;
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  document.addEventListener("visibilitychange", function () {
    if (document.hidden) { running = false; }
    else if (!running) { running = true; t0 = performance.now(); requestAnimationFrame(frame); }
  });

  window.addEventListener("resize", function () {
    W = window.innerWidth; H = window.innerHeight;
    camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H);
  }, { passive: true });
})();
