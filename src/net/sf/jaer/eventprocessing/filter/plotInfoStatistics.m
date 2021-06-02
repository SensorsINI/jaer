%% load a logging output from Info filter and plot the results
    [filename,path,filterindex]=uigetfile({'*.txt'},'Select recorded retina data file');
 

f=fopen([path,filename],'r');
data=textscan(f,'%d32%d64%f','CommentStyle','#');
fclose(f);

abst=data{2};
eventrate=data{3};
figure(1);
plot(abst,eventrate)
xlabel 'Time since 1970 (ms)'
ylabel 'Filtered event rate (Hz)'


