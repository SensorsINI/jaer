package ch.unizh.ini.jaer.projects.hopfield.matrix;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import ch.unizh.ini.jaer.projects.hopfield.orientationlearn.TrainingData;

public class KohonenAlgorithm extends JPanel

{
	int vector_size = 8;
	tableCanvas tabcan = new tableCanvas();
	Statistics statist = new Statistics(1, "LEARNRATE");
	Statistics statist2 = new Statistics(1, "ERROR");
	FeatureMap fMap = new FeatureMap();
	Button clear_button = new Button("clear");
	Button vmat_button = new Button("view matrix");
	Button rec_button = new Button("recognize");
	Button learn_button = new Button("learn");
	Button reset_button = new Button("reset");
	Choice nr_cycles_choice = new Choice();
	Label cycles = new Label("0");
	Label cyclesText = new Label("Cycles done:");
	Label nr_cyclesText = new Label("Number of cycles:");
	Label recpattern = new Label("");
	Label recpattern_text = new Label("I recognized:");
	Label tabcanText = new Label("You wrote:");
	Legend legend = new Legend();
	int trainingNumber = 4;
	private Neuron inLayer[] = new Neuron[vector_size];		        //input-neurons layer
	private Neuron mapLayer[][] = new Neuron [16][16];	//map-neurons layer
	double letterMatrix[][];
	String letter[];
	int object;
	int rand;
	int bestx,besty;              // store best selected neuron here
	// variables for learning
	int max_radius,min_radius;
	double max_learnrate,min_learnrate;
	int learned_cycles,number_of_cycles,current_radius;
	double current_learnrate;
	int kernel_width = 16;
	int kernel_height = 16;
	float[][] vertical = new float[kernel_width][kernel_height];
	float[][] horizontal = new float[kernel_width][kernel_height];
	float[][] on_center = new float[kernel_width][kernel_height];
	float[][] off_center = new float[kernel_width][kernel_height];
	float[][] oblique_bottom = new float[kernel_width][kernel_height];
	float[][] oblique_top = new float[kernel_width][kernel_height];
	float[][] corners = new float[kernel_width][kernel_height];
	float[][][] detectorFilters = new float[7][][];
	public BufferedImage [] resultImages; 
	public void init()
	{
		for(int i=0; i<vector_size; i++)
			inLayer[i] = new Neuron(0,i);
		for(int i=0; i<=15; i++)
			for(int j=0; j<=15; j++)
				mapLayer[i][j] = new Neuron(i,j);
		// initiate variables
		object = 0;
		max_radius = 15;
		min_radius = 1;
		max_learnrate = 0.9;
		min_learnrate = 0.1;
		learned_cycles = 0;
		number_of_cycles = 1000;
		current_learnrate = max_learnrate;
		current_radius =  max_radius;

		//fill choice:
		nr_cycles_choice.addItem("100");
		nr_cycles_choice.addItem("500");
		nr_cycles_choice.addItem("1000");
		nr_cycles_choice.addItem("2000");
		nr_cycles_choice.select(2);

		//define layout:
		this.setLayout(new GridLayout(3,2));
		Panel tabCanvas = new Panel();
		tabCanvas.setLayout(new GridLayout(2,2,15,15));
		recpattern_text.setFont(new Font("Helvetica", Font.BOLD, 14));
		tabCanvas.add(recpattern_text);
		tabCanvas.add(recpattern);
		tabcanText.setFont(new Font("Helvetica", Font.BOLD, 14));
		tabCanvas.add(tabcanText);
		tabCanvas.add(tabcan);

		this.add (tabCanvas);
		Panel buttons = new Panel();
		buttons.setLayout(new GridLayout(5,2,10,10));
		buttons.add(clear_button);
		buttons.add(reset_button);
		reset_button.disable();
		buttons.add(rec_button);
		rec_button.disable();
		buttons.add(learn_button);
		buttons.add(nr_cyclesText);
		buttons.add(nr_cycles_choice);
		buttons.add(cyclesText);
		buttons.add(cycles);
		//buttons.add(vmat_button);
		buttons.add(new Panel());
		buttons.add(new Panel());
		this.add(buttons);

		this.add(statist);
		this.add(statist2);

		this.add(fMap);
		this.add(legend);

		int index = 0;
		for(int i = 0;i<kernel_width;i++){
			for(int j = 0;j<kernel_height;j++){
				if(i<kernel_width/2)
					vertical[i][j] = 1.0f;
				else
					vertical[i][j] = 0.0f;
				if(j<kernel_height/2)
					horizontal[i][j] = 1.0f;
				else
					horizontal[i][j] = 0.0f;
				if((j<kernel_height/4 || j>kernel_height*3/4) && (i<kernel_width/4 || i>kernel_width*3/4)){
					off_center[i][j] = 1.0f;

				}
				else{
					off_center[i][j] = 0.0f;

				}

				if((j>kernel_height/4 && j<kernel_height*3/4) && (i>kernel_width/4 && i<kernel_width*3/4)){
					on_center[i][j] = 1.0f;

				}
				else{
					on_center[i][j] = 0.0f;

				}

				if(i<=j){
					oblique_bottom[i][j] = 1.0f;

				}
				else{
					oblique_bottom[i][j] = 0.0f;

				}
				if(i>=j){
					oblique_top[i][j] = 1.0f;

				}
				else{
					oblique_top[i][j] = 0.0f;

				}
				if((i<=kernel_width/10 && j<=kernel_height/10)||(i<=kernel_width/10 && (kernel_height - j)<=kernel_height/10)||((kernel_width-i)<=kernel_width/10 && j<=kernel_height/10)||((kernel_width-i)<=kernel_width/10 && (kernel_height-j)<=kernel_height/10)){
					corners[i][j] = 1.0f;

				}
				else{
					corners[i][j] = 0.0f;

				}


				index++;
			}
		}


		TrainingData trainingData = new TrainingData();

		letterMatrix = new double[trainingData.getNumberOfElements()][vector_size];
		trainingNumber = trainingData.getNumberOfElements();
		letter = new String[trainingData.getNumberOfElements()];			
		//use training data to train current
		int width = 256;
		int height = 256;
		int width_grid = 32;
		resultImages = new BufferedImage[trainingData.getNumberOfElements()];
		
		for(int k = 0;k<trainingData.getNumberOfElements();k++){
			BufferedImage bTrainingImage = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
			try {
				String filePath = trainingData.getPathOfTrainingMaterial(k);
				URL imageURL = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/"+ filePath);
				bTrainingImage = ImageIO.read(new File(imageURL.toURI()));
				double ratioX = (double)  width_grid / bTrainingImage.getWidth();
				double ratioY = (double)  width_grid / bTrainingImage.getHeight();
				BufferedImageOp op = new AffineTransformOp(AffineTransform
						.getScaleInstance(ratioX, ratioY), new RenderingHints(
								RenderingHints.KEY_INTERPOLATION,
								RenderingHints.VALUE_INTERPOLATION_BICUBIC));
				BufferedImage dst = op.filter(bTrainingImage, null);

				resultImages[k] = ImageIO.read(new File(imageURL.toURI()));
				String name = trainingData.getNameOfTrainingMaterial(k);
				letter[k] = ""+(k+1)+" "+ name;
				double[] vector = getVector(dst);
				letterMatrix[k] = vector;
				
				
				//got the boolean data
				//now generate the vector?

			}
			catch (Exception exc) {
				// TODO Auto-generated catch block
				exc.printStackTrace();
			}
		}
//		Random ranGen = new Random();
		for(int k = 0;k<trainingNumber;k++){
			for(int l= 0;l<8;l++){
				letterMatrix[k][l] *= 10000;//= (1+ranGen.nextGaussian());
			}
		}
		System.out.println("");

	}
	private double calculateMoment(int p, int q,BufferedImage inputImage, int centX, int centY){
		double result = 0;
		for(int x = 0;x<inputImage.getWidth();x++){
			for(int y = 0; y<inputImage.getHeight();y++){
				int rgb = (int) (returnGrayScaleAverage(inputImage.getRGB(x, y)));

				result+= Math.pow(x- centX, p) * Math.pow(y-centY,q) * rgb;
			}
		}
		
		return result;
		
	}

