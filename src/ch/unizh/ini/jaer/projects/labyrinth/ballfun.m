function xdot=ballfun(t,x)
% outputs 1st derivatives of input
% let input x be
%   x(1)=pos
%   x(2)=vel
% output is
%   xdot(1)=vel
%   xdot(2)=accel

m=1; % grams
g=9.8; % m/s^2
f=0.1; % friction force when multiplied by xdot
Gp=1; % gain on position error
Gv=0; % gain on velocity

% compute Gv to damp exactly

Gv=sqrt(4*g*m*Gp)-f;

x1=1; % desired position

xdot(2)=-(f+Gv)*x(2)-g/m*Gp*x(1)+g/m*Gp*x1;
xdot(1)=x(2);

xdot=xdot(:);


