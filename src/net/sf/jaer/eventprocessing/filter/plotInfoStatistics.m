%% load a logging output from Info filter and plot the results
    [filename,path,filterindex]=uigetfile({'*.*dat','*.aedat, *.dat'},'Select recorded retina data file');
 

f=fopen([path,filename],'r');
% skip header lines
bof=ftell(f);
line=native2unicode(fgets(f));
version=0;

while line(1)=='#',
    fprintf('%s\n',line(1:end-2)); % print line using \n for newline, discarding CRLF written by java under windows
    bof=ftell(f);
    line=native2unicode(fgets(f)); % gets the line including line ending chars
end


