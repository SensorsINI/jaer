package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.applet.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.*;
import java.util.*;


public class Kohonen extends Applet

{
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
  
  private Neuron inLayer[] = new Neuron[9];		        //input-neurons layer
  private Neuron mapLayer[][] = new Neuron [16][16];	//map-neurons layer
  char letterMatrix[][] = {
    {2,1,0,0,0,0,0,0,0}, // A  // Schraege ,ober waagr, mit. waag,
    {0,0,0,0,1,1,1,0,0}, // B  // untere waagr., senkrechte
    {0,0,0,0,0,0,0,0,1}, // C  // ob.re.bogen, un.re.bogen //bogen = bow!
    {0,0,0,0,1,0,0,1,0}, // D  // re.gr. bogen, li. gr. bogen  
    {0,1,1,1,1,0,0,0,0}, // E
    {0,1,1,0,1,0,0,0,0}, // F
    {0,1,0,0,2,0,0,0,0}  // H
  
  };
  char letter[] = {'A','B','C','D','E','F','H'};
  int object;
  int rand;
  int bestx,besty;              // store best selected neuron here
  // variables for learning
  int max_radius,min_radius;
  double max_learnrate,min_learnrate;
  int learned_cycles,number_of_cycles,current_radius;
  double current_learnrate;
 
	
	
  public void init()
  {
    for(int i=0; i<9; i++)
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
	    for(int l=0;l<9;l++)
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
    for(int k=0;k<7;k++) 
    {
      for(int l=0;l<9;l++)
        inLayer[l].setOutput(letterMatrix[k][l]);
      selectBestNeuron();
      System.out.println("Best neuron for "+letter[k] +" is "+bestx+","+besty);
      
      // evalError();

    }
  }  

  public double evalError() 
  {
    double dif = 0;

    for(int l=0;l<9;l++) 
      dif += inLayer[l].getOutput()-mapLayer[bestx][besty].inWeights[l];

    return dif;
    //System.out.println("Error is "+dif);
  }

