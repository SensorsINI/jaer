#interpolate between opencv and jAER

import numpy as np
from scipy import interpolate


filename = 'DAVIS240B-2015-Federico_office_0.aedat_label.txt'

data = np.loadtxt(filename)
step = 30

min_ts = np.min(data[:,1])
max_ts = np.max(data[:,1])
all_ts = np.linspace(min_ts,max_ts, max_ts-min_ts)[::step] #one every then us

target_x = interpolate.interp1d(data[:,1], data[:,2])
target_y = interpolate.interp1d(data[:,1], data[:,3])
frame_ts = interpolate.interp1d(data[:,1], data[:,0])
dim_x = interpolate.interp1d(data[:,1], data[:,5])
dim_y = interpolate.interp1d(data[:,1], data[:,6])

all_x = target_x(all_ts)
all_y = target_y(all_ts)
all_f = frame_ts(all_ts)
all_dim_x = dim_x(all_ts)
all_dim_y = dim_y(all_ts)
all_target = np.repeat(0,len(all_ts))

final_label = [all_f, all_ts, all_x, all_y, all_target , all_dim_x, all_dim_y];

out_file = open("interpolated.txt","w")
# framenumber timestamp loc_x loc_y targetTypeID dimx dimy
for i in range(1000):
    out_file.write(str(int(all_f[i]))+"\t"+str(int(all_ts[i]))+"\t"+str(int(all_x[i]))+"\t"+str(int(all_y[i]))+"\t"+str(int(all_target[i]))+"\t"+str(int(all_dim_x[i]))+"\t"+str(int(all_dim_y[i]))+"\n")

out_file.close()
