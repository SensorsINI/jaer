package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_PencilLineTracker extends EventProcessor {

	/* ***************************************************************************************************** */
	/* **  The following stuff we need to compute line tracking and desired table position ******************* */
	/* ***************************************************************************************************** */
	private double polyAX,  polyBX,  polyCX,  polyDX,  polyEX;
	private double currentBaseX,  currentSlopeX;
	private static final double polyDecay = 0.98;
	private static final double polyStddev = 4.0;

	private int polyAXI,  polyBXI,  polyCXI,  polyDXI,  polyEXI;
	private int currentBaseXI,  currentSlopeXI;

	private final int shiftPA = 11;							// how many bits to shift?
	private final int shiftPB = 17;
	private final int shiftPC = 25;
	private final int shiftPD = 10;
	private final int shiftPE = 17;

	private final int shiftBase = 12;
	private final int shiftSlope = 12;						// must be more than or equal to base!!

	private final int shiftNom = -4;						// shift for numerator
	private final int shiftDenom = 2;						// shift for denominator

	private static final int shiftPolyDecay = 20;
	private static final int shiftWeightDecay = 20;

	private final int mulB = (1<<shiftBase);
	private final int mulS = (1<<shiftSlope);

	private int lpcSlopeI, lpcBaseI;

	private static int convertDouble(double d, int s, String id) {
		long cl;
		int ci;
		cl = (long) Math.round(d*((double) (1<<s)));
		ci = (int)  Math.round(d*((double) (1<<s)));
		if (cl!=ci) {
			System.out.printf("Warning: convert Double (%s) clipped result. Input %12.8f, shift %s,  l(%14d),  i(%14d)\n", id, d, s, cl, ci);
		}
		return(ci);
	}


    private void updateCurrentEstimateX() {
        double denominator;
        denominator = 1.0 / (4.0 * polyAX * polyCX - polyBX * polyBX);
        if (denominator != 0.0) {
            currentBaseX  = (polyDX * polyBX - 2.0 * polyAX * polyEX) * denominator;
            currentSlopeX = (polyBX * polyEX - 2.0 * polyCX * polyDX) * denominator;
        }

//      int denominatorI = (4*polyAXI*polyCXI)/(1<<(shiftPA+shiftPC-shiftD)) - polyBXI*polyBXI / (1<<(shiftPB+shiftPB-shiftD));
        int d1 = mulshift32(polyAXI, polyCXI, (shiftPA+shiftPC-shiftDenom-2), "d1");		// -2 for 4x multiplication
        int d2 = mulshift32(polyBXI, polyBXI, (shiftPB+shiftPB-shiftDenom), "d2");
        int denominatorI =  d1 - d2;

    	int denominatorIBase = denominatorI>>(-shiftNom+shiftDenom+shiftBase);				// these only differ in the number of shift bits... maybe keep as one?
    	int denominatorISlope = denominatorI>>(-shiftNom+shiftDenom+shiftSlope);

//      currentBaseXI  = (polyDXI * polyBXI - 2 * polyAXI * polyEXI) / denominatorI;
//      currentSlopeXI = (polyBXI * polyEXI - 2 * polyCXI * polyDXI) / denominatorI;

		int num1, num2;
		if (denominatorIBase != 0) {
        	num1 = mulshift32(polyDXI, polyBXI, (shiftPD+shiftPB-shiftNom), "numBase1");
        	num2 = mulshift32(polyAXI, polyEXI, (shiftPA+shiftPE-shiftNom-1), "numBase2");
        	currentBaseXI  = ((num1 - num2) / denominatorIBase);
		}
		if (denominatorISlope != 0) {
        	num1 = mulshift32(polyBXI, polyEXI, (shiftPB+shiftPE-shiftNom), "numSlope1");
        	num2 = mulshift32(polyCXI, polyDXI, (shiftPC+shiftPD-shiftNom-1), "numSlope2");
        	currentSlopeXI = ((num1 - num2) / denominatorISlope);
        }
    }
    private void polyAddEventX(int x, int y, int t) { // x,y in pixels, t in microseconds

    	updateCurrentEstimateX();

		// short = 16bit
        // int = 32bit
        // long = 64bit
//if (1==0) {
        double proposedX = currentBaseX + y * currentSlopeX;
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));
        
        double dec = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));
        polyAX = dec * polyAX + weight * (y * y);
        polyBX = dec * polyBX + weight * (2.0 * y);
        polyCX = dec * polyCX + weight * (1.0);
        polyDX = dec * polyDX + weight * (-2.0 * x * y);
        polyEX = dec * polyEX + weight * (-2.0 * x);


        long proposedXL = (currentBaseXI) + ((  y * currentSlopeXI) >> (shiftSlope-shiftBase));
        int  proposedXI = (currentBaseXI) + ((  y * currentSlopeXI) >> (shiftSlope-shiftBase));
        if (proposedXL != proposedXI) {
        	System.out.println("Warning: Conversion of proposed Slope clipped");
        }

        int errorI = (x<<shiftBase) - proposedXI;
        if (errorI<0) {
        	errorI = -errorI;
        }
        errorI = errorI >> (shiftBase-4);			// now the error is shifted by 4 (ie 1/16 per increment)
        if (errorI>(16*20-1)) {
        	errorI=(16*20-1);
        }

        int weightI = lookupTable[errorI][0];
        int decayI = lookupTable[errorI][1];

