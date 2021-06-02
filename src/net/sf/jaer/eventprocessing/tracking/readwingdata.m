fid = fopen('wingLog_2006622_2034_16.txt','r');

c = 1;
lle=1;
lte=1;
rle=1;
rte=1;
while (~feof(fid))
    L = fgetl(fid);
    if ~strcmp(L(1),'#')
        A(c,:) = sscanf(L,'%f')';
        if A(c,1) == 1
            LLE(lle,:) = [A(c,2) A(c,3)];
            lle = lle+1;
        elseif A(c,1) == 2
            LTE(lte,:) = [A(c,2) A(c,3)];
            lte = lte+1;
        elseif A(c,1) == 3
            RLE(rle,:) = [A(c,2) A(c,3)];
            rle = rle+1;
        elseif A(c,1) == 4
            RTE(rte,:) = [A(c,2) A(c,3)];
            rte = rte+1;
        end
        c = c+1;
    end
end
size(LLE)
size(LTE)
size(RLE)
size(RTE)
%plot one of the edges:
plot(LLE(:,1),LLE(:,2),'r.');
hold on;
plot(LTE(:,1),LTE(:,2),'b.')