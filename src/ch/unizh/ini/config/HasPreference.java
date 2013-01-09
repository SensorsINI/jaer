package ch.unizh.ini.config;

   /** Marks a class (for example, some object in a subclass of Biasgen) as having a preference that can be loaded and stored. Classes do *not* store preferences unless
     * explicitly asked to do so. E.g. setters do not store preferences. Otherwise this can lead to an infinite loop of 
     * set/notify/set.
     */
    public interface HasPreference {

        public void loadPreference();

        public void storePreference();
    }