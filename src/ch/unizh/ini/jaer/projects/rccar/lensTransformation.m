k1 = 0.01;
horizon = 90;
sx = 128;
sy = 128;
mx = sx/2;
my = sy/2;
dx = zeros(sx,sy);
dy = zeros(sx,sy);
for y = 1:sy
    for x = 1:sx
       ro = sqrt((mx-x)*(mx-x)+(my-y)*(my-y));
       r = ro*(1+k1*(ro*ro));
       alpha = atan(abs((my-y)/(mx-x)));
       dx(y,x) = (r/ro)*cos(alpha)*sign(mx-x);
       dy(y,x) = (r/ro)*sin(alpha)*sign(my-y);
    end
end
dxi = eye(128)/dx;
dyi = eye(128)/dy;