  public void setInput() 
  {
    int inVec[][] = tabcan.getMatrix();
    int tmpVec[][] = new int[16][16];
    int owaag=0,mwaag=0,uwaag=0,senk=0,robow=0,rubow=0;
    int schraeg = 0,bigbow=0,lbow=0;
    int help=0;
    int ones=0;
    int k=0,i=0;

    //copy Vector
    for(k=0;k<15;k++)
      for(i=0;i<15;i++)
        tmpVec[k][i]=inVec[k][i];
   
    //copy to temp vector
    
    //initialize all horizontal and vertical counters
      i = 0;
      int horz1=0,horz2=0,empty=0;
      int vert1=0,vert2=0,vert3=0,vert4=0;
//upper left check!
      for(k=0;k<5;k++) {
	for(i=0;i<8;i++) {
        while((tmpVec[i][k]==0)&&(i<8)) i++;
        // vertical
        //in upper left, start going right!
        for(int m=k;m<k+2;m++)
	  for(int l=i;l<i+7;l++)
	    if(tmpVec[m][l] == 1) horz1++;
        //go down on vertical!
	 for(int l=k+2;l<13;l++)
          if(tmpVec[l][14] == 1) vert1++;

        for(int l=k+2;l<13;l++)
          if(tmpVec[l][13] == 1) vert2++;

        for(int l=k+1;l<13;l++)
          if(tmpVec[l][12] == 1) vert3++;

	for(int l=k+1;l<13;l++)
          if(tmpVec[l][11] == 1) vert4++;
        //horizontal
        for(int l=14;l>11;l--)
          for(int m=i;m<i+7;m++)
            if(tmpVec[l][m] == 1) horz2++;

	//check inside to see whether empty or not!
	for(int l=k+5;l<k+9;l++)
	  for(int m=i;m<i+5;m++)
	    if(tmpVec[l][m] == 1) empty++;

	 if((empty<=4)&&(horz1>=5)&&(horz2>=5)&&((vert1>=4)||(vert2>=5)||(vert3>=5)||(vert4>=5))) {

	   bigbow = 1;

	   for(int l=k+2;l<13;l++)
	     tmpVec[l][14] = 0;

	   for(int l=k+2;l<13;l++)
	     tmpVec[l][13] = 0;

	   for(int l=k+1;l<13;l++)
	     tmpVec[l][12] = 0;
	 }
	 empty=horz1=horz2=vert1=vert2=vert3=vert4=0;
	 
	}
      }


    // check for upper bows
     if(bigbow == 0) {
    int verti1=0;
    int hori1=0,hori2=0;

    for(k=0;k<4;k++) {
      for(i=0;i<9;i++) {
        while((tmpVec[i][k]==0)&&(i<9)) i++;
        // vertical
	for(int m=k;m<k+2;m++) 
	  for(int l=i;l<i+5;l++)
	    if(tmpVec[m][l] == 1) hori1++;

	 for(int l=k+1;l<k+6;l++)
          if(tmpVec[l][14] == 1) verti1++;

        for(int l=k+1;l<k+6;l++)
          if(tmpVec[l][13] == 1) verti1++;

        for(int l=k+1;l<k+6;l++)
          if(tmpVec[l][12] == 1) verti1++;

	for(int l=k+1;l<k+6;l++)
          if(tmpVec[l][11] == 1) verti1++;
        //horizontal
        for(int l=k+4;l<k+8;l++)
          for(int m=i;m<i+5;m++)
            if(tmpVec[l][m] == 1) hori2++;

	 if((hori1>=4)&&(hori2>=4)&&(verti1>=5)) {
	    System.out.println("o.hori1 = "+hori1);
	    System.out.println("o.verti1 = "+verti1);
	    System.out.println("o.hori2 = "+hori2);
            if(robow == 0) robow++;

	    // vertical
	     for(int l=i;l<i+7;l++)
	     tmpVec[k][l] = 0;
        
	    for(int l=k+1;l<k+6;l++)
	      tmpVec[l][14] = 0;

	    for(int l=k+1;l<k+6;l++)
	      tmpVec[l][13] = 0;

	    for(int l=k+1;l<k+4;l++)
	      tmpVec[l][12] = 0;

	   for(int l=k+1;l<k+4;l++)
	      tmpVec[l][11] = 0; 
        //horizontal
	// for(int l=k+5;l<k+8;l++)
        //  for(int m=i+1;m<i+5;m++)
        //    tmpVec[l][m] = 0;
        }
        hori1=hori2=verti1= 0;
      }
    }
   
    // check for lower bows  
    for(k=7;k<9;k++) {
      for(i=0;i<9;i++) {
        while((tmpVec[i][k]==0)&&(i<9)) i++;
        // vertical
	for(int m=k;m<k+2;m++) 
	  for(int l=i;l<i+7;l++)
	    if(tmpVec[m][l] == 1) hori1++;

	 for(int l=k;l<k+6;l++)
          if(tmpVec[l][14] == 1) verti1++;

        for(int l=k;l<k+6;l++)
          if(tmpVec[l][13] == 1) verti1++;

        for(int l=k;l<k+6;l++)
          if(tmpVec[l][12] == 1) verti1++;

	for(int l=k;l<k+6;l++)
          if(tmpVec[l][11] == 1) verti1++;
        //horizontal
        for(int l=11;l<15;l++)
          for(int m=i;m<i+5;m++)
            if(tmpVec[l][m] == 1) hori2++;

	 if((hori1>=4)&&(hori2>=4)&&(verti1>=5)) {
	    System.out.println("hori1 = "+hori1);
	    System.out.println("verti1 = "+verti1);
	    System.out.println("hori2 = "+hori2);
            if(rubow == 0) rubow++;
	    // vertical
	     for(int l=i;l<i+7;l++)
	     tmpVec[k][l] = 0;
        
	    for(int l=k+1;l<k+6;l++)
	      tmpVec[l][14] = 0;

	    for(int l=k+1;l<k+6;l++)
	      tmpVec[l][13] = 0;

	    for(int l=k+1;l<k+4;l++)
	      tmpVec[l][12] = 0;

	   for(int l=k+1;l<k+4;l++)
	      tmpVec[l][11] = 0; 
        //horizontal
	// for(int l=k+5;l<k+8;l++)
        //  for(int m=i+1;m<i+5;m++)
        //    tmpVec[l][m] = 0;
        }
        hori1=hori2=verti1= 0;
      }
    }
    }
     
     if((bigbow == 0) && (robow == 0) && (rubow == 0)) {
     // check for left bow
     int chorz1=0,chorz2=0,cvert1=0,cempty=0;

     for(k=0;k<3;k++) {
      for(i=0;i<15;i++) {
	for(int m=0;m<3;m++)
	  for(int l=14;l>3;l--)
	    if(tmpVec[m][l] == 1) chorz1++;

	for(int m=14;m>11;m--)
	  for(int l=0;l<13;l++)
	    if(tmpVec[m][l] == 1) chorz2++;

	for(int m=0;m<2;m++)
	  for(int l=3;l<14;l++)
	    if(tmpVec[l][m] == 1) cvert1++;

	//check inside
	for(int l=k+5;l<k+9;l++)
	  for(int m=2;m<7;m++)
	    if(tmpVec[l][m] == 1) cempty++;

	if((cempty<=4)&&(chorz1>=5)&&(chorz2>=5)&&(cvert1>=5)) {
	  lbow = 1;
	  // empty array
	  for(int x=0;x<15;x++) 
	    for(int y=0;y<15;y++) 
	      tmpVec[x][y] = 0;
	}
	cempty=chorz1=chorz2=cvert1=0;
      }
     }
     }

    i = 0;
     // check vertical
    for(k=0;k<15;k++) {
      while((i<15)&&(k<15)) {
        while((tmpVec[i][k]==0)&&(i<15)){
          i++;
        }
      
        while((tmpVec[i][k]==1)&&(i<15)){
          help++;
          i++;
        }
        if(help >= 6){
          senk++;
          k+=2;
        }
        help = 0;
      }
      i = 0;
    }
    
    if((robow == 0)&&(bigbow == 0)) { 
     //check horizontal
      i = 0;
      for(k=0;k<15;k++) {
	while((i<15)&&(k<15)) {
	  while((tmpVec[k][i]==0)&&(i<15)) i++;
      
	  while((tmpVec[k][i]==1)&&(i<15)) {
	    help++;
	    i++;
	  }
	  if(help >= 4) {
	    if(owaag == 0)
	      owaag++;
	    else if(mwaag == 0)
	      mwaag++;
	    else if(uwaag == 0)
	      uwaag++;
	    k+=2;
	  }

	  help = 0;
	}
	i = 0;
      }
    }

    if( (robow == 0)&&(bigbow == 0)) {
     int l;
     i = 0;
    // check oblique lr
    for(k=0;k<15;k++) {
      l = k;
      while(l>0) {
        while((tmpVec[l][i]==0)&&(i<15)&&(l>0)){
          i++;
          l--;
        }
      
        while((tmpVec[l][i]==1)&&(i<15)&&(l>0)) {
          help++;
          i++;
          l--;
        }
        if(help >= 4) 
	  if(schraeg < 2)
	    schraeg++;

        help = 0;
      }
      i = 0;
    }

    // check oblique rl
    i = 0;
    for(k=15;k>0;k--) {
      l = k;
      while(l<15) {
        while((tmpVec[l][i]==0)&&(i<15)&&(l<15)){
          i++;
          l++;
        }
      
        while((tmpVec[l][i]==1)&&(i<15)&&(l<15)) {
          help++;
          i++;
          l++;
        }
        if(help >= 4) 
	  if(schraeg < 2)
	    schraeg++;

        help = 0;
      }
      i = 0;
    } 
    }
    System.out.println("oblique = "+schraeg);
    System.out.println("top horizontal = "+owaag);
    System.out.println("middle horizontal = "+mwaag);
    System.out.println("under horizontal = "+uwaag);
    System.out.println("vertical = "+senk);
    System.out.println("re.ob. bow = "+robow);
    System.out.println("re.un. bow = "+rubow);
    System.out.println("gr. bow = "+bigbow);
    System.out.println("lbow = "+lbow);

    // setze input Layer
    inLayer[0].setOutput(schraeg);   // schraeg
    inLayer[1].setOutput(owaag);     // o. waagrecht
    inLayer[2].setOutput(mwaag);     // m. waagrecht
    inLayer[3].setOutput(uwaag);     // u. waagrecht
    inLayer[4].setOutput(senk);      // senkrecht
    inLayer[5].setOutput(robow);     // re.ob. Bogen
    inLayer[6].setOutput(rubow);     // re.un. Bogen
    inLayer[7].setOutput(bigbow);    // grosser re. Bogen
    inLayer[8].setOutput(lbow);      // gr. li. bogen
  }