	private double calculateScaleInvariantMoment(double zerozeromoment, double moment, int i, int j){
		return (moment / (Math.pow(zerozeromoment,(1+((i+j)/2)))));
	}
	public double[] getVector(BufferedImage sourceImage){
		double [] featureVector = new double[vector_size];
		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();
		detectorFilters[0] = vertical;
		detectorFilters[1] = horizontal;
		detectorFilters[2] = on_center;
		detectorFilters[3] = off_center;
		detectorFilters[4] = oblique_bottom;
		detectorFilters[5] = oblique_top;
		detectorFilters[6] = corners;
		int momentCenterXs[] = new int[9];
		int momentCenterYs[] = new int[9];
		int dotCounts[] = new int[9];
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				int rgb = (int) (returnGrayScaleAverage(sourceImage.getRGB(i, j)));

				// characterRep+characterRep+characterRep);
				if(rgb > 0.5){
					momentCenterXs[0]+=j;//(j-(width/2));
					momentCenterYs[0]+=i;//(i-(height/2));
					dotCounts[0]++;
					//if up
					if(i<=height/2){
						momentCenterXs[1]+=j;//(j-(width/2));
						momentCenterYs[1]+=i;//(i-(height/4));
						dotCounts[1]++;
					}
					//if down
					if(i>height/2){
						momentCenterXs[2]+=j;//(j-(width/2));
						momentCenterYs[2]+=i;//(i-(3*height/4));
						dotCounts[2]++;
					}


					//if left
					if(j<=width/2){
						momentCenterXs[3]+=j;//(j-(width/4));
						momentCenterYs[3]+=i;//(i-(height/2));
						dotCounts[3]++;
					}
					//if right
					if(j>width/2){
						momentCenterXs[4]+=j;//(j-(3*width/4));
						momentCenterYs[4]+=i;//(i-(height/2));
						dotCounts[4]++;
					}
					//if up left
					if(i<=height/2 && j<=width/2){
						momentCenterXs[5]+=j;//(j-(width/4));
						momentCenterYs[5]+=i;//(i-(height/4));
						dotCounts[5]++;
					}
					//if up right
					if(i<=height/2 && j>width/2){
						momentCenterXs[6]+=j;//(j-(3*width/4));
						momentCenterYs[6]+=i;//(i-(height/4));
						dotCounts[6]++;
					}
					//if bottom left
					if(i>height/2 && j<=width/2){
						momentCenterXs[7]+=j;//(j-(width/4));
						momentCenterYs[7]+=i;//(i-(3*height/4));
						dotCounts[7]++;
					}
					//if bottom right
					if(i>height/2 && j>width/2){
						momentCenterXs[8]+=j;//(j-(3*width/4));
						momentCenterYs[8]+=i;//(i-(3*height/4));
						dotCounts[8]++;
					}
				}


			}
		}
		//4 quadrants
		for(int k = 0;k<sourceImage.getWidth();k++){
			for(int j = 0;j<sourceImage.getHeight();j++){
				if(k == sourceImage.getWidth()/2 || j == sourceImage.getHeight()/2){
					sourceImage.setRGB(j, k, 0xFFFF0000);
				}
			}
		}

		//int i = 0;
		int colors[] = new int[9];
		colors[0] = 0xFFFF0000;
		colors[1] = 0xFF00FF00;
		colors[2] = 0xFF0000FF;
		colors[3] = 0xFFFF00FF;
		colors[4] = 0xFFFFFF00;
		colors[5] = 0xFF00FFFF;
		colors[6] = 0xFFF000F0;
		colors[7] = 0xFFF0F000;
		colors[8] = 0xFF00CCCC;
		for(int i = 0;i<9;i++){
			if(dotCounts[i]==0)
				dotCounts[i] = 1;

			momentCenterXs[i]/=dotCounts[i];
			momentCenterYs[i]/=dotCounts[i];
			try{
				//	for (int k = 0; k < 5; k++) {
				//	for (int l = 0; l < 5; l++) {
				sourceImage.setRGB((int)(momentCenterXs[i]),
						(int)(momentCenterYs[i]), colors[i]);

				//	}
				//	}
				//			bTrainingImageDigital.setRGB(momentCenterXs[i],
				//					momentCenterYs[i], 0xFF0000);
			}
			catch(Exception e){

			}


		}
		double momentzerozero = calculateMoment(0, 0, sourceImage, momentCenterXs[0], momentCenterYs[0]);
		double n20 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(2, 0, sourceImage, momentCenterXs[0], momentCenterYs[0]), 2, 0);
		double n02 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(0, 2, sourceImage, momentCenterXs[0], momentCenterYs[0]), 0, 2);
		double n11 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(1, 1, sourceImage, momentCenterXs[0], momentCenterYs[0]), 1, 1);
		double n03 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(0, 3, sourceImage, momentCenterXs[0], momentCenterYs[0]), 0, 3);
		double n30 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(3, 0, sourceImage, momentCenterXs[0], momentCenterYs[0]), 3, 0);
		double n21 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(2, 1, sourceImage, momentCenterXs[0], momentCenterYs[0]), 2, 1);
		double n12 = calculateScaleInvariantMoment(momentzerozero, calculateMoment(1, 2, sourceImage, momentCenterXs[0], momentCenterYs[0]), 1, 2);
		
		
		double I1 = n20 + n02;
		double I2 = Math.pow(n20 - n02,2) + Math.pow(2*n11, 2);
		double I3 = Math.pow(n30 - 3*n12,2) + Math.pow(3*n21- n03, 2);
		double I4 = Math.pow(n30 + n12,2) + Math.pow(n21+ n03, 2);
		double I5 = (n30-3*n12)*(n30+n12)*(Math.pow(n30+n12, 2)-3*(Math.pow(n21+n03,2)))+ (3*n21-n03)*(n21+n03)*(3*Math.pow(n30+n12, 2)-(Math.pow(n21+n03,2)));
		double I6 = (n20-n02)*(Math.pow(n30+n12,2)-(Math.pow(n21+n03, 2)))+4*n11*(n30+n12)*(n21+n03);
		double I7 = (3*n21-n03)*(n30+n12)*(Math.pow(n30+n12, 2)-3*(Math.pow(n21+n03,2)))+ (n30-n12)*(n21+n03)*(3*Math.pow(n30+n12, 2)-(Math.pow(n21+n03,2)));
		
		Date now = new Date();
