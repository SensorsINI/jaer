/*
Class that keeps track of state of state machine.  For usage, see, for example, {@link ada.floor.Ball}.
 
 * $Id: StateMachineStates.java,v 1.6 2004/04/27 15:58:32 tobi Exp $
 *@Since $Revision: 1.6 $
 
 *
 */

package net.sf.jaer.util;


/**
 *Class that keeps track of state of state machine, allowing access to previous state, time since state changed, defining an initial state.
 Use it like this:
 <pre>
     enum StateMachineStates{INITIAL, MIDDLE, FINAL} // define states
    class S extends StateMachineStates{ // extends StateMachineStates and implement the getInitial method
        StateMachineStates state=StateMachineStates.INITIAL;
        public Enum getInitial(){
            return StateMachineStates.INITIAL;
        }
    }
 </pre>
 Use the enum to define the states and then the set and get methods to set and get the state.
 <pre>
    S s=new S();
    s.set(StateMachineStates.MIDDLE);
    s.getPrevious();
    s.timeSinceChanged();
</pre>
 * @author tobi
 */
public abstract class StateMachineStates  {
    
    /** Defines the default initial state
     @return the initial state
     */
    abstract public Enum getInitial();
    
    /** initial state of machine: {@link Integer#MIN_VALUE} */
    private Enum currentState = getInitial(), previousState = getInitial();
    private long timeChanged;
    private boolean wasChanged = false;
//    HashMap<Enum,String> stateToNameMap=new HashMap<Enum,String>();
//    HashMap<String,Enum> nameToStateMap=new HashMap<String,Enum>();
    
    /** Create a new state machine with defined initial state.
     @param initialState a new initial state
     */
    public StateMachineStates(Enum initialState) {
        this.set(initialState);
    }
    
    /** Create a new state machine.
     */
    public StateMachineStates() {
        set(getInitial());
    }
    
    /** set a possibly new currentState. If state didn't change, don't do anything except
     * arrange that next time {@link #isChanged} is called, it returns <code>false</code>.
     * @param newState the new state to be in
     */
    public void set(Enum newState) {
        if (currentState == getInitial()) {
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
    public Enum get() {
        return currentState;
    }
    
    /** return previous state, before it was changed.
     */
    public Enum getPrevious() {
        return previousState;
    }
    
    /** has state been initialized or set away from it's {@link Integer#MIN_VALUE} initial state? */
    public boolean isInitialized(){
        return currentState!=getInitial();
    }
    
//    public Object clone(){
//        Object o=null;
//        try{
//            o=super.clone();
//        }catch(CloneNotSupportedException e){
//            System.err.println("can't clone StateMachineStates");
//            System.exit(1);
//        }
//        return o;
//    }
//    
//    /** gives state a String name
//     *@param name string to name current state
//     **/
//    public void setName(String name){
//        stateToNameMap.put(get(),name);
//        nameToStateMap.put(name,get());
//    }
//    
//    /** gets the name of the current state. This may not have been set by anyone.
//     *@return name
//     *@see #setName
//     *@see #getStateByName
//     */
//    public String getName(){
//        if(stateToNameMap==null) return get().toString();
//        return stateToNameMap.get(get());
//    }
    
//    /** gets the state Integer by name of state
//     *@return Integer of state
//     *@param name the name assigned before
//     *@see #setName
//     */
//    public Enum getStateByName(String name){
//        if(nameToStateMap==null) return null;
//        return nameToStateMap.get(name);
//    }
    
    /** @return name of state (enum itself, unless overridden) */
    public String toString(){
        return get().toString();
    }
    
    public boolean equals(StateMachineStates other){
        return get()==other.get();
    }
    
} // StateMachineStates class

