package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.ArrayList;

/******************************************************************************

******************************************************************************/

/**
 *
 *                        Class LstSquQuadRegr
     A C#  Class for Least Squares Regression for Quadratic Curve Fitting
 @author                         Alex Etchells  2010
http://www.codeproject.com/KB/recipes/QuadraticRegression.aspx
*/
public  class LstSquQuadRegr
{
     /* instance variables */
    ArrayList<float[]> pointArray = new ArrayList();
    private int numOfEntries;
    private float[] pointpair;

    /*constructor */
    public LstSquQuadRegr()
    {
        numOfEntries = 0;
        pointpair = new float[2];
    }

    /*instance methods */
    /// <summary>
    /// add point pairs
    /// </summary>
    /// <param name="x">x value</param>
    /// <param name="y">y value</param>
    public void addPoints(float x, float y)
    {
        pointpair = new float[2];
        numOfEntries +=1;
        pointpair[0] = x;
        pointpair[1] = y;
        pointArray.add(pointpair);
    }

    /// <summary>
    /// returns the a term of the equation ax^2 + bx + c
    /// </summary>
    /// <returns>a term</returns>
    public float aTerm()
    {
        assert numOfEntries>2 ;
        //notation sjk to mean the sum of x_i^j*y_i^k.
        float s40 = getSx4(); //sum of x^4
        float s30 = getSx3(); //sum of x^3
        float s20 = getSx2(); //sum of x^2
        float s10 = getSx();  //sum of x
        float s00 = numOfEntries;
        //sum of x^0 * y^0  ie 1 * number of entries

        float s21 = getSx2y(); //sum of x^2*y
        float s11 = getSxy();  //sum of x*y
        float s01 = getSy();   //sum of y

        //a = Da/D
        return (s21*(s20 * s00 - s10 * s10) -
                s11*(s30 * s00 - s10 * s20) +
                s01*(s30 * s10 - s20 * s20))
                /
                (s40*(s20 * s00 - s10 * s10) -
                 s30*(s30 * s00 - s10 * s20) +
                 s20*(s30 * s10 - s20 * s20));
    }

    /// <summary>
    /// returns the b term of the equation ax^2 + bx + c
    /// </summary>
    /// <returns>b term</returns>
    public float bTerm()
    {
        assert numOfEntries>2 ;
        //notation sjk to mean the sum of x_i^j*y_i^k.
        float s40 = getSx4(); //sum of x^4
        float s30 = getSx3(); //sum of x^3
        float s20 = getSx2(); //sum of x^2
        float s10 = getSx();  //sum of x
        float s00 = numOfEntries;
        //sum of x^0 * y^0  ie 1 * number of entries

        float s21 = getSx2y(); //sum of x^2*y
        float s11 = getSxy();  //sum of x*y
        float s01 = getSy();   //sum of y

        //b = Db/D
        return (s40*(s11 * s00 - s01 * s10) -
                s30*(s21 * s00 - s01 * s20) +
                s20*(s21 * s10 - s11 * s20))
                /
                (s40 * (s20 * s00 - s10 * s10) -
                 s30 * (s30 * s00 - s10 * s20) +
                 s20 * (s30 * s10 - s20 * s20));
    }

    /// <summary>
    /// returns the c term of the equation ax^2 + bx + c
    /// </summary>
    /// <returns>c term</returns>
    public float cTerm()
    {
        assert numOfEntries>2 ;
        //notation sjk to mean the sum of x_i^j*y_i^k.
        float s40 = getSx4(); //sum of x^4
        float s30 = getSx3(); //sum of x^3
        float s20 = getSx2(); //sum of x^2
        float s10 = getSx();  //sum of x
        float s00 = numOfEntries;
        //sum of x^0 * y^0  ie 1 * number of entries

        float s21 = getSx2y(); //sum of x^2*y
        float s11 = getSxy();  //sum of x*y
        float s01 = getSy();   //sum of y

        //c = Dc/D
        return (s40*(s20 * s01 - s10 * s11) -
                s30*(s30 * s01 - s10 * s21) +
                s20*(s30 * s11 - s20 * s21))
                /
                (s40 * (s20 * s00 - s10 * s10) -
                 s30 * (s30 * s00 - s10 * s20) +
                 s20 * (s30 * s10 - s20 * s20));
    }

    public float rSquare() // get r-squared
    {
         assert numOfEntries>2 ;
        // 1 - (residual sum of squares / total sum of squares)
        return 1 - getSSerr() / getSStot();
    }


    /*helper methods*/
    private float getSx() // get sum of x
    {
        float Sx = 0;
        for(float[] ppair:pointArray)
        {
            Sx += ppair[0];
        }
        return Sx;
    }

    private float getSy() // get sum of y
    {
        float Sy = 0;
        for(float[] ppair:pointArray)
        {
            Sy += ppair[1];
        }
        return Sy;
    }

    private float getSx2() // get sum of x^2
    {
        float Sx2 = 0;
         for(float[] ppair:pointArray)
        {
            Sx2 += Math.pow(ppair[0], 2); // sum of x^2
        }
        return Sx2;
    }

    private float getSx3() // get sum of x^3
    {
        float Sx3 = 0;
        for(float[] ppair:pointArray)
        {
            Sx3 += Math.pow(ppair[0], 3); // sum of x^3
        }
        return Sx3;
    }

    private float getSx4() // get sum of x^4
    {
        float Sx4 = 0;
         for(float[] ppair:pointArray)
        {
            Sx4 += Math.pow(ppair[0], 4); // sum of x^4
        }
        return Sx4;
    }

    private float getSxy() // get sum of x*y
    {
        float Sxy = 0;
         for(float[] ppair:pointArray)
        {
            Sxy += ppair[0] * ppair[1]; // sum of x*y
        }
        return Sxy;
    }

    private float getSx2y() // get sum of x^2*y
    {
        float Sx2y = 0;
         for(float[] ppair:pointArray)
        {
            Sx2y += Math.pow(ppair[0], 2) * ppair[1]; // sum of x^2*y
        }
        return Sx2y;
    }

    private float getYMean() // mean value of y
    {
        float y_tot = 0;
         for(float[] ppair:pointArray)
        {
            y_tot += ppair[1];
        }
        return y_tot/numOfEntries;
    }

    private float getSStot() // total sum of squares
    {
        //the sum of the squares of the differences between
        //the measured y values and the mean y value
        float ss_tot = 0;
          for(float[] ppair:pointArray)
        {
            ss_tot += Math.pow(ppair[1] - getYMean(), 2);
        }
        return ss_tot;
    }

    private float getSSerr() // residual sum of squares
    {
        //the sum of the squares of te difference between
        //the measured y values and the values of y predicted by the equation
        float ss_err = 0;
         for(float[] ppair:pointArray)
        {
            ss_err += Math.pow(ppair[1] - getPredictedY(ppair[0]), 2);
        }
        return ss_err;
    }

    private float getPredictedY(float x)
    {
        //returns value of y predicted by the equation for a given value of x
        return (float)(aTerm() * Math.pow(x, 2) + bTerm() * x + cTerm());
    }
}