$(function() {
    if (window.location == window.parent.location) {
        //direct invocation, redirect to container
        window.location.replace(indexUrl + '#' + helpId);
    } else {
        window.parent.postMessage(helpId, '*');
    }

    $(document).keydown(function(event) {
        var keycode = event.which;
        switch (keycode) {
            case 115: //F4
                window.parent.postMessage('#focus', '*');
                event.preventDefault();
                break;
            case 74:  //j
                nav("left");
                break;
            case 75:  //k
                nav("right");
                break;
            case 85:  //u
                nav("up");
                break;
            case 68:  //d
                nav("down");
                break;
        }
    });

    function nav(id) {
        $('.nav-' + id).first().each(function() {this.click();})
    }

});

$(window).on('message', function(event) {
    window.location.href = event.originalEvent.data;
});
