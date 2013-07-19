//package ch.unizh.ini.jaer.projects.poseestimation;
//



















//
///**
// *
// * @author Haza
// */
//@Description("Motion Parralax for Distance estimation in 1 Dimension along x axis")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
//public class MotionParralax1D  extends RectangularClusterTracker implements FrameAnnotater, Observer {
//    
//    // Controls
//    protected double distanceFromCameraToRotationPointCm = getPrefs().getDouble("MotionParralax1D.distanceFromCameraToRotationPointCm", 8.5);
//    {
//        setPropertyTooltip("distanceFromCameraToRotationPointCm", "Distance from camera to point of rotation in cm");
//    }
//
//    // Internal variables
//    int[][] initialPosition = new int[1][1];    // Initial Location of Cluster
//    int[][] finalPosition = new int[1][1];      // Final Location of Cluster 
//    double angle = 0;                           // Camera Rotation Angle (also, Rotation Angle betyween initial and final cluster location)
//    double estimatedDistance = 0;               // Estimated Distance of cluster center to camera lens 
//    
//    // Drawing Points 
//
//    /**
//     * Constructor
//     * @param chip Called with AEChip properties
//     */
//    public MotionParralax1D(AEChip chip) {
//        super(chip);
//        chip.addObserver(this);
//        initFilter();
//    } // END CONSTRUCTOR
//    
//    /**
//     * Called on creation
//     */    
//    @Override
//    public void initFilter() {
//        super.initFilter(); // call initFilter from RectangularClusterTracker
//    } // END METHOD
//
//    /**
//     * Called on filter reset
//     */    
//    @Override
//    public void resetFilter() {
//        super.resetFilter(); // call resetFilter from RectangularClusterTracker
//    } // END METHOD
//    
//    /**
//     * Called on changes of observable objects from the class
//     * @param o One of the objects being observed that was updated
//     * @param arg Message passed by o to be interpreted by filter if necessary
//     */    
//    @Override
//    public void update(Observable o, Object arg) {
//        initFilter();
//    } // END METHOD
//
//    /**
//     * Receives Packets of information and passes it onto processing
//     * @param in Input events can be null or empty.
//     * @return The filtered events to be rendered or written out
//     */
//    @Override
//    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
//        // Check for empty packet
//        if(in.getSize() == 0) 
//            return in;
//        // Check that filtering is in fact enabled
//        if(!filterEnabled) 
//            return in;
//        // If necessary, pre filter input packet 
//        if(enclosedFilter!=null) 
//            in=enclosedFilter.filterPacket(in);
//        calculateDistance(in.getLastTimestamp());
//        return in;
//    } // END METHOD
//
//    
//    /** 
//     * Annotation or drawing method
//     * @param drawable OpenGL Rendering Object
//     */
//    @Override
//    synchronized public void annotate(GLAutoDrawable drawable) {
//        super.annotate(drawable); // Draw annotations from RectangularClusterTracker
//    } // END METHOD
//    
//    /** 
//     * Calculate Approximate Distance of Cluster
//     * @param 
//     * @return 
//     */
//    private void calculateDistance(int t) {
//        int nn = getNumVisibleClusters();
//        if (nn == 0) {
//            return; // no visible clusters
//        } // END IF
//
//        // find cluster shifts and weight of each cluster, count visible clusters
//        // the clusters are born at different times. 
//        // therefore find the shift of each cluster as though
//        // it had lived as long as the oldest cluster. 
//        // then average all the clusters weighted by the number of events
//        // collected by each cluster.
//        int weightSum = 0;
//        int ageSum = 0;
//        nn = 0;
//        Cluster oldestCluster = null;
//        float avgxloc = 0, avgyloc = 0, avgxvel = 0, avgyvel = 0;
//        for (Cluster c : clusters) {
//            if (c.isVisible()) {
//                float weight = c.getMassNow(t);
//                weightSum += weight;
//                avgxloc += (c.getLocation().x - c.getBirthLocation().x) * weight;
//                avgyloc += (c.getLocation().y - c.getBirthLocation().y) * weight;
//                avgxvel += c.getVelocityPPT().x * weight;
//                avgyvel += c.getVelocityPPT().y * weight;
//                ageSum += c.getLifetime() * weight;
//                nn++;
//                if (oldestCluster == null || c.getBirthTime() < oldestCluster.getBirthTime()) {
//                    oldestCluster = c;
//                } // END IF
//            } // END IF
//        } // END FOR
//        if (weightSum == 0) {
//            return;
//        } // END IF
//        averageClusterAge = ageSum / weightSum;
//
//        // compute weighted-mean scene shift, but only if there is at least 1 visible cluster
//
//        if (nn > 0) {
//            avgxloc /= weightSum;
//            avgyloc /= weightSum;
//            velocityPPt.x = avgxvel / weightSum;
//            velocityPPt.y = avgyvel / weightSum;
//            avgyvel /= weightSum;
//            smallAngleTransformFinder.filterTransform(-avgxloc, -avgyloc, 0, t);
//        } 
////            System.out.println(String.format("x,y= %.1f , %.1f ",avgxloc,avgyloc));
//        } else { // using general transformEvent rather than weighted sum of cluster movements
//            smallAngleTransformFinder.update(t);
//        } // END IF
//    } // END METHOD
//    
//    
//    /**
//     * Getter for distanceFromCameraToRotationPointCm
//     * @return distanceFromCameraToRotationPointCm
//     */
//    public double getDistanceFromCameraToRotationPointCm() {
//        return this.distanceFromCameraToRotationPointCm;
//    }
//
//    /**
//     * Setter for distanceFromCameraToRotationPointCm
//     * @param distanceFromCameraToRotationPointCm distanceFromCameraToRotationPointCm
//     */
//    public void setDistanceFromCameraToRotationPointCm(final double distanceFromCameraToRotationPointCm) {
//        getPrefs().putDouble("MotionParralax1D.distanceFromCameraToRotationPointCm", distanceFromCameraToRotationPointCm);
//        getSupport().firePropertyChange("distanceFromCameraToRotationPointCm", this.distanceFromCameraToRotationPointCm, distanceFromCameraToRotationPointCm);
//        this.distanceFromCameraToRotationPointCm = distanceFromCameraToRotationPointCm;
//    }
//
//    public double getMinDistanceFromCameraToRotationPointCm() {
//        return 0.1;
//    }
//
//    public double getMaxDistanceFromCameraToRotationPointCm() {
//        return 100;
//    }
//    
//    
//    
//    
//    
//}