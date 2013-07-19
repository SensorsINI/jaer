package ch.unizh.ini.jaer.config.onchip;


import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.AbstractConfigBit;


/** One bit of extra configuration */
public class OnchipConfigBit extends AbstractConfigBit implements HasPreference {

    private int position;

   /** Makes a new on-chip extra configuration bit.
     * 
     * @param name label
     * @param position along shift register. Each loaded bit produces complementary output pair that is tapped off inside chip. We load positive (uncomplemented) value here.
     * @param tip tool-tip and hint
     */
    public OnchipConfigBit(Chip chip, String name, int position, String tip, boolean def) {
        super(chip, name, tip, def);
        this.position = position;
    }

    /**
     * @return the position
     */
    public int getPosition() {
        return position;
    }

    /**
     * @param position the position to set
     */
    public void setPosition(int position) {
        this.position = position;
    }

 
}