//		long msTime = now.getTime();

//		File file = new File("/tmp/images/my_image"+msTime +".png");
//		try {
//			ImageIO.write(sourceImage, "png", file);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		double top_value = 0;
		
		featureVector[0] = momentCenterXs[5];
		featureVector[1] = momentCenterYs[5];
		featureVector[2] = momentCenterXs[6];
		featureVector[3] = momentCenterYs[6];
		featureVector[4] = momentCenterXs[7];
		featureVector[5] = momentCenterYs[7];
		featureVector[6] = momentCenterXs[8];
		featureVector[7] = momentCenterYs[8];
		
		featureVector[0] = 0.0f;
		featureVector[1] = I1;
		featureVector[2] = I2;
		featureVector[3] = I3;
		featureVector[4] = I4;
		featureVector[5] = I5;
		featureVector[6] = I6;
		featureVector[7] = I7;
		
//		for(int i = 0;i<8;i++){
//			if(featureVector[i]>top_value){
//				top_value = featureVector[i];
//			}
//		}
//		for(int i = 0;i<8;i++){
//				featureVector[i] = featureVector[i]/top_value;
//		}
		
//		double top_value = 0;
//		for(int k = 0;k<7;k++){
//			double totalValue = 0;
//			for(int i=0; i<width; i++)
//			{
//				for(int j = 0; j < height; j++){
//					int new_gray_value = (int) (returnGrayScaleAverage(sourceImage.getRGB(i, j))*(detectorFilters[k][i][j]));
//					totalValue+=new_gray_value;
//				}
//			}
//
//			//totalValue/=1000000;
//			featureVector[k] =(totalValue);
//			if(totalValue>top_value){
//				top_value = totalValue;
//			}
//
//		}
		//normalization
