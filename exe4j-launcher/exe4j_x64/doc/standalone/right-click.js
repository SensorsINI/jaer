function MTMcatchRight(thisEvent) {
  if(thisEvent) {
    if (thisEvent.which == 3 || thisEvent.which == 2) {
      alert("Ping!");
      return false;
    }
  } else if(event && (event.button == 2 || event.button == 3)) {
    alert("Ping!");
    return false;
  }
  return true;
}

function initialize() {
  document.onmousedown = MTMcatchRight;
  if(document.layers) {
    window.captureEvents(Event.MOUSEDOWN);
  }
  window.onmousedown = MTMcatchRight;
}
