%% model control of a ball (actually cylinder) on a 1d line subject to
%% gravity (tilt of table) and friction.


dt=1e-3; % timestep seconds
tspan=0:dt:10; % seconds

h=@ballfun;

[t,state]=ode45(h,tspan,[0,0]);

pos=state(:,1);
vel=state(:,2);

figure(1);
plot(t,pos);
xlabel 'time (s)'
ylabel 'position (m)'