//		for(int k = 0;k<vector_size;k++){
//			//featureVector[k] = (featureVector[k] / (top_value/2));
//			//featureVector[k] = (featureVector[k] / (top_value));
//
//			System.out.println(""+ k+ " : "+ featureVector[k]);
//		}
		return featureVector;
	}

	public void selectBestNeuron()
	{
		double dist=0,old_dist=0;

		for(int x=0; x<=15; x++)
			for(int y=0; y<=15; y++) 
			{
				dist = mapLayer[x][y].getMaxNeti(inLayer);
				if(dist > old_dist)
				{
					old_dist = dist;
					bestx = x;
					besty = y;
				}
			}
	}


	public void change_weights() 
	{
		int xlow,xhigh,ylow,yhigh;
		double gaussian = 0.0;

		xlow  = bestx - current_radius;
		xhigh = bestx + current_radius;
		ylow  = besty - current_radius;
		yhigh = besty + current_radius;

		if(xlow < 0) xlow = 0;
		if(xhigh > 15) xhigh = 15;
		if(ylow < 0) ylow = 0;
		if(yhigh > 15) yhigh = 15;

		for(int x=xlow;x <= xhigh;x++)
			for(int y=ylow;y <= yhigh;y++) 
			{
				gaussian = Math.exp(-(sqr(x-bestx)+sqr(y-besty))/
						(2*sqr(current_radius)));
				for(int l=0;l<vector_size;l++)
					mapLayer[x][y].inWeights[l] += current_learnrate*gaussian*(inLayer[l].getOutput()- mapLayer[x][y].inWeights[l]);
				mapLayer[x][y].setName(letter[rand]);
			}
	} 


	public void eval_learnrate()
	{
		current_learnrate = max_learnrate*Math.exp(((double)learned_cycles/(double)number_of_cycles)*Math.log(min_learnrate/max_learnrate));
	}

	public void eval_radius()
	{
		current_radius = (int)Math.round(max_radius*Math.exp(((double)
				learned_cycles/(double)number_of_cycles)*
				Math.log((double)min_radius/(double)max_radius)));
	}

	public double sqr(double x)
	{
		return x * x;
	}

	public void detNeurons()
	{
		for(int k=0;k<trainingNumber;k++) 
		{
			for(int l=0;l<vector_size;l++)
				inLayer[l].setOutput(letterMatrix[k][l]);
			selectBestNeuron();
			System.out.println("Best neuron for "+letter[k] +" is "+bestx+","+besty);

			// evalError();

		}
	}  

	public double evalError() 
	{
		double dif = 0;

		for(int l=0;l<vector_size;l++) 
			dif += inLayer[l].getOutput()-mapLayer[bestx][besty].inWeights[l];

		return dif;
		//System.out.println("Error is "+dif);
	}
	public int[][] convertMatrix(int[][] input)
	{
		int[][] output = new int[16][16];

		int leftborder=0,rightborder=0,topborder=0,downborder=0;
		int leftold=0,rightold=0;
		int width, height;


		// get topBorder
		for(int i=0;i<16;i++)
			for(int k=0;k<16;k++)
				if((input[i][k] == 1)&&(topborder == 0)) 
					topborder=i;


		// get leftBorder
		for(int i=0;i<16;i++)
			for(int k=0;k<16;k++)
				if((input[k][i] == 1)&&(leftborder == 0)) leftborder=i;

		// get downBorder
		for(int i=15;i>=0;i--)
			for(int k=0;k<16;k++)
				if((input[i][k] == 1)&&(downborder == 0)) downborder=i;


		// get rightBorder
		for(int i=15;i>=0;i--)
			for(int k=0; k<16;k++)
				if((input[k][i] == 1)&&(rightborder == 0)) rightborder=i;

		width  = rightborder-leftborder;
		height = downborder-topborder;  

		int shrinkx = (int)Math.round((double)width/(double)(16-2));
		int shrinky = (int)Math.round((double)height/(double)(16-2));

		if(shrinkx == 0) shrinkx = 1;
		if(shrinky == 0) shrinky = 1;

		/* System.out.println("lb= "+leftborder+" rb= "+rightborder);
    System.out.println("tb= "+topborder+" db= "+downborder);
    System.out.println("sx= "+shrinkx+" sy= "+shrinky);
    System.out.println("wi= "+width+" hi= "+height);
    System.out.println();
		 */
		//resize and shrink to that!
		int x=0,y=0;

		for(int i=topborder;i<=downborder;i++){
			for(int k=leftborder;k<=rightborder;k++) {
				//System.out.print(CanMatrix[i][k]);
				x = (int)Math.round((double)(i-topborder)/(double)shrinky);
				y = (int)Math.round((double)(k-leftborder)/(double)shrinkx);
				//System.out.print("x= "+x+" y= "+y+" ");
				if((x<(16-1))&&(y<(16-1))&&(input[i][k]==1))
					output[x][y]= input[i][k];
				else if((x>=16-1)&&(y<16-1)&&(input[i][k]==1))
					output[16-2][y] = input[i][k];
				else if((y>=16-1)&&(x<16-1)&&(input[i][k]==1))
					output[x][16-2] = input[i][k];
			}
			// System.out.println();
		}
		//System.out.println();
		return output;
	}

	public int[][] fillup(int[][] input)
	{
		//fill up gaps!
		int k=0;
		for(int i=0;i<15;i++) {
			while(k<15) {
				while((input[i][k] == 0) && (k<15) ) k++;
				//find repeating ones!
				while((input[i][k] == 1) && (k<15) ) k++;
				//find repeating ones
				if(k<14) {
					if((input[i][k+1] == 1)||(input[i][k+2] == 1))
						input[i][k] = input[i][k+1] = 1;
				}
			}
			k=0;
		}

		k=0;

		for(int i=0;i<15;i++) {
			while(k<15) {
				while((input[k][i] == 0) && (k<15) ) k++;

				while((input[k][i] == 1) && (k<15) ) k++;

				if(k<14) {
					if((input[k+1][i] == 1)||(input[k+2][i] == 1))
						input[k][i] = input[k+1][i] = 1;
				}
			}
			k=0;
		}
		return input;
	}


	public int returnGrayScaleAverage(int pixel) {
		int red   = (pixel >> 16) & 0xff;
		int green = (pixel >>  8) & 0xff;
		int blue  = (pixel      ) & 0xff;
		// Deal with the pixel as necessary...
		return (red+green+blue)/3;
	}
	private void setInput(){
		//		int outputVector = getVector()
		//		  inLayer[0].setOutput(schraeg);   // schraeg
		//		    inLayer[1].setOutput(owaag);     // o. waagrecht
		//		    inLayer[2].setOutput(mwaag);     // m. waagrecht
		//		    inLayer[3].setOutput(uwaag);     // u. waagrecht
		//		    inLayer[4].setOutput(senk);      // senkrecht
		//		    inLayer[5].setOutput(robow);     // re.ob. Bogen
		//		    inLayer[6].setOutput(rubow);     // re.un. Bogen
	}
	public double[] recognize(BufferedImage sourceImage, Boolean isEucledian){
		double [] resultVector = new double[trainingNumber];
		double [] featureVector = getVector(sourceImage);
		recpattern.setFont(new Font("Helvetica",Font.BOLD,26));

		//normalization
		for(int k = 0;k<vector_size;k++){
			inLayer[k].setOutput(featureVector[k]);
		}
		double closest_neighbour_distance = Integer.MAX_VALUE;
		int closest_neighbour = 0;
		if(isEucledian){
			for(int i =0;i<trainingNumber;i++){
				double total_distance = 0;
				for(int k=0;k<vector_size;k++){
					if(letterMatrix[i][k]!=0){
						double mid_distance = Math.pow((letterMatrix[i][k] - featureVector[k]),2);
						total_distance+= (mid_distance);
					}
					else{
						//System.out.println("Skipping "+i+ "/"+ k);
					}
				}
				//System.out.println("O:"+ i+"T:"+total_distance);
				if(total_distance < closest_neighbour_distance){
					closest_neighbour = i;
					closest_neighbour_distance = Math.sqrt(total_distance);
				}
				resultVector[i] = total_distance;
			}
			recpattern.setText("R:"+ closest_neighbour+" D:" + closest_neighbour_distance);
		}
		else{
			//setInput(inputPattern);
			selectBestNeuron();
			evalError();
			recpattern.setText(String.valueOf(mapLayer[bestx][besty].getName()));

		}
		// initialize input neurons

		//		//System.out.println("----------------------------------");


		//for(int x=0;x<16;x++) {
		// for(int y=0;y<16;y++) 
		//  System.out.print(" "+mapLayer[x][y].getName());
		// System.out.println();}
		//System.out.println("----------------------------------");

		//System.out.println("selected an  "+mapLayer[bestx][besty].getName());
		//System.out.println("----------------------------------");
		return resultVector;

	}
	public boolean action(Event e, Object arg)
	{
		if (e.target == clear_button)      //clear canvas
		{
			tabcan.clearTable();
			recpattern.setText("");
		}
		else if(e.target == learn_button) { // learn pattern
			int inVec[];
			Random random = new Random();
			rec_button.enable();
			reset_button.enable();
			// do learning
			
			while(learned_cycles <= number_of_cycles) {
				//read XML

				//read images
				//generate all that stuff from the pics

				// first get input vector and initialize input neurons
				// get Random number

				rand = Math.abs(random.nextInt()%trainingNumber);
				for(int l=0;l<vector_size;l++)
					inLayer[l].setOutput(letterMatrix[rand][l]);

				// now select best neuron
				selectBestNeuron();
				// adjust weights
				change_weights();
				// evaluate new learning rate and radius
				// mapLayer[bestx][besty].setName(letter[rand]);
				eval_learnrate();
				eval_radius();

				statist.drawValue(current_learnrate);
				statist2.drawValue(Math.abs(evalError()));
				fMap.drawActivity(mapLayer);

				cycles.setText(String.valueOf(learned_cycles));
				learned_cycles++;
			}
			// determine perfect pattern neurons
			detNeurons();

		}
		else if(e.target == rec_button) 
		{ // analyse input
			int inMatrix[][] = tabcan.getMatrix();
			double [] featureVector = new double[vector_size];

			int width = 16;
			int height = 16;

			detectorFilters[0] = vertical;
			detectorFilters[1] = horizontal;
			detectorFilters[2] = on_center;
			detectorFilters[3] = off_center;
			detectorFilters[4] = oblique_bottom;
			detectorFilters[5] = oblique_top;
			detectorFilters[6] = corners;

			double top_value = 1;
			for(int k = 0;k<vector_size;k++){
				double totalValue = 0;
				for(int i=0; i<width; i++)
				{
					for(int j = 0; j < height; j++){
						int new_gray_value = (int) (inMatrix[i][j] * detectorFilters[k][i][j]);

						totalValue+=new_gray_value;
					}
				}

				//totalValue/=1000000;
				featureVector[k] =(totalValue);
				if(totalValue>top_value){
					top_value = totalValue;
				}

			}
			//normalization
			for(int k = 0;k<vector_size;k++){
				featureVector[k] = (featureVector[k] / (top_value/2));
				System.out.println(""+ k+ " : "+ featureVector[k]);
				inLayer[k].setOutput(featureVector[k]);
			}

			int closest_neighbour_distance = 65536;
			int closest_neighbour = 0;
			for(int i =0;i<trainingNumber;i++){
				int total_distance = 0;
				for(int k=0;k<vector_size;k++){
					int mid_distance = (int) Math.pow((letterMatrix[i][k] - featureVector[k]),2);
					total_distance+= mid_distance;
				}
				if(total_distance < closest_neighbour_distance){
					closest_neighbour = i;
					closest_neighbour_distance = total_distance;
				}
				System.out.println("Distance to" + i+" is:"+ total_distance);


			}
			System.out.println("Closest neighbour:"+ closest_neighbour);
			//calculate the distance

			// initialize input neurons
			selectBestNeuron();
			//System.out.println("----------------------------------");
			evalError();

			//for(int x=0;x<16;x++) {
			// for(int y=0;y<16;y++) 
			//  System.out.print(" "+mapLayer[x][y].getName());
			// System.out.println();}
			//System.out.println("----------------------------------");

			//System.out.println("selected an  "+mapLayer[bestx][besty].getName());
			//System.out.println("----------------------------------");

			recpattern.setFont(new Font("Helvetica",Font.BOLD,26));
			recpattern.setText(String.valueOf(mapLayer[bestx][besty].getName()));

			//best_neuron_x.setText(String.valueOf(bestx));
			//best_neuron_y.setText(String.valueOf(besty));
		}

		else if(e.target == vmat_button)  //view matrix
		{
			int mat[][];
			int vec[];
			mat = tabcan.getMatrix();
			for(int i=0; i<=15; i++)
			{
				for(int j=0; j<=15; j++)
					System.out.print(mat[i][j]);
				System.out.println();
			}
			System.out.println("--------------------------------");
			vec = tabcan.getVector();
			for (int i=0; i<256; i++)
				System.out.print(vec[i]);
			System.out.println();
			System.out.println("--------------------------------");

		}
		else if(e.target == reset_button)
		{
			for(int i=0; i<5; i++)
				inLayer[i] = new Neuron(0,i);
			for(int i=0; i<=15; i++)
				for(int j=0; j<=15; j++)
					mapLayer[i][j] = new Neuron(i,j);

			learned_cycles = 0;
			current_learnrate = max_learnrate;
			current_radius =  max_radius;

			statist.resetXpos();
			statist2.resetXpos();

			tabcan.clearTable();
			recpattern.setText("");

			fMap.resetMap();
			reset_button.disable();
			rec_button.disable();
		}
		else if(e.target == nr_cycles_choice)
		{
			number_of_cycles = Integer.parseInt(nr_cycles_choice.getSelectedItem());
		}
		return true;
	}			       
}
//----------------------------------------------------------------

