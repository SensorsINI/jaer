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
public class SlotcarPhysics implements java.io.Serializable {

    // Track friction
    protected float friction;

    // Mass of car
    protected float carMass;

    // Length of car
    protected float carLength;

    // Height of car center-of-mass
    protected float comHeight;

    // Maximum force allowed before car flies off the track
    private float maxOutwardForce;

    // Factor to correct oblique orientation
    protected float orientationCorrectFactor;

    // Engine force for acceleration
    protected float engineForce;

    // Moment of inertia for rotations around guide
    protected float momentInertia;

    // Drag coefficient of the car
    protected float dragCoefficient;

    public SlotcarPhysics(float friction, float carMass, float carLength,
            float comHeight, float momentInertia,
            float orientationCorrectFactor, float engineForce,
            float dragCoefficient) {
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
        friction = 0.5f;
        carMass = 0.1f;
        carLength = 0.05f;
        comHeight = 0.001f;
        momentInertia = 0.1f;
        orientationCorrectFactor = 0.5f;
        engineForce = 0.25f;
        dragCoefficient = 0.2f;

        computeMaxOutwardForce();
    }

    public float getCarMass() {
        return carMass;
    }

    public void setCarMass(float carMass) {
        this.carMass = carMass;
        computeMaxOutwardForce();
    }

    public float getCarLength() {
        return carLength;
    }

    public void setCarLength(float carLength) {
        this.carLength = carLength;
        computeMaxOutwardForce();
    }

    public float getComHeight() {
        return comHeight;
    }

    public void setComHeight(float comHeight) {
        this.comHeight = comHeight;
        computeMaxOutwardForce();
    }

    public float getEngineForce() {
        return engineForce;
    }

    public void setEngineForce(float engineForce) {
        this.engineForce = engineForce;
    }

    public float getOrientationCorrectFactor() {
        return orientationCorrectFactor;
    }

    public void setOrientationCorrectFactor(float orientationCorrectFactor) {
        // this.orientationCorrectForce = orientationCorrectForce;
        this.orientationCorrectFactor = (float)Math.min(0.0, Math.max(1.0, orientationCorrectFactor));
    }


    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public float getMomentInertia() {
        return momentInertia;
    }

    public void setMomentInertia(float momentInertia) {
        this.momentInertia = momentInertia;
    }



    public float getMaxOutwardForce() {
        return maxOutwardForce;
    }



    private void computeMaxOutwardForce() {
        maxOutwardForce = carMass * carLength / (2f * comHeight);
    }


    private float computeCentrifugalForce(float curveRadius, float speed) {
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
    public SlotcarState nextState(SlotcarState curState, float throttle, float curveRadius,
            float curveDirection, float dt) {
        SlotcarState nextState = new SlotcarState(curState);

        // Forward acceleration
        float newSpeed = curState.speed + throttle*engineForce*dt / carMass;
        // Effect of friction and drag
        newSpeed -= carMass * 9.81 * friction * dt;
        newSpeed -= dragCoefficient * curState.speed*curState.speed * dt;
        newSpeed = (float) Math.max(newSpeed, 0);
        nextState.speed = newSpeed;

        float centForce = computeCentrifugalForce(curveRadius, newSpeed);
        nextState.outwardForce = centForce;

        
        if (Math.abs(centForce) > this.maxOutwardForce) {
            // Car turns over and leaves track
            nextState.onTrack = false;
        }
        else {
            // Compute orientation of car

            float newOrientation = curState.relativeOrientation;
            float newAngularVelocity = curState.angularVelocity;
            // Straighten orientation of car
            newOrientation *= (1.0 - dt * orientationCorrectFactor);

            // Compute torque attacking at COM to change angular velocity
            // We assume that the COM is at the center of the car
            // We assume that half of the force is compensated by resistance of the guide
            float torque = (0.5f * centForce) * curveDirection * (0.5f * carLength);
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
