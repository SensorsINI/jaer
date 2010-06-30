/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * Implements the physics of the slot-car race. Implements the movement of the car,
 * takes care of friction, and computes when the car flies off the track.
 * @author Michael Pfeiffer
 */
public class SlotcarPhysics {

    // Track friction
    protected double friction;

    // Mass of car
    protected double carMass;

    // Length of car
    protected double carLength;

    // Height of car center-of-mass
    protected double comHeight;

    // Maximum force allowed before car flies off the track
    private double maxOutwardForce;

    // Factor to correct oblique orientation
    protected double orientationCorrectFactor;

    // Engine force for acceleration
    protected double engineForce;

    // Moment of inertia for rotations around guide
    protected double momentInertia;

    // Drag coefficient of the car
    protected double dragCoefficient;

    public SlotcarPhysics(double friction, double carMass, double carLength,
            double comHeight, double momentInertia, 
            double orientationCorrectFactor, double engineForce,
            double dragCoefficient) {
        this.friction = friction;
        this.carMass = carMass;
        this.carLength = carLength;
        this.comHeight = comHeight;
        this.momentInertia = momentInertia;
        this.orientationCorrectFactor = orientationCorrectFactor;
        this.engineForce = engineForce;
        this.dragCoefficient = dragCoefficient;

        computeMaxOutwardForce();
    }

    /**
     * Default constructor with default values for all forces and masses.
     */
    public SlotcarPhysics() {
        friction = 0.5;
        carMass = 0.1;
        carLength = 0.05;
        comHeight = 0.001;
        momentInertia = 0.1;
        orientationCorrectFactor = 0.5;
        engineForce = 0.25;
        dragCoefficient = 0.2;

        computeMaxOutwardForce();
    }

    public double getCarMass() {
        return carMass;
    }

    public void setCarMass(double carMass) {
        this.carMass = carMass;
        computeMaxOutwardForce();
    }

    public double getCarLength() {
        return carLength;
    }

    public void setCarLength(double carLength) {
        this.carLength = carLength;
        computeMaxOutwardForce();
    }

    public double getComHeight() {
        return comHeight;
    }

    public void setComHeight(double comHeight) {
        this.comHeight = comHeight;
        computeMaxOutwardForce();
    }

    public double getEngineForce() {
        return engineForce;
    }

    public void setEngineForce(double engineForce) {
        this.engineForce = engineForce;
    }

    public double getOrientationCorrectFactor() {
        return orientationCorrectFactor;
    }

    public void setOrientationCorrectFactor(double orientationCorrectFactor) {
        // this.orientationCorrectForce = orientationCorrectForce;
        this.orientationCorrectFactor = Math.min(0.0, Math.max(1.0, orientationCorrectFactor));
    }


    public double getFriction() {
        return friction;
    }

    public void setFriction(double friction) {
        this.friction = friction;
    }

    public double getMomentInertia() {
        return momentInertia;
    }

    public void setMomentInertia(double momentInertia) {
        this.momentInertia = momentInertia;
    }



    public double getMaxOutwardForce() {
        return maxOutwardForce;
    }



    private void computeMaxOutwardForce() {
        maxOutwardForce = carMass * carLength / (2.0 * comHeight);
    }


    private double computeCentrifugalForce(double curveRadius, double speed) {
        return (carMass * speed*speed / curveRadius);
    }

    /**
     * Computes the next state of the car.
     * @param curState The object containing the current state of the car
     * @param throttle Forward acceleration of the car
     * @param curveRadius Curvature radius of the track
     * @param curveDirection Direction of the curve (-1 = left, 1 = right)
     * @param dt Time step
     * @return Next state of the car
     */
    public SlotcarState nextState(SlotcarState curState, double throttle, double curveRadius,
            double curveDirection, double dt) {
        SlotcarState nextState = new SlotcarState(curState);

        // Forward acceleration
        double newSpeed = curState.speed + throttle*engineForce*dt / carMass;
        // Effect of friction and drag
        newSpeed -= carMass * 9.81 * friction * dt;
        newSpeed -= dragCoefficient * curState.speed*curState.speed * dt;
        newSpeed = Math.max(newSpeed, 0.0);
        nextState.speed = newSpeed;

        double centForce = computeCentrifugalForce(curveRadius, newSpeed);
        nextState.outwardForce = centForce;

        
        if (Math.abs(centForce) > this.maxOutwardForce) {
            // Car turns over and leaves track
            nextState.onTrack = false;
        }
        else {
            // Compute orientation of car

            double newOrientation = curState.relativeOrientation;
            double newAngularVelocity = curState.angularVelocity;
            // Straighten orientation of car
            newOrientation *= (1.0 - dt * orientationCorrectFactor);

            // Compute torque attacking at COM to change angular velocity
            // We assume that the COM is at the center of the car
            // We assume that half of the force is compensated by resistance of the guide
            double torque = (0.5 * centForce) * curveDirection * (0.5 * carLength);
            newAngularVelocity += torque * dt / momentInertia;

            newAngularVelocity *= (1.0 - dt * orientationCorrectFactor);

            // Rotate around guide
            newOrientation += dt * newAngularVelocity;

            nextState.angularVelocity = newAngularVelocity;
            nextState.relativeOrientation = newOrientation;
        }
        // System.out.println("outwardForce " + centForce);
        // System.out.println("orientation " + nextState.relativeOrientation);
        // System.out.println("angVelocity " + nextState.angularVelocity);

        return nextState;
    }
}
