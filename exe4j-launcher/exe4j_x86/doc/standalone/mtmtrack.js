// Morten's JavaScript Tree Menu Tracking Script
// version 2.3.2-macfriendly, dated 2002-06-10
// http://www.treemenu.com/

// Copyright (c) 2001-2002, Morten Wang & contributors
// All rights reserved.

// This software is released under the BSD License which should accompany
// it in the file "COPYING".  If you do not have this file you can access
// the license through the WWW at http://www.treemenu.com/license.txt

if((navigator.appName == "Netscape" && parseInt(navigator.appVersion) >= 3 && navigator.userAgent.indexOf("Opera") == -1) || (navigator.appName == "Microsoft Internet Explorer" && parseInt(navigator.appVersion) >= 4) || (navigator.appName == "Opera" && parseInt(navigator.appVersion) >= 5)) {
	var MTMCodeFrame = "code";
	for(i = 0; i < parent.frames.length; i++) {
		if(parent.frames[i].name == MTMCodeFrame && parent.frames[i].MTMLoaded) {
			parent.frames[i].MTMTrack = true;
			setTimeout("parent.frames[" + i + "].MTMDisplayMenu()", 50);
			break;
		}
	}
}
