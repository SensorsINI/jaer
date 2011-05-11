% learn a set of threshold for the pixels to get a better rendering effect
% use simple learning rule which could be stated like the following
% nt(i)=new threshold of pixel i
% ot(i)=old threshold of pixel i
% lr=learning rate
% evr(i)=event rate of pixel i
% mevr(i,j)=mean events rate of pixel i in its neighborhood j
% update rule: nt(i)=ot(i)+lr*(evr(i)-mevr(i,j))
% the neighborhood of the pixel is defined as all the possible image
% patches including a certain pixel
% the default neighborhood size is 8 by 8 square
% @ time May 11, 2011
% @ location Capo Caccia
% @ events Capo Caccia Workshop 2011
% @ author Tao Zhou

clear all
img_size=128;
% allocate the memory for threshold map
threshold_map_pos=ones(img_size);
threshold_map_neg=ones(img_size);

% learning rate alpha
alpha=0.000005;
for i=1:9 % number of data available now
    % name the events statistics in the file formated as "evi.mat"
    % where i is basically a int number say ev1.mat or ev2.mat
    dir=['ev' num2str(i) '.mat'];
    load(dir)
    neighborhood_size=8;
    % left shift is the number of pixels you need to shift from a
    % neighborhood center to the left or top border of the neighborhood
    % right shift is the number of pixels you need to shift from a
    % neighborhood center to the right or bottom border of the neighborhood
    if mod(neighborhood_size,2)==0
        left_shift=neighborhood_size/2-1;
        right_shift=neighborhood_size/2;
    else
        left_shift=(neighborhood_size-1)/2;
        right_shift=(neighborhood_size-1)/2;
    end
    for p=left_shift+1:img_size-right_shift
        for q=left_shift+1:img_size-right_shift
            mean_rate_pos=sum(sum(events_positive(p-left_shift:p+...
            right_shift,q-left_shift:q+right_shift)))/(neighborhood_size^2);
            mean_rate_neg=sum(sum(events_negative(p-left_shift:p+...
            right_shift,q-left_shift:q+right_shift)))/(neighborhood_size^2);          
            for m=1:neighborhood_size
                for n=1:neighborhood_size
                    fs=4; % fixed shift = left_shift+1
                    threshold_map_pos(p+m-fs,q+n-fs)=...
                    threshold_map_pos(p+m-fs,q+n-fs)-...
                    (events_positive(p+m-fs,q+n-fs)-mean_rate_pos)*alpha;
                    threshold_map_neg(p+m-fs,q+n-fs)=...
                    threshold_map_neg(p+m-fs,q+n-fs)-...
                    (events_negative(p+m-fs,q+n-fs)-mean_rate_neg)*alpha;
                end
            end
        end
    end
end

minimum=min(min(threshold_map_pos));
maximum=max(max(threshold_map_pos));
threshold_map_pos=(threshold_map_pos-minimum)./(maximum-minimum).*0.1+1.5;


minimum=min(min(threshold_map_neg));
maximum=max(max(threshold_map_neg));
threshold_map_neg=(threshold_map_neg-minimum)./(maximum-minimum).*0.1+1.5;

% reflect the threshold map coordinate system from matlab convention to DVS
% convention
tmp=zeros(img_size);
tmn=zeros(img_size);
for i=1:img_size
    for j=1:img_size
        tmp(129-i,j)=threshold_map_pos(i,j);
        tmn(129-i,j)=threshold_map_neg(i,j);
    end
end

figure(1)
imagesc(tmp)
figure(2)
imagesc(tmn)

dlmwrite('t_map_p.txt',tmp,'delimiter',' ','newline','pc')
dlmwrite('t_map_n.txt',tmn,'delimiter',' ','newline','pc')