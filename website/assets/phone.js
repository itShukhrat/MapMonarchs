/* Real-app phone showcase — swap real screenshots + step copy as the pinned
   section scrolls. Real screens from the closed-testing build; no mockups. */
(function () {
  "use strict";
  var pin = document.getElementById("showpin");
  if (!pin) return;
  var imgs = Array.prototype.slice.call(pin.querySelectorAll(".screen img"));
  var steps = Array.prototype.slice.call(pin.querySelectorAll(".showstep"));
  var N = steps.length;
  function show(i) {
    imgs.forEach(function (im) { im.classList.toggle("on", +im.getAttribute("data-i") === i); });
    steps.forEach(function (s) { s.classList.toggle("on", +s.getAttribute("data-i") === i); });
  }
  show(0);
  function arm() {
    if (typeof gsap === "undefined" || typeof ScrollTrigger === "undefined") return;
    gsap.registerPlugin(ScrollTrigger);
    ScrollTrigger.create({
      trigger: pin, start: "top top", end: "bottom bottom",
      onUpdate: function (s) { show(Math.min(N - 1, Math.floor(s.progress * (N - 0.001)))); }
    });
  }
  if (typeof ScrollTrigger !== "undefined") arm(); else window.addEventListener("load", arm);
})();