class tableCanvas extends Canvas
{
	private final int pixelSize = 5;    // n*n-virtual-pixel
	private final int matrixSize = 16;  // m*m-matrix

	private int matrix[][] = new int[matrixSize][matrixSize];
	private int vector[]   = new int[matrixSize*matrixSize];
	private int CanMatrix[][] = new int[pixelSize*matrixSize-pixelSize][pixelSize*matrixSize-pixelSize];

	Graphics graph;
	int last_x, last_y;

	public boolean mouseDown (Event e, int x, int y)
	{
		if ((x<=(pixelSize * matrixSize)-pixelSize)&&
				(y<=(pixelSize * matrixSize)-pixelSize)&&
				(x>=0)&&(y>=0))
		{
			last_x = x;
			last_y = y;
			// matrix[(y-(y%pixelSize))/pixelSize][(x-(x%pixelSize))/pixelSize]  = 1;
			CanMatrix[y][x] = 1;
		}
		return true;
	}

	public boolean mouseDrag(Event e, int x, int y)
	{
		if ((x<=(pixelSize * matrixSize)-pixelSize)&&
				(y<=(pixelSize * matrixSize)-pixelSize)&&
				(x>=0)&&(y>=0))
		{	
			graph.setColor(Color.black);
			graph.drawLine(last_x, last_y, x, y);
			last_x = x;
			last_y = y;

			// matrix[(y-(y%pixelSize))/pixelSize][(x-(x%pixelSize))/pixelSize]  = 1;
			CanMatrix[y][x] = 1;
		}
		return true;
	}

