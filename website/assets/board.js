/* MapMonarchs — the living board of Uzbekistan (homepage hero).
 *
 * The country outline (window.UZ_OUTLINE) is the WORLD BOUNDARY; a hex field is
 * generated and clipped to it. Scroll is TIME: a deterministic clash simulation
 * runs — sovereigns arrive, territories grow, borders meet, and cells CHANGE
 * HANDS — until the board is dense. It is a BOARD SIMULATION, not live data
 * (the real game is in closed testing); the page labels it so and the numbers
 * are illustrative, never presented as real player counts.
 *
 * WORLD_MODE = "simulation" (deterministic, seeded — identical every load).
 */
(function () {
  "use strict";
  var WORLD_MODE = "simulation";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var stage = document.getElementById("stage");
  var progressBar = document.getElementById("progress");
  var elPlayers = document.getElementById("ro-players"), elCells = document.getElementById("ro-cells"), elChanged = document.getElementById("ro-changed"), elEra = document.getElementById("ro-era");

  function onProg() { var h = document.documentElement; progressBar.style.width = (h.scrollTop / (h.scrollHeight - h.clientHeight || 1) * 100).toFixed(2) + "%"; }
  window.addEventListener("scroll", onProg, { passive: true }); onProg();
  var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }); }, { threshold: 0.16 });
  document.querySelectorAll(".reveal").forEach(function (el) { io.observe(el); });

  function webglOK() { try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; } }
  var OUT = window.UZ_OUTLINE || [];

  // point in polygon (ray cast) against the outline [x,z]
  function inside(x, z) {
    var n = OUT.length, ins = false, i, j;
    for (i = 0, j = n - 1; i < n; j = i++) {
      var xi = OUT[i][0], zi = OUT[i][1], xj = OUT[j][0], zj = OUT[j][1];
      if (((zi > z) !== (zj > z)) && (x < (xj - xi) * (z - zi) / (zj - zi) + xi)) ins = !ins;
    }
    return ins;
  }

  // ---------- build hex field clipped to Uzbekistan ----------
  var S = 0.34, dir = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
  var cells = [], index = {};
  (function build() {
    var minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9, p;
    for (p = 0; p < OUT.length; p++) { minX = Math.min(minX, OUT[p][0]); maxX = Math.max(maxX, OUT[p][0]); minZ = Math.min(minZ, OUT[p][1]); maxZ = Math.max(maxZ, OUT[p][1]); }
    var rMin = Math.floor(minZ / (S * 1.5)) - 1, rMax = Math.ceil(maxZ / (S * 1.5)) + 1;
    for (var r = rMin; r <= rMax; r++) {
      var qMin = Math.floor((minX / (S * Math.sqrt(3))) - r / 2) - 1, qMax = Math.ceil((maxX / (S * Math.sqrt(3))) - r / 2) + 1;
      for (var q = qMin; q <= qMax; q++) {
        var x = S * Math.sqrt(3) * (q + r / 2), z = S * 1.5 * r;
        if (inside(x, z)) { index[q + "," + r] = cells.length; cells.push({ q: q, r: r, x: x, z: z }); }
      }
    }
    // neighbour indices
    for (var i = 0; i < cells.length; i++) { var c = cells[i]; c.nb = []; for (var d = 0; d < 6; d++) { var n = index[(c.q + dir[d][0]) + "," + (c.r + dir[d][1])]; if (n !== undefined) c.nb.push(n); } }
  })();
  var COUNT = cells.length;

  // deterministic pseudo-random from indices (no state)
  function rnd(a, b) { var s = Math.sin(a * 127.1 + b * 311.7) * 43758.5453; return s - Math.floor(s); }

  // sovereign seeds at real Uzbek cities (projected to board coords), with arrival order
  var SEEDS = [
    { x: 9.01, z: 0.23, arrive: 0 },   // Tashkent
    { x: 4.63, z: 4.48, arrive: 2 },   // Samarkand
    { x: -0.25, z: 4.18, arrive: 5 },  // Bukhara
    { x: 14.97, z: 1.59, arrive: 8 },  // Fergana (east)
    { x: -9.49, z: -2.72, arrive: 12 },// Nukus (west)
    { x: -7.53, z: -0.38, arrive: 16 },// Urgench
    { x: 5.25, z: 10.68, arrive: 22 }, // Termez (south)
    { x: 1.59, z: 3.38, arrive: 30 }   // Navoi
  ];
  function nearestCell(x, z) { var best = 0, bd = 1e9; for (var i = 0; i < COUNT; i++) { var dx = cells[i].x - x, dz = cells[i].z - z, dd = dx * dx + dz * dz; if (dd < bd) { bd = dd; best = i; } } return best; }
  SEEDS.forEach(function (s) { s.cell = nearestCell(s.x, s.z); });

  // ---------- run the clash simulation into frames ----------
  var FRAMES = 64, frames = [], stats = [];
  (function simulate() {
    var owner = new Int8Array(COUNT); owner.fill(-1);
    var changed = 0;
    for (var f = 0; f < FRAMES; f++) {
      // sovereigns arrive
      SEEDS.forEach(function (s, si) { if (f >= s.arrive && owner[s.cell] < 0) owner[s.cell] = si; });
      var next = Int8Array.from(owner);
      var i, d, o;
      // growth: unclaimed cells adopt the strongest adjacent owner
      for (i = 0; i < COUNT; i++) {
        if (owner[i] >= 0) continue;
        var tally = {}, best = -1, bestC = 0;
        for (d = 0; d < cells[i].nb.length; d++) { o = owner[cells[i].nb[d]]; if (o >= 0) { tally[o] = (tally[o] || 0) + 1; if (tally[o] > bestC || (tally[o] === bestC && o < best)) { bestC = tally[o]; best = o; } } }
        if (best >= 0 && rnd(i, f) < 0.55) next[i] = best;
      }
      // takeover: a border cell under stronger pressure changes hands
      for (i = 0; i < COUNT; i++) {
        o = owner[i]; if (o < 0) continue;
        var cnt = {}, own = 0, rivalBest = -1, rivalC = 0;
        for (d = 0; d < cells[i].nb.length; d++) { var no = owner[cells[i].nb[d]]; if (no < 0) continue; if (no === o) own++; else { cnt[no] = (cnt[no] || 0) + 1; if (cnt[no] > rivalC) { rivalC = cnt[no]; rivalBest = no; } } }
        if (rivalBest >= 0 && rivalC >= 3 && rivalC > own && rnd(i, f + 999) < 0.30) { next[i] = rivalBest; changed++; }
      }
      owner = next;
      frames.push(Int8Array.from(owner));
      var players = {}, held = 0;
      for (i = 0; i < COUNT; i++) if (owner[i] >= 0) { held++; players[owner[i]] = 1; }
      stats.push({ players: Object.keys(players).length, cells: held, changed: changed });
    }
  })();

  // ---------- honest illustrative numbers (scale the sim up to a believable board) ----------
  var CELL_FACTOR = 42;      // each simulated cell stands for ~this many real cells, illustratively
  var eras = ["The board opens", "First claims land", "Regions take shape", "Borders meet", "Ground changes hands", "The map fills", "A national board"];
  function fmt(n) { return n.toLocaleString("en-US"); }

  if (reduce || !webglOK() || typeof THREE === "undefined") {
    stage.classList.add("fallback");
    // still show the final honest illustrative state in the readout
    var last = stats[FRAMES - 1];
    if (elPlayers) elPlayers.textContent = "~" + fmt(last.players * 60);
    if (elCells) elCells.textContent = "~" + fmt(last.cells * CELL_FACTOR);
    if (elChanged) elChanged.textContent = "~" + fmt(last.changed * 8);
    if (elEra) elEra.textContent = eras[eras.length - 1];
    return;
  }

  // ---------- three.js ----------
  var W = window.innerWidth, H = window.innerHeight, mobile = W < 760;
  var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, mobile ? 1.5 : 2)); renderer.setSize(W, H);
  if ("outputColorSpace" in renderer) renderer.outputColorSpace = THREE.SRGBColorSpace;
  stage.appendChild(renderer.domElement);
  var scene = new THREE.Scene(); scene.fog = new THREE.Fog(0x06080d, 34, 82);
  var camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 200);
  scene.add(new THREE.AmbientLight(0x232f48, 1.15));
  var keyL = new THREE.DirectionalLight(0xbcd4ff, 1.35); keyL.position.set(-14, 30, 16); scene.add(keyL);
  var fill = new THREE.DirectionalLight(0x2a3a5c, 0.5); fill.position.set(10, 12, -12); scene.add(fill);

  // world boundary line
  var pts = OUT.map(function (p) { return new THREE.Vector3(p[0], 0.12, p[1]); });
  var line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0x4c8dff, transparent: true, opacity: 0.5 }));
  scene.add(line);

  // sovereign palette (distinct, muted-but-clear; not neon)
  var PAL = [0xe68a3d, 0x4c8dff, 0x3fb6a8, 0xb06be0, 0xe5c15a, 0xe5556b, 0x5ec26a, 0x62b8e0].map(function (h) { return new THREE.Color(h); });
  var PAL_HI = PAL.map(function (c) { return c.clone().lerp(new THREE.Color(0xffffff), 0.34); });
  var C_OPEN = new THREE.Color(0x141b28), C_OPEN2 = new THREE.Color(0x1a2334);

  var geo = new THREE.CylinderGeometry(S * 0.94, S * 0.94, 0.5, 6); geo.rotateY(Math.PI / 6);
  var mesh = new THREE.InstancedMesh(geo, new THREE.MeshStandardMaterial({ metalness: 0.16, roughness: 0.72 }), COUNT);
  mesh.setColorAt(0, C_OPEN); scene.add(mesh);
  var dummy = new THREE.Object3D();

  function paint(p) {
    var f = Math.min(FRAMES - 1, Math.max(0, Math.round(p * (FRAMES - 1)))), owner = frames[f], i, d;
    for (i = 0; i < COUNT; i++) {
      var o = owner[i], col, lift;
      if (o < 0) { col = ((cells[i].q + cells[i].r) % 3 === 0) ? C_OPEN2 : C_OPEN; lift = 0.02; }
      else {
        var border = false;
        for (d = 0; d < cells[i].nb.length; d++) { if (owner[cells[i].nb[d]] !== o) { border = true; break; } }
        if (cells[i].nb.length < 6) border = true; // country edge
        col = border ? PAL_HI[o] : PAL[o];
        lift = border ? 0.62 : 0.34;
      }
      dummy.position.set(cells[i].x, lift, cells[i].z); dummy.scale.set(1, 1 + lift * 2.0, 1); dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix); mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true; if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    line.material.opacity = 0.5 - p * 0.24;
    // readouts — illustrative, scaled, honestly labelled in the DOM
    var st = stats[f];
    if (elPlayers) elPlayers.textContent = "~" + fmt(st.players * 60);
    if (elCells) elCells.textContent = "~" + fmt(st.cells * CELL_FACTOR);
    if (elChanged) elChanged.textContent = "~" + fmt(st.changed * 8);
    if (elEra) elEra.textContent = eras[Math.min(eras.length - 1, Math.floor(p * eras.length))];
    // camera: overview -> descend/rotate -> pull back to the whole country
    var ang = -Math.PI / 2 + Math.sin(p * Math.PI) * 0.28;
    var pull = 1 - Math.sin(p * Math.PI) * 0.16;            // dip closer mid, back out at the ends
    var rad = (mobile ? 30 : 26) * pull, hgt = (mobile ? 30 : 25) * pull + (p > 0.85 ? (p - 0.85) * 30 : 0);
    camera.position.set(Math.cos(ang) * rad, hgt, Math.sin(ang) * rad + 2);
    camera.lookAt(0, 0, mobile ? 0 : -0.5);
  }
  paint(0);

  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({ trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.6, onUpdate: function (s) { paint(s.progress); } });
  }

  var running = true;
  function frame() { if (!running) return; renderer.render(scene, camera); requestAnimationFrame(frame); }
  requestAnimationFrame(frame);
  document.addEventListener("visibilitychange", function () { if (document.hidden) running = false; else if (!running) { running = true; requestAnimationFrame(frame); } });
  window.addEventListener("resize", function () { W = window.innerWidth; H = window.innerHeight; mobile = W < 760; camera.aspect = W / H; camera.updateProjectionMatrix(); renderer.setSize(W, H); }, { passive: true });
})();
