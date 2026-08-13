/* /command — the control layer. An oblique tactical board: your territory forms
   and a scan sweeps it; at the roadmap stage, dim silhouettes of what's coming
   rise at the far edge (clearly ghosts, never presented as live). */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progress = document.getElementById("progress");
  var stateEl = document.getElementById("ro-state");

  function onProg() { var h = document.documentElement; progress.style.width = (h.scrollTop / (h.scrollHeight - h.clientHeight || 1) * 100).toFixed(2) + "%"; }
  window.addEventListener("scroll", onProg, { passive: true }); onProg();
  var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }); }, { threshold: 0.18 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function smooth(a, b, x) { var t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }
  if (reduce || !webglOK() || typeof THREE === "undefined") { stage.classList.add("fallback"); return; }

  var W = window.innerWidth, H = window.innerHeight;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(W, H); stage.appendChild(renderer.domElement);
  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 18, 46);
  var camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 120);
  scene.add(new THREE.AmbientLight(0x28324c, 1.1));
  var keyL = new THREE.DirectionalLight(0xbcd4ff, 1.3); keyL.position.set(-6, 15, 8); scene.add(keyL);
  var warm = new THREE.PointLight(0xffb066, 12, 40); warm.position.set(-1, 5, 3); scene.add(warm);

  var N = 10, dir = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
  var cells = [], index = {};
  for (var q = -N; q <= N; q++) for (var r = Math.max(-N, -q - N); r <= Math.min(N, -q + N); r++) {
    var x = Math.sqrt(3) * (q + r / 2), z = 1.5 * r;
    index[q + "," + r] = cells.length;
    cells.push({ q: q, r: r, x: x, z: z, dist: Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r)), noise: Math.abs(Math.sin(q * 12.9 + r * 78.2)) % 1 });
  }
  var count = cells.length;
  var order = cells.map(function (c, i) { return i; }).sort(function (a, b) { return (cells[a].dist + cells[a].noise * 1.4) - (cells[b].dist + cells[b].noise * 1.4); });
  var MAXOWN = Math.floor(count * 0.48);

  var geo = new THREE.CylinderGeometry(0.92, 0.92, 0.6, 6); geo.rotateY(Math.PI / 6);
  var mesh = new THREE.InstancedMesh(geo, new THREE.MeshStandardMaterial({ metalness: 0.18, roughness: 0.7 }), count);
  mesh.setColorAt(0, new THREE.Color(0x161d2c)); scene.add(mesh);
  var C_OPEN = new THREE.Color(0x151c2a), C_OPEN2 = new THREE.Color(0x1d2740), C_MINE = new THREE.Color(0xd97b32), C_MINE_HI = new THREE.Color(0xffc07a);
  var dummy = new THREE.Object3D(); var own = new Uint8Array(count);

  // scan sweep — a thin bright bar crossing the board
  var scan = new THREE.Mesh(new THREE.PlaneGeometry(0.5, N * 3.4), new THREE.MeshBasicMaterial({ color: 0x7da9ff, transparent: true, opacity: 0.14, blending: THREE.AdditiveBlending }));
  scan.rotation.x = -Math.PI / 2; scan.position.y = 0.7; scene.add(scan);

  // roadmap silhouettes — dim, tall, clearly future (never lit like real ground)
  var ghosts = [];
  for (var g = 0; g < 3; g++) {
    var gm = new THREE.Mesh(new THREE.CylinderGeometry(1.1, 1.1, 3.4, 6),
      new THREE.MeshBasicMaterial({ color: 0x2f4d86, transparent: true, opacity: 0.0, wireframe: false }));
    gm.geometry.rotateY(Math.PI / 6);
    gm.position.set((g - 1) * 4.2, 0, -N * 1.5 - 3);
    scene.add(gm); ghosts.push(gm);
  }

  function paint(p) {
    var grow = smooth(0.05, 0.58, p), ownedCount = Math.floor(grow * MAXOWN), i;
    own.fill(0); for (i = 0; i < ownedCount; i++) own[order[i]] = 1;
    for (i = 0; i < count; i++) {
      var c = cells[i], col, lift = 0;
      if (own[i] === 1) {
        var border = false; for (var d = 0; d < 6; d++) { var n = index[(c.q + dir[d][0]) + "," + (c.r + dir[d][1])]; if (n === undefined || own[n] !== 1) { border = true; break; } }
        col = border ? C_MINE_HI : C_MINE; lift = border ? 0.6 : 0.34;
      } else { col = (c.dist % 3 === 0) ? C_OPEN2 : C_OPEN; lift = 0; }
      dummy.position.set(c.x, lift, c.z); dummy.scale.set(1, 1 + lift * 1.3, 1); dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix); mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true; if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    warm.intensity = 8 + grow * 14;
    // roadmap ghosts rise, dim
    var road = smooth(0.66, 0.9, p);
    for (i = 0; i < ghosts.length; i++) { ghosts[i].material.opacity = road * 0.16; ghosts[i].scale.y = 0.2 + road * (0.8 + i * 0.12); ghosts[i].position.y = ghosts[i].scale.y * 1.7 - 0.3; }
    if (stateEl) stateEl.textContent = ownedCount === 0 ? "standing by" : (road > 0.3 ? "board ready" : (grow < 0.9 ? "reading" : "holding"));
    // oblique command camera, slow push-in
    var cz = 15 - p * 3.4, cy = 8.5 - p * 1.6;
    camera.position.set(Math.sin(p * 0.5) * 2.5, cy, cz); camera.lookAt(0, 0.4, -1.5);
  }
  paint(0.04);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6, onUpdate: function (s) { paint(s.progress); } });
  }

  var t0 = performance.now(), running = true;
  function frame(now) {
    if (!running) return; var t = (now - t0) / 1000;
    scan.position.x = Math.sin(t * 0.5) * (N * 1.6);
    scan.material.opacity = 0.1 + (Math.cos(t * 0.5) * 0.5 + 0.5) * 0.1;
    renderer.render(scene, camera); requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; t0 = performance.now(); requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = window.innerWidth; H = window.innerHeight; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
