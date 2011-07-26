package net.sf.jaer.hardwareinterface.serial.edvsviewer;

public class Tools_Test {

	private long div(long num, long den) {
		long res, sb;
		long sign=1;

		res = 0;
		if (num<0) {
			num=-num;
			if (den>0) {
				sign=-1;
			} else {
				den=-den;
			}
		} else {
			if (den<0) {
				sign=-1;
				den=-den;
			}
		}

		if (num!=0) {
			for (sb=31; sb>=0; sb--) {
				res = res<<1;
				if ((num>>sb) >= den) {
					res = res + 1;
					num = num - (den<<(sb));
				}
				if (num==0) {
					res <<= sb;
					break;
				}
			}
		}

		if (sign==-1) return(-(res));
		return(res);
	}

	public Tools_Test() {
		long l1, l2;
		long n;

		for (l1=-400; l1<400; l1++) {
			for (l2=-399; l2<400; l2 += 2) {

				double resD = ((double) l1)/((double) l2);
				if (resD<0) {
					n = (long) Math.ceil(resD);
				} else {
					n = (long) Math.floor(resD);
				}
				
				long res = div(l1, l2);
				
				if (n != res) {
					System.out.printf (" %3d / %3d  =  %3d  ?=?  %3d\n", l1, l2, n, res);
				}
			}
		}
		System.out.println("Test done");

	}
}
