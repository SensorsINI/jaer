package Matplotlib;

import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tobid
 */
public class MatplotlibTest {

    public static void main(String[] args){
        Plot plt = Plot.create();
        plt.plot()
                .add(Arrays.asList(1.3, 2))
                .label("label")
                .linestyle("--");
        plt.xlabel("xlabel");
        plt.ylabel("ylabel");
        plt.text(0.5, 0.2, "text");
        plt.title("Title!");
        plt.legend();
        try {
            plt.show();
        } catch (IOException ex) {
            Logger.getLogger(MatplotlibTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (PythonExecutionException ex) {
            Logger.getLogger(MatplotlibTest.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
