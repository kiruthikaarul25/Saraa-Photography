/* Saara nav: torch light follows the mouse / finger on the top nav links */
(function(){
  function init(){
    document.querySelectorAll('.ss-nav .ss-links a').forEach(function(a){
      function mv(e){
        var r=a.getBoundingClientRect();
        a.style.setProperty('--mx',(e.clientX-r.left)+'px');
        a.style.setProperty('--my',(e.clientY-r.top)+'px');
      }
      a.addEventListener('pointermove',mv);
      a.addEventListener('pointerdown',mv);
      a.addEventListener('pointerleave',function(){a.style.removeProperty('--mx');a.style.removeProperty('--my');});
    });
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
