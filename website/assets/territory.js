/* /territory — accumulated presence. A cluster grows, its border (held edges)
   glows, a rival pushes an edge in, and presence takes it back. Distinct from
   the home field: here the story is the BORDER and the CONTEST, up close. */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progress = document.getElementById("progress");
  var held = document.getElementById("ro-held"), edge = document.getElementById("ro-edge");

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
  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 20, 48);
  var camera = new THREE.PerspectiveCamera(44, W / H, 0.1, 120);
  scene.add(new THREE.AmbientLight(0x27324a, 1.15));
  var key = new THREE.DirectionalLight(0xbcd4ff, 1.35); key.position.set(-7, 16, 9); scene.add(key);
  var warm = new THREE.PointLight(0xffb066, 14, 40); warm.position.set(-2, 5, 2); scene.add(warm);

  // hex grid (axial), with neighbour adjacency for border detection
  var N = 12, SIZE = 1.0, dir = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
  var cells = [], index = {};
  for (var q = -N; q <= N; q++) for (var r = Math.max(-N, -q - N); r <= Math.min(N, -q + N); r++) {
    var x = SIZE * Math.sqrt(3) * (q + r / 2), z = SIZE * 1.5 * r;
    index[q + "," + r] = cells.length;
    var noise = Math.abs(Math.sin(q * 12.9898 + r * 78.233) * 43758.5453) % 1;
    cells.push({ q: q, r: r, x: x, z: z, dist: Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r)), noise: noise });
  }
  var count = cells.length;
  // rival home: a pocket on the +x (east) side
  cells.forEach(function (c) { c.rivalHome = (c.x > 6.2 && c.dist >= 6 && c.dist <= 10); });
  // growth rank for the player's cluster: distance + organic noise, rival pockets excluded
  var order = cells.map(function (c, i) { return i; }).filter(function (i) { return !cells[i].rivalHome; })
    .sort(function (a, b) { return (cells[a].dist + cells[a].noise * 1.6) - (cells[b].dist + cells[b].noise * 1.6); });
  var MAXOWN = Math.floor(order.length * 0.62);

  var geo = new THREE.CylinderGeometry(0.92, 0.92, 0.6, 6); geo.rotateY(Math.PI / 6);
  var mesh = new THREE.InstancedMesh(geo, new THREE.MeshStandardMaterial({ metalness: 0.16, roughness: 0.72 }), count);
  mesh.setColorAt(0, new THREE.Color(0x161d2c)); scene.add(mesh);
  var C_OPEN = new THREE.Color(0x161d2c), C_OPEN2 = new THREE.Color(0x1e293c),
      C_MINE = new THREE.Color(0xd97b32), C_MINE_HI = new THREE.Color(0xffc07a), C_RIVAL = new THREE.Color(0xe5484d);
  var dummy = new THREE.Object3D();
  var own = new Uint8Array(count); // 0 open, 1 mine, 2 rival

  function computeOwnership(p) {
    own.fill(0);
    var grow = smooth(0.04, 0.52, p), ownedCount = Math.floor(grow * MAXOWN), i;
    for (i = 0; i < count; i++) if (cells[i].rivalHome) own[i] = 2;
    for (i = 0; i < ownedCount; i++) own[order[i]] = 1;
    // contest: rival pushes the eastern edge in (push up), then presence takes it back (push down)
    var push = smooth(0.52, 0.70, p) - smooth(0.80, 0.96, p);
    var k = Math.round(push * 7);
    if (k > 0) {
      var mineIdx = []; for (i = 0; i < count; i++) if (own[i] === 1) mineIdx.push(i);
      mineIdx.sort(function (a, b) { return cells[b].x - cells[a].x; }); // easternmost first
      for (i = 0; i < k && i < mineIdx.length; i++) own[mineIdx[i]] = 2;
    }
    return { ownedCount: ownedCount, push: push };
  }

  function paint(p) {
    var st = computeOwnership(p), borderCount = 0;
    for (var i = 0; i < count; i++) {
      var c = cells[i], o = own[i], col, lift = 0;
      if (o === 1) {
        // border = a held cell touching non-held ground
        var isBorder = false;
        for (var d = 0; d < 6; d++) { var n = index[(c.q + dir[d][0]) + "," + (c.r + dir[d][1])]; if (n === undefined || own[n] !== 1) { isBorder = true; break; } }
        if (isBorder) { col = C_MINE_HI; lift = 0.78; borderCount++; } else { col = C_MINE; lift = 0.4; }
      } else if (o === 2) { col = C_RIVAL; lift = 0.5; }
      else { col = (c.dist % 3 === 0) ? C_OPEN2 : C_OPEN; lift = 0; }
      dummy.position.set(c.x, lift, c.z); dummy.scale.set(1, 1 + lift * 1.4, 1); dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix); mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true; if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    warm.intensity = 8 + smooth(0.04, 0.52, p) * 16;
    // narrative readout (qualitative, not raw counts)
    if (held) held.textContent = st.ownedCount === 0 ? "forming" : (st.ownedCount < MAXOWN * 0.6 ? "holding" : "holding firm");
    if (edge) edge.textContent = st.push > 0.25 ? "under pressure" : (p > 0.82 ? "restored" : "the bright rim");
    // camera: slow orbit around the cluster, tilting down as it grows
    var ang = 0.5 + p * 0.7, rad = 15 - p * 3.5, hgt = 11 - p * 2.5;
    camera.position.set(Math.cos(ang) * rad, hgt, Math.sin(ang) * rad);
    camera.lookAt(-1, 0.5, 0);
  }
  paint(0.05);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6, onUpdate: function (s) { paint(s.progress); } });
  }

  var t0 = performance.now(), running = true;
  function frame(now) { if (!running) return; var t = (now - t0) / 1000; warm.position.x = -2 + Math.sin(t * 0.4) * 1.5; renderer.render(scene, camera); requestAnimationFrame(frame); }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; t0 = performance.now(); requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = window.innerWidth; H = window.innerHeight; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
