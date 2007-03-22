/*
Class that keeps track of state of state machine.  For usage, see, for example, {@link ada.floor.Ball}.
 
 * $Id: State.java,v 1.6 2004/04/27 15:58:32 tobi Exp $
 *@Since $Revision: 1.6 $
 
 *
 */

package ch.unizh.ini.caviar.util;

import java.util.HashMap;

/**
 *Class that keeps track of state of state machine.
 * @author tobi
 */
public class State implements Cloneable {
    
    // cloned from tobi's Ada State class and modified for enum states
    // not tested with enum states - not clear now to subclass with enum states
    
    protected enum States {INITIAL};
    
    /** initial state of machine: {@link Integer#MIN_VALUE} */
    public static final int STARTING_STATE=Integer.MIN_VALUE;
    private States currentState = States.INITIAL, previousState = States.INITIAL;
    private long timeChanged;
    private boolean wasChanged = false;
    HashMap<States,String> stateToNameMap=new HashMap<States,String>();
    HashMap<String,States> nameToStateMap=new HashMap<String,States>();
    
    /** Create a new state machine.
     */
    public State() {
        this(States.INITIAL);
    }
    
    /** Create a new state machine with defined initial state.
     */
    public State(States initialState) {
        this.set(initialState);
    }
    
    /** set a possibly new currentState. If state didn't change, don't do anything except
     * arrange that next time {@link #isChanged} is called, it returns <code>false</code>.
     * @param newState the new state to be in
     */
    public void set(States newState) {
        if (currentState == States.INITIAL) {
            currentState = newState;
            timeChanged = System.currentTimeMillis();
            wasChanged = true;
        }else if (newState != currentState) {
            previousState = currentState;
            currentState = newState;
            timeChanged = System.currentTimeMillis();
            wasChanged = true;
        }else {
            wasChanged = false;
        }
    }
    
    /** was the currentState changed the last time it was {@link #set}? */
    public boolean isChanged() {
        return wasChanged;
    }
    
    /** return time in ms since state last changed */
    public long timeSinceChanged() {
        return System.currentTimeMillis() - timeChanged;
    }
    
    /** Return present state
     */
    public States get() {
        return currentState;
    }
    
    /** return previous state, before it was changed.
     */
    public States getPrevious() {
        return previousState;
    }
    
    /** has state been initialized or set away from it's {@link #STARTING_STATE}? */
    public boolean isInitialized(){
        return currentState!=States.INITIAL;
    }
    
    public Object clone(){
        Object o=null;
        try{
            o=super.clone();
        }catch(CloneNotSupportedException e){
            System.err.println("can't clone State");
            System.exit(1);
        }
        return o;
    }
    
    /** gives state a String name
     *@param name string to name current state
     **/
    public void setName(String name){
        stateToNameMap.put(get(),name);
        nameToStateMap.put(name,get());
    }
    
    /** gets the name of the current state. This may not have been set by anyone.
     *@return name
     *@see #setName
     *@see #getStateByName
     */
    public String getName(){
        if(stateToNameMap==null) return null;
        return stateToNameMap.get(get());
    }
    
    /** gets the state Integer by name of state
     *@return Integer of state
     *@param name the name assigned before
     *@see #setName
     */
    public States getStateByName(String name){
        if(nameToStateMap==null) return null;
        return nameToStateMap.get(name);
    }
    
    /** @return e.g. "State 3" */
    public String toString(){
        return "State "+get();
    }
    
} // State class

