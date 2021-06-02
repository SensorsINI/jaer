%% load data
load SlotCarRacer-2010-07-06T22-40-46+0200

%% plot controller data
% filter measurements with box filter
n=10;
box=ones(n,1);
mf=conv(box,meas)/n;
m=length(mf);
mf=mf(n:m);
figure(1);
set(0,'defaultaxesfontsize',14);
%plot(ts,meas,ts,mf,ts,des,'r','linewidth',2)
plot(ts,meas,ts,des,'r','linewidth',2)
xlabel 'time (s)'; ylabel 'speeed (PPS)'
save SlotCarRacer-2010-07-06T22-40-46+0200
set(gca,'xlim',[0,70])

