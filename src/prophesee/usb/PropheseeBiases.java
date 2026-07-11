package prophesee.usb;

/**
 * IMX636 bias values (idac_ctl bytes). Defaults from neuromorphic-drivers prophesee_evk4.
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeBiases {

    public int pr = 0x7C;
    public int fo = 0x53;
    public int hpf = 0x00;
    public int diffOn = 0x66;
    public int diff = 0x4D;
    public int diffOff = 0x49;
    public int inv = 0x5B;
    public int refr = 0x14;
    public int reqpuy = 0x8C;
    public int reqpux = 0x7C;
    public int sendreqpdy = 0x94;
    public int unknown1 = 0x74;
    public int unknown2 = 0x51;

    public PropheseeBiases copy() {
        final PropheseeBiases b = new PropheseeBiases();
        b.pr = pr;
        b.fo = fo;
        b.hpf = hpf;
        b.diffOn = diffOn;
        b.diff = diff;
        b.diffOff = diffOff;
        b.inv = inv;
        b.refr = refr;
        b.reqpuy = reqpuy;
        b.reqpux = reqpux;
        b.sendreqpdy = sendreqpdy;
        b.unknown1 = unknown1;
        b.unknown2 = unknown2;
        return b;
    }
}
