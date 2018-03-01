/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
/* &&From
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instance;
import weka.core.Instances;
 * &&To
 */

/**
 *
 * @author Eun Yeong Ahn
 */
public class CLearningModel {
    //Naive Bayes
    /*
     * &&From

    NaiveBayes nb = new NaiveBayes();
    Instances data = null;
    void BuildModel(String path){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(CLearningModel.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            data = new Instances(reader);
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(CLearningModel.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // setting class attribute
        //data.deleteAttributeAt(10);
        data.setClassIndex(data.numAttributes() - 1);
        try {
            nb.buildClassifier(data);
        } catch (Exception ex) {
            Logger.getLogger(CLearningModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    double PredictModel(Instance instance){
        double pred = -1;
        instance.setDataset(data);
        try {
            pred = nb.classifyInstance(instance);
            System.out.println("prediction" + pred);
        } catch (Exception ex) {
            Logger.getLogger(CLearningModel.class.getName()).log(Level.SEVERE, null, ex);
        }

        return pred;
    }

    void UpdateModel(Instance instance){
        instance.setDataset(data);
        try {
            nb.updateClassifier(instance);
        } catch (Exception ex) {
            Logger.getLogger(CLearningModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void ClearModel(){
        System.out.println("cleaer model");
        nb = new NaiveBayes();
        BuildModel(CParameter.ClearArff);
       
    }
     * */
}