//        int w = convertDouble(weight, shiftWeightDecay, "Weight");
//System.out.printf("Error: %10d  %10d  diff: %10d\n", weightI, w, (weightI-w));

//        int weightI = convertDouble(weight, shiftWeightDecay, "Weight");
//        int decayI = convertDouble(dec, shiftPolyDecay, "Decay");

        polyAXI = mulshift32(decayI, polyAXI, shiftPolyDecay, "polyAXIDecay");
        polyAXI += mulshift32(weightI, y*y, (shiftWeightDecay-shiftPA), "polyAXIadd");

		polyBXI = mulshift32(decayI, polyBXI, shiftPolyDecay, "polyBXIDecay");
        polyBXI += mulshift32(weightI, y, (shiftWeightDecay-shiftPB-1), "polyBXIadd");		// -1 for 2*y

//        polyCXI = mulshift32(decayI, polyCXI, shiftPolyDecay, "polyCXIDecay");					// REMOVE C FROM COMPUTATION - IT'S CONSTANT
//        polyCXI += mulshift32(weightI, 1, (shiftWeightDecay-shiftPC), "polyCXIadd");
//        polyCXI = 50<<(shiftPC);
//        System.out.printf("PolyC: %12.8f PolyCXI: %10d -- %10d  ", polyCX, polyCXI, (1<<(shiftPC-1)));

        polyDXI = mulshift32(decayI, polyDXI, shiftPolyDecay, "polyDXIDecay");
        polyDXI += mulshift32(weightI, -x*y, (shiftWeightDecay-shiftPD-1), "polyDXIadd");	// -1 for -2*x*y

        polyEXI = mulshift32(decayI, polyEXI, shiftPolyDecay, "polyEXIDecay");
        polyEXI += mulshift32(weightI, -x, (shiftWeightDecay-shiftPE-1), "polyEXIadd");		// -1 for -2*x
