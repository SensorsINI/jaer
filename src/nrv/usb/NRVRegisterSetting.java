package nrv.usb;

/**
 * One I2C register write (or wait pseudo-entry) from an NRV bias settings file.
 *
 * @see https://nrv.kr/
 */
public class NRVRegisterSetting {

    public static final int WAIT_REG_ADDR = -1;

    private final int slaveAddr;
    private final int regAddr;
    private int value;
    private final String comment;
    private boolean applied;

    public NRVRegisterSetting(int slaveAddr, int regAddr, int value, String comment) {
        this.slaveAddr = slaveAddr;
        this.regAddr = regAddr;
        this.value = value;
        this.comment = comment == null ? "" : comment;
        this.applied = false;
    }

    public static NRVRegisterSetting waitSetting(int waitMs, String comment) {
        return new NRVRegisterSetting(0, WAIT_REG_ADDR, waitMs, comment);
    }

    public boolean isWait() {
        return regAddr == WAIT_REG_ADDR;
    }

    public int getSlaveAddr() {
        return slaveAddr;
    }

    public int getRegAddr() {
        return regAddr;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getComment() {
        return comment;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    @Override
    public String toString() {
        if (isWait()) {
            return String.format("wait %d ms%s", value, comment.isEmpty() ? "" : " // " + comment);
        }
        return String.format("%02x:%04x=%02x%s", slaveAddr, regAddr, value & 0xff,
                comment.isEmpty() ? "" : " // " + comment);
    }
}