	public void paint(Graphics g)
	{
		graph = getGraphics();
		Dimension dim;

		this.resize((pixelSize * matrixSize)-pixelSize, 
				(pixelSize * matrixSize)-pixelSize);
		dim = this.size();
		g.setColor(Color.white);
		g.fillRect(0, 0, dim.width, dim.height);

		//update the canvas:
		g.setColor(Color.black);
		for(int i=0; i<=(matrixSize-1); i++)
			for(int j=0; j<=(matrixSize-1); j++)
				if(matrix[i][j] == 1) drawPixel(j*pixelSize,i*pixelSize);
		/*for(int i=0; i<((pixelSize * matrixSize)-pixelSize); i++)
      for(int j=0; j<((pixelSize * matrixSize)-pixelSize); j++)
        if(CanMatrix[i][j] == 1) drawPixel(j,i);*/
	}

	public void drawPixel(int x, int y)
	{
		if ((x<=(pixelSize * matrixSize)-pixelSize)&&
				(y<=(pixelSize * matrixSize)-pixelSize)&&
				(x>=0)&&(y>=0))
		{
			graph.setColor(Color.black);
			graph.drawLine(x, y, x, y);
		}
	}

	public void convertMatrix()
	{
		int leftborder=0,rightborder=0,topborder=0,downborder=0;
		int leftold=0,rightold=0;
		int width, height;


		// get topBorder
		for(int i=0;i<((pixelSize * matrixSize)-pixelSize);i++)
			for(int k=0;k<((pixelSize * matrixSize)-pixelSize);k++)
				if((CanMatrix[i][k] == 1)&&(topborder == 0)) 
					topborder=i;


		// get leftBorder
		for(int i=0;i<((pixelSize * matrixSize)-pixelSize);i++)
			for(int k=0;k<((pixelSize * matrixSize)-pixelSize);k++)
				if((CanMatrix[k][i] == 1)&&(leftborder == 0)) leftborder=i;

		// get downBorder
		for(int i=((pixelSize * matrixSize)-pixelSize-1);i>=0;i--)
			for(int k=0;k<((pixelSize * matrixSize)-pixelSize);k++)
				if((CanMatrix[i][k] == 1)&&(downborder == 0)) downborder=i;


		// get rightBorder
		for(int i=((pixelSize * matrixSize)-pixelSize-1);i>=0;i--)
			for(int k=0; k<((pixelSize * matrixSize)-pixelSize);k++)
				if((CanMatrix[k][i] == 1)&&(rightborder == 0)) rightborder=i;

		width  = rightborder-leftborder;
		height = downborder-topborder;  

		int shrinkx = (int)Math.round((double)width/(double)(matrixSize-2));
		int shrinky = (int)Math.round((double)height/(double)(matrixSize-2));

		if(shrinkx == 0) shrinkx = 1;
		if(shrinky == 0) shrinky = 1;

		/* System.out.println("lb= "+leftborder+" rb= "+rightborder);
    System.out.println("tb= "+topborder+" db= "+downborder);
    System.out.println("sx= "+shrinkx+" sy= "+shrinky);
    System.out.println("wi= "+width+" hi= "+height);
    System.out.println();
		 */
		//resize and shrink to that!
		int x=0,y=0;

		for(int i=topborder;i<=downborder;i++){
			for(int k=leftborder;k<=rightborder;k++) {
				//System.out.print(CanMatrix[i][k]);
				x = (int)Math.round((double)(i-topborder)/(double)shrinky);
				y = (int)Math.round((double)(k-leftborder)/(double)shrinkx);
				//System.out.print("x= "+x+" y= "+y+" ");
				if((x<(matrixSize-1))&&(y<(matrixSize-1))&&(CanMatrix[i][k]==1))
					matrix[x][y]= CanMatrix[i][k];
				else if((x>=matrixSize-1)&&(y<matrixSize-1)&&(CanMatrix[i][k]==1))
					matrix[matrixSize-2][y] = CanMatrix[i][k];
				else if((y>=matrixSize-1)&&(x<matrixSize-1)&&(CanMatrix[i][k]==1))
					matrix[x][matrixSize-2] = CanMatrix[i][k];
			}
			// System.out.println();
		}
		//System.out.println();
	}