//}
    }
    private void resetPolynomial() {
        polyAX = 0.0;
        polyBX = 0.0;
        polyCX = 0.0;
        polyDX = 0.0;
        polyEX = 0.0;
//        polyFX = 0.0;

        // add two "imaginary" events to filter, resulting in an initial vertical line
          double x, y;
        // add point 64/0
        x = 64;
        y = 0;
        polyAX += (y * y);
        polyBX += (2.0 * y);
        polyCX += (1.0);
        polyDX += (-2.0 * x * y);
        polyEX += (-2.0 * x);
//        polyFX += (x * x);
        
        // add point 64/127
        x = 64;
        y = 127;
        polyAX += (y * y);
        polyBX += (2.0 * y);
        polyCX += (1.0);
        polyDX += (-2.0 * x * y);
        polyEX += (-2.0 * x);
//        polyFX += (x * x);

//        for (long n=0; n<10000; n++) {
//        	polyAddEventX(64,  10, 0);
//        	polyAddEventX(64, 110, 0);
//        }

        polyAXI = convertDouble(polyAX, shiftPA, "A");			// this will change sometimes soon :)
        polyBXI = convertDouble(polyBX, shiftPB, "B");
        polyCXI = convertDouble(polyCX, shiftPC, "C");
        polyDXI = convertDouble(polyDX, shiftPD, "D");
        polyEXI = convertDouble(polyEX, shiftPE, "E");

//        polyCX = 50.0;
//        polyCXI = 50<<(shiftPC);
        
        polyAXI=   630846060;			// centered vertical line!
        polyBXI=   793051797;
        polyCXI=  1677721599;
        polyDXI=  -396525898;
        polyEXI=  -838860799;
        
//        System.out.printf("\n");
//        System.out.printf("polyA=%12d;\n", polyAXI);
//        System.out.printf("polyB=%12d;\n", polyBXI);
//        System.out.printf("polyC=%12d;\n", polyCXI);
//        System.out.printf("polyD=%12d;\n", polyDXI);
//        System.out.printf("polyE=%12d;\n", polyEXI);

        updateCurrentEstimateX();
    }

	private int mulshift32(int a, int b, int s, String id) {
		long resL;
		int resI;
		resL = ((long) a) * ((long) b);
		resL = resL >> s;
    	resI = (int) resL;
    	if (resI != resL) {
    		System.out.printf("Warning: mulshift (%s) clipped result. Input %12d * %12d, shift %3d, l(%14d), i(%14d)\n", id, a, b, s, resL, resI);
    	}
    	return(resI);
	}

	public static void generateWeightDecayLookup() {

		double error, weight, decay;
		for (error=0; error < 20; error = error+(1.0/16.0)) {		// 20*16 values of 2 numbers each

			weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));
			decay = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));

			int weightI = convertDouble(weight, shiftWeightDecay, "weight");
			int decayI = convertDouble(decay, shiftPolyDecay, "decay");

			System.out.printf("{%12d, %12d},\n", weightI, decayI);		// for Java
		}

	}
	private int[][] lookupTable = {
			{     1048576,      1027604},
			{     1048448,      1027607},
			{     1048064,      1027615},
			{     1047425,      1027628},
			{     1046530,      1027645},
			{     1045381,      1027668},
			{     1043978,      1027696},
			{     1042323,      1027730},
			{     1040416,      1027768},
			{     1038259,      1027811},
			{     1035854,      1027859},
			{     1033202,      1027912},
			{     1030305,      1027970},
			{     1027166,      1028033},
			{     1023786,      1028100},
			{     1020168,      1028173},
			{     1016315,      1028250},
			{     1012229,      1028331},
			{     1007913,      1028418},
			{     1003371,      1028509},
			{      998606,      1028604},
			{      993620,      1028704},
			{      988419,      1028808},
			{      983004,      1028916},
			{      977380,      1029028},
			{      971552,      1029145},
			{      965522,      1029266},
			{      959295,      1029390},
			{      952876,      1029518},
			{      946269,      1029651},
			{      939479,      1029786},
			{      932509,      1029926},
			{      925365,      1030069},
			{      918052,      1030215},
			{      910574,      1030365},
			{      902936,      1030517},
			{      895145,      1030673},
			{      887203,      1030832},
			{      879118,      1030994},
			{      870893,      1031158},
			{      862535,      1031325},
			{      854049,      1031495},
			{      845439,      1031667},
			{      836712,      1031842},
			{      827873,      1032019},
			{      818928,      1032197},
			{      809881,      1032378},
			{      800739,      1032561},
			{      791507,      1032746},
			{      782190,      1032932},
			{      772794,      1033120},
			{      763325,      1033310},
			{      753787,      1033500},
			{      744187,      1033692},
			{      734530,      1033885},
			{      724822,      1034080},
			{      715067,      1034275},
			{      705271,      1034471},
			{      695439,      1034667},
			{      685578,      1034864},
			{      675691,      1035062},
			{      665784,      1035260},
			{      655862,      1035459},
			{      645930,      1035657},
			{      635993,      1035856},
			{      626057,      1036055},
			{      616125,      1036253},
			{      606203,      1036452},
			{      596295,      1036650},
			{      586406,      1036848},
			{      576539,      1037045},
			{      566701,      1037242},
			{      556895,      1037438},
			{      547124,      1037634},
			{      537394,      1037828},
			{      527708,      1038022},
			{      518070,      1038215},
			{      508484,      1038406},
			{      498953,      1038597},
			{      489482,      1038786},
			{      480073,      1038975},
			{      470730,      1039161},
			{      461456,      1039347},
			{      452255,      1039531},
			{      443129,      1039713},
			{      434081,      1039894},
			{      425114,      1040074},
			{      416230,      1040251},
			{      407433,      1040427},
			{      398724,      1040602},
			{      390106,      1040774},
			{      381581,      1040944},
			{      373152,      1041113},
			{      364819,      1041280},
			{      356586,      1041444},
			{      348453,      1041607},
			{      340423,      1041768},
			{      332496,      1041926},
			{      324675,      1042082},
			{      316961,      1042237},
			{      309354,      1042389},
			{      301856,      1042539},
			{      294468,      1042687},
			{      287190,      1042832},
			{      280024,      1042976},
			{      272970,      1043117},
			{      266029,      1043255},
			{      259201,      1043392},
			{      252487,      1043526},
			{      245887,      1043658},
			{      239400,      1043788},
			{      233028,      1043915},
			{      226770,      1044041},
			{      220627,      1044163},
			{      214597,      1044284},
			{      208681,      1044402},
			{      202879,      1044518},
			{      197190,      1044632},
			{      191614,      1044744},
			{      186150,      1044853},
			{      180797,      1044960},
			{      175556,      1045065},
			{      170425,      1045168},
			{      165403,      1045268},
			{      160491,      1045366},
			{      155686,      1045462},
			{      150988,      1045556},
			{      146396,      1045648},
			{      141909,      1045738},
			{      137526,      1045825},
			{      133246,      1045911},
			{      129068,      1045995},
			{      124990,      1046076},
			{      121012,      1046156},
			{      117131,      1046233},
			{      113347,      1046309},
			{      109659,      1046383},
			{      106065,      1046455},
			{      102563,      1046525},
			{       99153,      1046593},
			{       95833,      1046659},
			{       92602,      1046724},
			{       89457,      1046787},
			{       86399,      1046848},
			{       83424,      1046908},
			{       80532,      1046965},
			{       77722,      1047022},
			{       74991,      1047076},
			{       72339,      1047129},
			{       69763,      1047181},
			{       67263,      1047231},
			{       64836,      1047279},
			{       62482,      1047326},
			{       60198,      1047372},
			{       57984,      1047416},
			{       55838,      1047459},
			{       53758,      1047501},
			{       51742,      1047541},
			{       49791,      1047580},
			{       47901,      1047618},
			{       46071,      1047655},
			{       44301,      1047690},
			{       42588,      1047724},
			{       40932,      1047757},
			{       39330,      1047789},
			{       37782,      1047820},
			{       36286,      1047850},
			{       34840,      1047879},
			{       33444,      1047907},
			{       32096,      1047934},
			{       30795,      1047960},
			{       29540,      1047985},
			{       28328,      1048009},
			{       27160,      1048033},
			{       26034,      1048055},
			{       24948,      1048077},
			{       23901,      1048098},
			{       22893,      1048118},
			{       21922,      1048138},
			{       20988,      1048156},
			{       20088,      1048174},
			{       19222,      1048192},
			{       18389,      1048208},
			{       17587,      1048224},
			{       16817,      1048240},
			{       16076,      1048254},
			{       15364,      1048269},
			{       14680,      1048282},
			{       14024,      1048296},
			{       13393,      1048308},
			{       12787,      1048320},
			{       12206,      1048332},
			{       11649,      1048343},
			{       11114,      1048354},
			{       10601,      1048364},
			{       10109,      1048374},
			{        9638,      1048383},
			{        9187,      1048392},
			{        8754,      1048401},
			{        8340,      1048409},
			{        7944,      1048417},
			{        7564,      1048425},
			{        7201,      1048432},
			{        6854,      1048439},
			{        6522,      1048446},
			{        6204,      1048452},
			{        5900,      1048458},
			{        5610,      1048464},
			{        5333,      1048469},
			{        5068,      1048475},
			{        4816,      1048480},
			{        4574,      1048485},
			{        4344,      1048489},
			{        4125,      1048494},
			{        3915,      1048498},
			{        3715,      1048502},
			{        3525,      1048506},
			{        3344,      1048509},
			{        3171,      1048513},
			{        3006,      1048516},
			{        2849,      1048519},
			{        2700,      1048522},
			{        2558,      1048525},
			{        2422,      1048528},
			{        2294,      1048530},
			{        2171,      1048533},
			{        2055,      1048535},
			{        1945,      1048537},
			{        1839,      1048539},
			{        1740,      1048541},
			{        1645,      1048543},
			{        1555,      1048545},
			{        1469,      1048547},
			{        1388,      1048548},
			{        1311,      1048550},
			{        1238,      1048551},
			{        1169,      1048553},
			{        1104,      1048554},
			{        1041,      1048555},
			{         983,      1048556},
			{         927,      1048557},
			{         874,      1048559},
			{         824,      1048560},
			{         777,      1048560},
			{         732,      1048561},
			{         689,      1048562},
			{         649,      1048563},
			{         611,      1048564},
			{         575,      1048564},
			{         542,      1048565},
			{         510,      1048566},
			{         479,      1048566},
			{         451,      1048567},
			{         424,      1048568},
			{         398,      1048568},
			{         374,      1048569},
			{         352,      1048569},
			{         330,      1048569},
			{         310,      1048570},
			{         291,      1048570},
			{         273,      1048571},
			{         257,      1048571},
			{         241,      1048571},
			{         226,      1048571},
			{         212,      1048572},
			{         198,      1048572},
			{         186,      1048572},
			{         174,      1048573},
			{         163,      1048573},
			{         153,      1048573},
			{         143,      1048573},
			{         134,      1048573},
			{         125,      1048573},
			{         117,      1048574},
			{         110,      1048574},
			{         103,      1048574},
			{          96,      1048574},
			{          90,      1048574},
			{          84,      1048574},
			{          78,      1048574},
			{          73,      1048575},
			{          68,      1048575},
			{          64,      1048575},
			{          60,      1048575},
			{          56,      1048575},
			{          52,      1048575},
			{          48,      1048575},
			{          45,      1048575},
			{          42,      1048575},
			{          39,      1048575},
			{          36,      1048575},
			{          34,      1048575},
			{          32,      1048575},
			{          29,      1048575},
			{          27,      1048575},
			{          26,      1048575},
			{          24,      1048576},
			{          22,      1048576},
			{          21,      1048576},
			{          19,      1048576},
			{          18,      1048576},
			{          16,      1048576},
			{          15,      1048576},
			{          14,      1048576},
			{          13,      1048576},
			{          12,      1048576},
			{          11,      1048576},
			{          11,      1048576},
			{          10,      1048576},
			{           9,      1048576},
			{           8,      1048576},
			{           8,      1048576},
			{           7,      1048576},
			{           7,      1048576},
			{           6,      1048576},
			{           6,      1048576},
			{           5,      1048576},
			{           5,      1048576},
			{           5,      1048576},
			{           4,      1048576}
	};

	
	public void init() {
		resetPolynomial();
		isActive.setText("PencilLineTracker");
	}
	public int processNewEvent(int eventX, int eventY, int eventP) {
		polyAddEventX(eventX, eventY, 0);
        return(0);
	}

	public void processSpecialData(String specialData) {
		if ((specialData.length()) == 8) {
			lpcSlopeI = (((specialData.charAt(0))-32)<<18) | (((specialData.charAt(1))-32)<<12) | (((specialData.charAt(2))-32)<<6) | ((specialData.charAt(3))-32);
			lpcBaseI  = (((specialData.charAt(4))-32)<<18) | (((specialData.charAt(5))-32)<<12) | (((specialData.charAt(6))-32)<<6) | ((specialData.charAt(7))-32);
			if ((lpcSlopeI & (1<<23))!=0) {
				lpcSlopeI |= (0xFF000000);
			}
			if ((lpcBaseI & (1<<23))!=0) {
				lpcBaseI  |= (0xFF000000);
			}
		}
	}

	public void paintComponent(Graphics g) {
		updateCurrentEstimateX();
		double lowX  = currentBaseX +   0.0 * currentSlopeX;
		double highX = currentBaseX + 127.0 * currentSlopeX;
		g.setColor(Color.cyan);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);

		//System.out.println("CurrentBase/SlopeXI: " + currentBaseXI + "     " + currentSlopeXI);
		lowX  = (((double) currentBaseXI)/((double) mulB)) + (  0.0 * ((double) currentSlopeXI)) / ((double) (mulS));
		highX = (((double) currentBaseXI)/((double) mulB)) + (127.0 * ((double) currentSlopeXI)) / ((double) (mulS));
		g.setColor(Color.yellow);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);
		g.drawLine(4*((int) lowX)-1, 0, 4*((int) highX)-1, 4*127);
		g.drawLine(4*((int) lowX)-2, 0, 4*((int) highX)-2, 4*127);
		g.drawLine(4*((int) lowX)+1, 0, 4*((int) highX)+1, 4*127);
		g.drawLine(4*((int) lowX)+2, 0, 4*((int) highX)+2, 4*127);

		lowX  = ((((double) lpcBaseI)/((double) mulB)) + (127.0 * ((double) lpcSlopeI)) / ((double) (mulS)));
		highX = ((((double) lpcBaseI)/((double) mulB)) + (  0.0 * ((double) lpcSlopeI)) / ((double) (mulS)));

//		if (!(flipX.isSelected())) {
//			lowX = 127-lowX;
//			highX = 127-highX;
//		}

		g.setColor(Color.pink);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);
		g.drawLine(4*((int) lowX)-1, 0, 4*((int) highX)-1, 4*127);
		g.drawLine(4*((int) lowX)-2, 0, 4*((int) highX)-2, 4*127);
		g.drawLine(4*((int) lowX)+1, 0, 4*((int) highX)+1, 4*127);
		g.drawLine(4*((int) lowX)+2, 0, 4*((int) highX)+2, 4*127);
	}

	public void callBackButtonPressed(ActionEvent e) {
		System.out.println("CallBack!");
	}
}
