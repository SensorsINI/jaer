To generate a new github release

See which is the latest release on https://github.com/SensorsINI/jaer/releases.
Decide the next release version number, e.g. 1.8.1.

You can also see the tags at https://github.com/SensorsINI/jaer/tags. Releases are made from a particular tag.

In git terminal, from root of jaer

$ git tag <tag, e.g. 1.8.1>
$ git push origin <tag>

Output will be
Total 0 (delta 0), reused 0 (delta 0)
To https://github.com/SensorsINI/jaer.git
 * [new tag]             1.8.1 -> 1.8.1

After some minutes, the new release will appear on https://github.com/SensorsINI/jaer/releases 
The release notes can then be edited on github web page, if you are logged in and have commit rights.

The release name should then be jaer-1.8.1, for example.

View the build results at https://travis-ci.com/github/SensorsINI/jaer .
To add the OAuth secret github token for building, go to https://travis-ci.com/github/SensorsINI/jaer/settings. You need to login with github OAuth.
To make a new token, if needed, login to https://github.com/settings/tokens ,