	public void fillup()
	{
		//fill up gaps!
		int k=0;
		for(int i=0;i<15;i++) {
			while(k<15) {
				while((matrix[i][k] == 0) && (k<15) ) k++;
				//find repeating ones!
				while((matrix[i][k] == 1) && (k<15) ) k++;
				//find repeating ones
				if(k<14) {
					if((matrix[i][k+1] == 1)||(matrix[i][k+2] == 1))
						matrix[i][k] = matrix[i][k+1] = 1;
				}
			}
			k=0;
		}

		k=0;

		for(int i=0;i<15;i++) {
			while(k<15) {
				while((matrix[k][i] == 0) && (k<15) ) k++;

				while((matrix[k][i] == 1) && (k<15) ) k++;

				if(k<14) {
					if((matrix[k+1][i] == 1)||(matrix[k+2][i] == 1))
						matrix[k][i] = matrix[k+1][i] = 1;
				}
			}
			k=0;
		}

	}

	public int[][] getMatrix()
	{
		convertMatrix();
		fillup();
		return matrix;
	}

	public int[] getVector()
	{
		int x = 0;

		for (int i=0; i<=matrixSize-1; i++)
			for (int j=0; j<=matrixSize-1; j++)
				vector[x++] = matrix[i][j];


		return vector;
	}

	public void clearTable()
	{
		Dimension dim;
		Graphics g = this.getGraphics();

		//clear canvas:
		dim = this.size();
		g.setColor(Color.white);

		g.fillRect(0, 0, dim.width, dim.height);

		//clear the matrix:
		for(int i=0; i<=(matrixSize-1); i++)
			for(int j=0; j<=(matrixSize-1); j++)
				matrix[i][j] = 0;

		for(int i=0; i < (pixelSize*matrixSize-pixelSize); i++)
			for(int j=0; j < (pixelSize*matrixSize-pixelSize);j++)
				CanMatrix[i][j] = 0; 
	}
}



//------------------------------------------------------------

class Neuron
{
	int vector_size = 8;
	private double neti;                     //netto input
	private String objName;
	private double output;
	private int xpos, ypos;                  //position in lattice
	public double inWeights[] = new double[vector_size];//weighted connections 
	//to input-neurons
	public double mapWeights[][] = new double[16][16];	//weighted connections 
	//to other map-neurons

	Neuron(int xp, int yp)
	{
		neti = 0;
		output = 0;
		xpos = xp;
		ypos = yp;
		objName = "x";

		//init inWeights ...
		Random random = new Random();
		for(int l=0;l<vector_size;l++)
			inWeights[l] = (random.nextDouble());
		// init mapWeights with a Gaussian function
		for(int x=0;x<16;x++)
			for(int y=0;y<16;y++)
				mapWeights[x][y] =  Math.exp(-(sqr(x-xpos)+sqr(y-ypos))/(2*sqr(15)));
	}

	private double sqr(double x)
	{
		return x * x;
	}


	public double getOutput()
	{
		return output;
	}

	public void setNeti(double x)
	{
		neti = x;
	}

	public void setName(String c)
	{
		objName = c;
	}

	public String getName()
	{
		return objName;
	}


	public void setOutput(double o)
	{
		output = o;
	}

	public double getMaxNeti(Neuron [] inLayer)
	{
		double sum = 0;

		for(int i=0; i<vector_size; i++)
			sum += inWeights[i] * inLayer[i].getOutput();

		return sum;
	}

	public void evalOutput()
	{
		output = 1.0 / (1.0 + Math.exp(-neti)); 
	}

	public void evalInput(Neuron [] inLayer, Neuron [][] mapLayer)
	{
		double sum = 0;

		for(int i=0; i<vector_size; i++)
			sum += inWeights[i] * inLayer[i].getOutput();

		for(int i=0; i<=15; i++)
			for(int j=0; j<=15; j++)
			{
				if ((i != xpos) && (j != ypos))
					sum += mapWeights[i][j] * mapLayer[i][j].getOutput();
			}
		neti = sum;
		evalOutput();
	}
}

//---------------------------------------------------------------

class Statistics extends Canvas
{
	private int height;
	private int width;
	private int xpos;
	private String title;
	private double factorY;
	private double maxyVal;
	private double y_old;
	private int[] buf = new int[150];  //buffer for y-values
	Image output;                      //for double buffering
	Graphics outputGraphics;           //       -"-


