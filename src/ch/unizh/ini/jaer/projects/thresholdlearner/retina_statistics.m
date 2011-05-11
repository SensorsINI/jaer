% read the events out from the DVS data log file and do the simple
% summation of the events and arrange the summation in a matlab
% conventional way. the out put of the function is two events number arrays
% one is events positive denotes how much positive events occurs during 
% the stimulus and the other is events negative denotes how much negative
% events happened during the stimulus
% you should save these two 128 by 128 arrays to a data file, say ev1.mat

% @ time May 11, 2011
% @ location Capo Caccia
% @ events Capo Caccia Workshop 2011
% @ author Tao Zhou

clear all

retina_size=128;
events_positive=zeros(retina_size);
events_negative=zeros(retina_size);

[allAddr,allTs]=loadaerdat('2.aedat'); % read the DVS data to matlab
total_len=length(allAddr);
clear allTs;

% load the data into the memory in several iterations
step_size=300000; % read
iteration=total_len/step_size;
for j=1:iteration
    
    if j~=iteration
        data=allAddr(step_size*(j-1)+1:step_size*(j),1);
    else
        data=allAddr(step_size*(j-1)+1:total_len,1);
    end
    [x,y,pol]=extractRetina128EventsFromAddr(data);% extract the retina position
    len=length(x);
    for i=1:len
        if pol(i)==1
            events_positive(128-y(i),x(i)+1)=events_positive(128-y(i),x(i)+1)+1;
        elseif pol(i)==-1
            events_negative(128-y(i),x(i)+1)=events_negative(128-y(i),x(i)+1)+1;
        end
    end
end