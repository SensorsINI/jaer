
$(function () {

    var inSync = false;
    var ignoreNextSync = false;

    var url = window.location.href;
    var base = url.substring(0, url.lastIndexOf('/') + 1);

    var $tree = $('#tree');
    $tree.tree({
        data: treeData,
        autoEscape: false,
        onCreateLi: function (node, $li) {
            if (node.children.length == 0 && node.getLevel() > 1) {
                var iconUrl = node.imageUrl ? node.imageUrl : 'images/help_topic_18@2x.png';
                $li.css({
                    'background': 'url(' + iconUrl + ') no-repeat left 2px',
                    'background-size': '18px 18px',
                    'padding-left': '20px'
                });
            }
        }
    });

    $tree.bind('tree.select',
        function (event) {
            if (event.node && !inSync) {
                var node = event.node;
                var targetWindow = window.parent.document.getElementById('content').contentWindow;
                targetWindow.postMessage(base + node.href, '*');
                ignoreNextSync = true;
            }
        }
    );

    $tree.bind('tree.click',
        function(event) {
            if ($tree.tree('isNodeSelected', event.node)) {
                event.preventDefault();
            }
        }
    );

    $(document).keydown(function(event) {
        if (event.which == 115) { // F4
            document.getElementById('content').contentWindow.focus();
            event.preventDefault();
        }
    });

    // handle redirect
    var hash = location.hash;
    if (hash) {
        if (history) {
            // remove hash
            history.pushState('', document.title, window.location.pathname);
        }
        var helpId = hash.substr(1);
        ignoreNextSync = true;
        $('#content').one('load', function() {
            syncMenu(helpId, true);
        });
    }

    $(window).on('message', function(event) {
        var data = event.originalEvent.data;
        if (data == '#focus') {
            $('#content').blur();
            window.focus();
            $tree.focus();
        } else {
            if (!ignoreNextSync) {
                syncMenu(data, false)
            }
            ignoreNextSync = false;
        }
    });

    $tree.focus();

    function syncMenu(helpId, load) {
        inSync = !load;
        try {
            var node = $tree.tree('getNodeById', helpId);
            var currentSelection = $tree.tree('getSelectedNode');
            if (node != currentSelection) {
                $.each($tree.tree('getState').open_nodes, function(index, value) {
                    $tree.tree('closeNode', $tree.tree('getNodeById', value), false)
                });

                $tree.tree('selectNode', node);
                $tree.tree('scrollToNode', node);
            }
        } finally {
            inSync = false;
        }
    }

});