	Statistics(double maxY, String statTitle)
	{
		this.height = 140;
		this.width = 190;
		this.xpos = 35;
		this.factorY = 100/maxY;
		this.maxyVal = maxY;
		this.title = statTitle;
		y_old = 0;
	}

	public void paint(Graphics g)
	{
		this.resize(this.width, this.height);
		this.setBackground(Color.lightGray);
		g.setColor(Color.black);
		g.drawLine(34, 10, 34, 111);                    //y-axis
		g.drawLine(34, 111, 185, 111);                  //x-axis
		g.drawString(String.valueOf(maxyVal), 5, 15);   //max. y
		g.drawString("0", 15, 115);                      //min. y
		g.setColor(Color.red);
		g.drawString(title, 35, 128);  //title
		g.setColor(Color.blue);
		for(int x=35; x<xpos; x++)      //only update
		{
			if (x==35)
				g.drawLine(x, this.height-buf[x-35]-30, 
						x, this.height-buf[x-35]-30);
			else
				g.drawLine(x, this.height-buf[x-36]-30,
						x, this.height-buf[x-35]-30);
		}			       
	}

	public final void drawValue(double ypos)
	{
		Graphics g = this.getGraphics();

		if (((ypos*factorY)<=100) && ((ypos*factorY)>=0))
			if (xpos <= 184)  //dont't use buffer for drawing
			{
				g.setColor(Color.blue);

				if (xpos==35)
					g.drawLine(xpos, this.height-(int)(ypos*factorY)-30,
							xpos, this.height-(int)(ypos*factorY)-30);
				else
					g.drawLine(xpos, this.height-(int)(ypos*factorY)-30, 
							xpos-1, (int)y_old);//this.height-(int)(ypos*factorY)-30);
				buf[xpos-35] = (int)(ypos*factorY);
				y_old = this.height-(int)(ypos*factorY)-30;
				xpos++;
			}
			else    //draw buffered values using double buffering
			{
				output = createImage(150, 100);
				outputGraphics = output.getGraphics();

				outputGraphics.setColor(Color.lightGray);
				outputGraphics.fillRect(0, 0, 150, 100);
				insertYval(ypos);   //insert last y-value
				outputGraphics.setColor(Color.blue);
				for(int x=0; x<=149; x++)
				{
					if (x==0)
						outputGraphics.drawLine(x, 100-buf[x], x, 100-buf[x]);
					else
						outputGraphics.drawLine(x, 100-buf[x], x, 100-buf[x-1]);
				}
				g.drawImage (output, 35, 11, this);
			}
	}

	private final void insertYval(double y)
	{
		for(int i=1; i<=149; i++)
			buf[i-1] = buf[i];                       //shift 1
		buf[149] = (int)(y*factorY);    //update last y-value
	}

	public final void resetXpos()
	{
		Graphics g = this.getGraphics();

		xpos = 35;    //set min xpos
		g.setColor(Color.lightGray);
		g.fillRect(35, 11, 150, 100);    //clear diagram
	}
}


//--------------------------------------------------------

class FeatureMap extends Canvas
{
	Image output;                      //for double buffering
	Graphics outputGraphics;           //       -"-
	Neuron [][] mapLayer_bak;
	boolean to_update = false;


	public void paint(Graphics g)
	{
		this.resize(210, 190);
		this.setBackground(Color.lightGray);
		g.setColor(Color.red);
		g.drawString("FEATURE MAP", 35, 180);  //title
		if(to_update==true)
			drawActivity(mapLayer_bak);
		else
		{
			g.setColor(Color.gray);
			for(int x=0;x<16;x++)
				for(int y=0;y<16;y++)
					g.fillOval(y*10+5+25, x*10+5, 9, 9);
		}
	}


	public final void drawActivity(Neuron [][] mapLayer)
	{
		mapLayer_bak = mapLayer;
		to_update = true;

		Graphics g = this.getGraphics();
		output = createImage(170, 170);
		outputGraphics = output.getGraphics();

		outputGraphics.setColor(Color.lightGray);
		outputGraphics.fillRect(0, 0, 170, 170);
		for(int x=0;x<16;x++) 
			for(int y=0;y<16;y++)
			{
				//	String letter[] = {"Circle", "Plus", "Triangle", "Star", "Person"};
				switch(mapLayer[x][y].getName().charAt(0))
				{
				case '1':
				{
					outputGraphics.setColor(Color.blue);
					break;
				}
				case '2':
				{
					outputGraphics.setColor(Color.green);
					break;
				}
				case '3':
				{
					outputGraphics.setColor(Color.yellow);
					break;
				}
				case '4':
				{
					outputGraphics.setColor(Color.pink);
					break;
				}
				case '5':
				{
					outputGraphics.setColor(Color.red);
					break;
				}
				case '6':
				{
					outputGraphics.setColor(Color.white);
					break;
				}
				case '7':
				{
					outputGraphics.setColor(Color.magenta);
					break;
				}
				default:
				{
					outputGraphics.setColor(Color.lightGray);
					break;
				}
				}
				outputGraphics.fillOval(y*10+5, x*10+5, 9, 9);
			}
		g.drawImage (output, 25, 0, this);
	}

	public final void resetMap()
	{
		to_update = false;
		repaint();
	}
}


//-----------------------------------------------

class Legend extends Canvas
{
	public void paint(Graphics g)
	{
		Color colors[] = {Color.blue, Color.green, Color.yellow,
				Color.pink, Color.red, Color.white, Color.magenta};
		String chars[] = {"= First", "= Second", "= Third","= Fourth","= Fifth"};

		for(int y=0;y<chars.length;y++)
		{
			g.setColor(colors[y]);
			g.fillOval(10,y*20+15, 9, 9);
			g.setColor(Color.black);
			g.drawString(chars[y], 30,y*20+15+10);
		}
	}
}

