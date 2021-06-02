%generates a set of points lyling on multiple circles aroung a common center
function neighbors()

%---------------------------------------------------------------
% relativeliy sparse, with higher density in center (89 points)
r = [ 0  1  2  3  5  7  9 11 14 17];     % radius of the circle on which n(i) points will be drawn
n = [ 1  4  4  8  8  8  8 16 16 16];     % number of points on a circle with radius r(i)
w = [ 0  0  1  0  1  0  1  0  1  0];     % if w(i) is 0, the first point will be drawn at 0°, else it will be slightly shifted

% maximal circle radius
max = r(size(r,2));
% image size
img = zeros(2*max+1, 2*max+1);
% total number of points drawn
count = 0;

% generate points
for i = (1:size(r,2))
    for j = (0:n(i)-1);
        if w(i) == 0
            alpha = 2*j/n(i)*pi;
        else
            alpha = 2*j/n(i)*pi + pi/(n(i));
        end
        x = round(r(i)*sin(alpha));
        y = round(r(i)*cos(alpha));
        %add point only, if this point was not drawn yet
        if img(x+max+1, y+max+1) == 0
            % image
            img(x+max+1, y+max+1) = 2;
            % output vectors
            count = count + 1;
            X(count) = x;
            Y(count) = y;
        end
    end
end

% show image
colormap(gray(2));
image(img)

% show coordinates of points relative to image center
[X' Y']
count


% other possible prototypes. for usage copy-paste them to the top
%---------------------------------------------------------------
% circles with same number of point for each radius and therefore decreasing density (177 points)
r = [ 0  1  2  3  4  5  6  7  8  9 10];
n = [20 20 20 20 20 20 20 20 20 20 20];
w = [ 0  1  0  1  0  1  0  1  0  1  0];

%---------------------------------------------------------------
% solid disc (351 points)
r = [ 0  1  2  3  4  5  6  7  8  9 10];
n = [99 99 99 99 99 99 99 99 99 99 99];
w = [ 0  1  0  1  0  1  0  1  0  1  0];


%---------------------------------------------------------------
% fig. 5.x
r = [ 0  1  2  3  5  7  9 11 14 17 20];
n = [ 1  4  4  8  8  8  8 16 16 16 16];
w = [ 0  0  1  0  1  0  1  0  1  0  1]; 
%---------------------------------------------------------------
% fig. 5.x
r = [ 0  1  2  3  5  7  9 11 13 15 20];
n = [ 1  4  4  8  8  8  8 16 16 16  0];
w = [ 0  0  1  0  1  0  1  0  1  0  0]; 
%---------------------------------------------------------------
% fig. 5.x
r = [ 0  1  2  3  4  5  6  7  8  9 20];
n = [ 1  4  4  8  8  8  8 16 16 16  0];
w = [ 0  0  1  0  1  0  1  0  1  0  0];