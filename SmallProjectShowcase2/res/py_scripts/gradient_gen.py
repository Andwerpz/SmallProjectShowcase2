from matplotlib import pyplot as plt
import matplotlib 
import numpy as np

plt.figure()
a=np.outer(np.arange(0,1,0.01),np.ones(10))
cdict2 = {'red':   [(0.0,  0.0, 0.0),
                   (0.5,  1.0, 1.0),
                   (1.0,  1.0, 1.0)],
         'green': [(0.0,  0.0, 0.0),
                   (0.25, 0.0, 0.0),
                   (0.75, 1.0, 1.0),
                   (1.0,  1.0, 1.0)],
         'blue':  [(0.0,  0.0, 0.0),
                   (0.5,  0.0, 0.0),
                   (1.0,  1.0, 1.0)]} 
my_cmap2 = matplotlib.colors.LinearSegmentedColormap('my_colormap2',cdict2,256)
plt.imshow(a,aspect='auto', cmap =my_cmap2)                   
plt.show()