  public static int returnGrayScaleAverage(int pixel) {
		int red   = (pixel >> 16) & 0xff;
		int green = (pixel >>  8) & 0xff;
		int blue  = (pixel      ) & 0xff;
		// Deal with the pixel as necessary...
		return (red+green+blue)/3;
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
        // first get input vector and initialize input neurons
	    // get Random number
        rand = Math.abs(random.nextInt()%7);
	    for(int l=0;l<9;l++)
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
      int inVec[];
      inVec = tabcan.getVector(); 
      // initialize input neurons
      setInput();
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
  private double neti;                     //netto input
  private char objName;
  private double output;
  private int xpos, ypos;                  //position in lattice
  public double inWeights[] = new double[9];//weighted connections 
  						  //to input-neurons
  public double mapWeights[][] = new double[16][16];	//weighted connections 
  							//to other map-neurons

  Neuron(int xp, int yp)
  {
    neti = 0;
    output = 0;
    xpos = xp;
    ypos = yp;
    objName = 'x';
  
    //init inWeights ...
    Random random = new Random();
    for(int l=0;l<9;l++)
      inWeights[l] = random.nextDouble();
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

  public void setName(char c)
  {
    objName = c;
  }

  public char getName()
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
 
     for(int i=0; i<9; i++)
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

    for(int i=0; i<9; i++)
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
        switch(mapLayer[x][y].getName())
        {
          case 'A':
          {
            outputGraphics.setColor(Color.blue);
            break;
          }
          case 'B':
          {
            outputGraphics.setColor(Color.green);
            break;
          }
          case 'C':
          {
            outputGraphics.setColor(Color.yellow);
            break;
          }
          case 'D':
          {
            outputGraphics.setColor(Color.pink);
            break;
          }
          case 'E':
          {
            outputGraphics.setColor(Color.red);
            break;
          }
          case 'F':
          {
            outputGraphics.setColor(Color.white);
            break;
          }
	  case 'H':
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
    String chars[] = {"= +", "= O", "= I", "= D", 
                      "= S", "= W", "= A"};

    for(int y=0;y<=6;y++)
    {
      g.setColor(colors[y]);
      g.fillOval(10,y*20+15, 9, 9);
      g.setColor(Color.black);
      g.drawString(chars[y], 30,y*20+15+10);
    }
  }
}

