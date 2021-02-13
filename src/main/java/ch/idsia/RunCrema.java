package ch.idsia;

import ch.idsia.crema.IO;
import ch.idsia.crema.factor.GenericFactor;
import ch.idsia.crema.factor.credal.linear.IntervalFactor;
import ch.idsia.crema.inference.Inference;
import ch.idsia.crema.inference.approxlp.CredalApproxLP;
import ch.idsia.crema.inference.ve.CredalVariableElimination;
import ch.idsia.crema.model.graphical.DAGModel;
import ch.idsia.crema.utility.hull.ConvexHull;
import ch.idsia.experiments.Convert;
import ch.javasoft.util.ints.IntHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.commons.lang3.time.StopWatch;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.sound.midi.Soundbank;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Stream;


/*
-et --method=cve -r 2 -w 1 -x 0 -y 1 --log=./logs/logfile.log ./networks/vmodel/vmodel-mult_n4_mID2_mD6_mV4_nV4-3.uai
 */

public class RunCrema implements Runnable {

	private static String argStr;

	private static Logger logger;

	private GenericFactor result;
	private GenericFactor exactResult;
	private Long time;

	enum InferenceMethod {approxlp, intervallp, cve, cve_ch, cve_ch10, cve_ch5}

	// Set as hash map
	private final static List<InferenceMethod> VE_METHODS = List.of(
			InferenceMethod.cve,
			InferenceMethod.cve_ch,
			InferenceMethod.cve_ch5,
			InferenceMethod.cve_ch10
		);

	private final static List<InferenceMethod> APPROX_METHODS = List.of(
			InferenceMethod.approxlp,
			InferenceMethod.intervallp,
			InferenceMethod.cve_ch5,
			InferenceMethod.cve_ch10
	);
	private static Map<InferenceMethod, ConvexHull.Method> CH_METHODS = Map.ofEntries(
			Map.entry(InferenceMethod.cve_ch, ConvexHull.Method.LP_CONVEX_HULL),
			Map.entry(InferenceMethod.cve_ch5, ConvexHull.Method.REDUCED_HULL_5),
			Map.entry(InferenceMethod.cve_ch10, ConvexHull.Method.REDUCED_HULL_10)
	);



	/// Command line

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@Option(names = {"-m", "--method"}, required = true, description = "Inference method: ${COMPLETION-CANDIDATES}")
	private InferenceMethod method;

	@Option(names = {"-x", "--target"}, required = true, description = "Target variable ID.")
	private int target;

	@Option(names = {"-y", "--observed"}, description = "Observed variable ID.")
	private int observed = -1;

	@Option(names = {"-t", "--time"}, description = "Measure time")
	private boolean measureTime;

	@Option(names = {"-e", "--error"}, description = "Measure error")
	private boolean measureError;

	@Option(names = {"-w", "--warmups"}, description = "Number of warmups (which are not measured). Default is 0.")
	public void setWarmups(int w){
		if(w<0) wrongParam("The number of warmups cannot be negative.");
		warmups = w;
	}
	private int warmups = 0;

	@Option(names = {"-r", "--runs"}, description = "Number of runs. Default is 1.")
	public void setRuns(int r){
		if(r<1) wrongParam("The number of runs cannot be lower than 1.");
		runs = r;
	}
	private int runs = 1;

	@Option(names={"-l", "--log"}, description = "Log file path. If not specified, messages are shown on standard output.")
	String logFile;

	@Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
	private boolean helpRequested;


	@Parameters(description = "Model path in UAI format.")
	private String modelPath;

	private void wrongParam(String msg){
		throw new CommandLine.ParameterException(spec.commandLine(), msg);
	}

	public static void main(String[] args) {
		argStr = String.join(" ", args);
		CommandLine.run(new RunCrema(), args);
	}



	@Override
	public void run() {

		try {
			setUp();
			logger.info("Input args: " + argStr);

			experiments();
			processResults();
		}catch (Exception e){
			logger.severe(e.toString());
			e.printStackTrace();
			System.exit(-1);

		}catch (Error e){
			logger.severe(e.toString());
			System.exit(-1);
		}


	}

	private void experiments() throws IOException, InterruptedException {
		logger.info("Starting experiments");

		// Load the model
		DAGModel model = (DAGModel) IO.readUAI(modelPath);
		logger.info("Loaded model "+model.getNetwork());

		StopWatch watch = new StopWatch();


		// Run the warmup iterations
		for(int i=1; i<=warmups; i++) {
			watch.reset();
			watch.start();
			evaluate(model, method);
			watch.stop();
			logger.info("Warmup iteration "+i+"/"+warmups+" ("+watch.getTime()+" ms.)");
		}


		// Run the measurable experiments
		time = 0L;
		for(int i=1; i<=runs; i++) {
			watch.reset();
			watch.start();
			result = evaluate(model, method);
			watch.stop();
			time += watch.getTime();
			logger.info("Measurable iteration "+i+"/"+runs+" ("+watch.getTime()+" ms.)");

		}

		// If needed, run the exact
		if(measureError) {
			exactResult = result;
			if(APPROX_METHODS.contains(method)) {
				logger.info("Running exact inference");
				// Convert model to V-space (if not VE-based)
				DAGModel exactModel = model;
				if(method == InferenceMethod.approxlp)
					exactModel = Convert.HmodelToVmodel(model);
				// Run the exact inference
				exactResult = evaluate(exactModel, InferenceMethod.cve);

			}
		}

	}

	private GenericFactor evaluate(DAGModel model, InferenceMethod method) throws InterruptedException {
		Inference inf = null;

		// Set up the inference engine
		if(VE_METHODS.contains(method)){
			inf = new CredalVariableElimination(model);
			// Set convex hull
			if(CH_METHODS.keySet().contains(method))
				((CredalVariableElimination)inf).setConvexHullMarg(CH_METHODS.get(method));
		}else if(method==InferenceMethod.approxlp){
			inf = new CredalApproxLP(model);
		}



		TIntIntHashMap evid = new TIntIntHashMap();
		if(observed != -1)
			evid.put(observed, 0);

		//if(List.of(inf.getInferenceModel(target, evid).getVariables()).contains(observed))
		//	return inf.query(target, evid);

		return inf.query(target, evid);

	}

	private void processResults(){
		logger.info("Processing results");
	}

	private void setUp(){

		logger = Logger.getLogger("MyLog");
		FileHandler fh = null;

		// This block configure the logger with handler and formatter
		try {
			if(logFile == null)
				logFile = File.createTempFile("RunCrema", ".log").getAbsolutePath();

			System.out.println(logFile);
			fh = new FileHandler(logFile, true);

		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.addHandler(fh);
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF_%1$tT][%4$s][java] %5$s%6$s%n");
		SimpleFormatter formatter = new SimpleFormatter();
		fh.setFormatter(formatter);
		logger.info("Saving log to: "+logFile);
	}

}