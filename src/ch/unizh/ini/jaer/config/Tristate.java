package ch.unizh.ini.jaer.config;

/** Used for tristate outputs */
public enum Tristate {

    High, Low, HiZ;

    public boolean isHigh() {
        return this == Tristate.High;
    }

    public boolean isLow() {
        return this == Tristate.Low;
    }

    public boolean isHiZ() {
        return this == Tristate.HiZ;
    }
}