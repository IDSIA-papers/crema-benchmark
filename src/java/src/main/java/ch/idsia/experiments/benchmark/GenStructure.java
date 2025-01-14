package ch.idsia.experiments.benchmark;

import ch.idsia.crema.model.generator.BNGenerator;
import ch.idsia.crema.utility.RandomUtil;

import java.util.HashMap;
import java.util.Map;

public class GenStructure {

    // constant
    static int nGraphs = 3;
    static int maxInducedWidth = 8;

    public static void main(String[] args) throws Exception {
        String[] structs = {"singly", "multi"}; // singly (polytree), multi
        //int[] numNodes = {4,6,8,10};
        int[] numNodes = {5,7,9};
        int[] maxDegree = {6};
        int[] maxInDegree = {2,4,6};
        int[] maxValues = {4}; // binary and ternary variables

        for(int n: numNodes){
            for(int mID: maxInDegree){
                for(int mD: maxDegree){
                    for(int mV: maxValues) {
                        for(String structure : structs){
                            generate(structure, n, mID, mD, mV);
                        }
                    }
                }
            }
        }
    }

    public static void generate(String structure, int n, int mID, int mD, int mV) throws Exception {
        String param_str = structure.substring(0,4)+"_n"+n+"_mID"+mID+"_mD"+mD+"_mV"+mV+"-";
        String format = "xml"; // default format
        String baseFileName = "networks/precise/bnet-"+param_str;

        System.out.println("\n"+baseFileName);

        RandomUtil.setRandomSeed(param_str.hashCode());

        BNGenerator gen = new BNGenerator(n, mD);

        // Set random seed
        System.out.println(param_str.hashCode());

        //// Generation process /////
        // Optional parameters
        Map<String, String> kwargs = new HashMap<>();
        kwargs.put("maxInducedWidth", String.valueOf(maxInducedWidth));
        kwargs.put("nGraphs", String.valueOf(nGraphs));

        // start
        gen.generate(structure, mV, baseFileName, format, kwargs);

        System.out.println("Networks generated!");
    }
}
