package qcri.dafna.main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import weka.core.Instances;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import net.sf.javaml.classification.tree.RandomTree;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.featureselection.scoring.RELIEF;
import net.sf.javaml.tools.data.FileHandler;
import au.com.bytecode.opencsv.CSVWriter;
import qcri.dafna.allegation.Allegator;
import qcri.dafna.combiner.Combiner;
import qcri.dafna.dataModel.data.DataSet;
import qcri.dafna.dataModel.data.Globals;
import qcri.dafna.dataModel.data.Source;
import qcri.dafna.dataModel.data.SourceClaim;
import qcri.dafna.dataModel.data.ValueBucket;
import qcri.dafna.dataModel.dataFormatter.DataTypeMatcher;
import qcri.dafna.dataModel.quality.voterResults.VoterQualityMeasures;
import qcri.dafna.experiment.ExperimentDataSetConstructor;
import qcri.dafna.voter.Cosine;
import qcri.dafna.voter.MaximumLikelihoodEstimation;
import qcri.dafna.voter.ThreeEstimates;
import qcri.dafna.voter.TwoEstimates;
import qcri.dafna.voter.SimpleLCA;
import qcri.dafna.voter.GuessLCA;
import qcri.dafna.voter.TruthFinder;
import qcri.dafna.voter.Voter;
import qcri.dafna.voter.VoterParameters;
import qcri.dafna.voter.dependence.SourceDependenceModel;
import qcri.dafna.voter.latentTruthModel.LatentTruthModel;
import qcri.dafna.explaination.*;

public class UIMain {

	public static void main(String[] args) throws Exception {

		String outputPath = args[3];

		DataSet ds = createDataSet(args, outputPath);
		VoterQualityMeasures qualityMeasures = launch(args, ds);
		if(! args[args.length -1].equals("Allegate"))
		{
			writeTrustworthiness(ds, outputPath);
			writeConfidence(ds, outputPath);
			MetricsGenerator mg = new MetricsGenerator(ds);
			List<Metrics> allMetrics= mg.generateMetrics(args[args.length - 2], args[args.length -1]);
			writeMetrics(allMetrics, outputPath);
			writeMetricsForFeatureRanking(allMetrics, outputPath);
			
			try 
			{
				Dataset data = FileHandler.loadDataset(new File(outputPath + System.getProperty("file.separator") + "MetricsTrainingData.csv"),12, ",");
				RELIEF reliefF = new RELIEF();
				reliefF.build(data);
				List<Double> scores = new ArrayList<Double>();
				for (int i = 0; i < reliefF.noAttributes(); i++){
					scores.add(reliefF.score(i));
					//System.out.print(reliefF.score(i)+",");
				}
				//Ranking
				/*
				List<Double> scoresSorted = new ArrayList<Double>(scores);
				Collections.sort(scoresSorted);
				int[] rank = new int[scores.size()];
				for(int k = 0; k < scores.size(); k++)
					rank[k] = scoresSorted.size() - scoresSorted.indexOf(scores.get(k));
				*/
			}
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			/*
			try 
			{
				Dataset data = FileHandler.loadDataset(new File(outputPath + System.getProperty("file.separator") + "MetricsTrainingData.csv"),11, ",");
				Random rg = new Random(9);
				RandomTree rTree = new RandomTree(11, rg);
				rTree.buildClassifier(data);
				int correct = 0, wrong = 0;
		        // Classify all instances and check with the correct class values
		        for (Instance inst : data) {
		            Object predictedClassValue = rTree.classify(inst);
		            Object realClassValue = inst.classValue();
		            if (predictedClassValue.equals(realClassValue))
		                correct++;
		            else
		                wrong++;
		        }
		        System.out.println("Correct predictions  " + correct);
		        System.out.println("Wrong predictions " + wrong);				
			}
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
					e.printStackTrace();
			}
			*/
			
			//JBoost
			/*
			String arguments[] = {"-S", "~/home/dalia/DAFNAData/formatted/Books/experimentResults/my/my", "-serialTreeOutput", "~/home/dalia/DAFNAData/formatted/Books/experimentResults/my/atree.serialized"};
			jboost.controller.Controller.main(arguments);
			*/
			
			BufferedReader reader = new BufferedReader(new FileReader("/home/dalia/Downloads/weka_input_train.arff"));
			Instances data = new Instances(reader);
			reader.close();
			// setting class attribute
			data.setClassIndex(data.numAttributes() - 1);
			Classifier cls = new J48();
			cls.buildClassifier(data);
			
			cls.toString();
			
			qualityMeasures.printMeasures();
			System.out.println("Finished");
		}
	}

	private static DataSet createDataSet(String[] args, String outputPath) {
		double toleranceFactor = 0.01; // 0.1 max, 0 min
		String dataSetDirectory = args[1];
		String groundTruthDir = args[2];
		String delim = ",";

		return ExperimentDataSetConstructor.readDataSet(
			dataSetDirectory, toleranceFactor, groundTruthDir, outputPath, delim);
	}
	
	private static VoterQualityMeasures launch(String[] args, DataSet ds) throws IOException {
		VoterQualityMeasures q = null;
		boolean convergence100 = false; // If you change it here please also change in Allegator.java (not paremetrised by this)
		boolean profileMemory = false; // If you change it here please also change in Allegator.java
		// for all voters
		double cosineSimDiff = Double.parseDouble(args[4]);  // 0-1
		double startingTrust = Double.parseDouble(args[5]);  // 0-1
		double startingConf = Double.parseDouble(args[6]);  // 0-1 
		double startingErrorFactor = Double.parseDouble(args[7]);  // 0-1
		VoterParameters params = new VoterParameters(cosineSimDiff, startingTrust, startingConf, startingErrorFactor);
		Voter algo;
		
		// specific params
		String algo_name = args[0];
		switch(algo_name){
		case "Cosine":
			double dampeningFactorCosine = Double.parseDouble(args[8]); // 0-1
			startingConf = Double.parseDouble(args[9]);
			params = new VoterParameters(cosineSimDiff, startingTrust, startingConf,startingErrorFactor);
			algo = new Cosine(ds, params, dampeningFactorCosine);	
			break;
		case "2-Estimates":
			double normalizationWeight = Double.parseDouble(args[8]);
			algo = new TwoEstimates(ds, params,normalizationWeight );
			break;
		case "3-Estimates":
			double ThreeNormalizationWeight = Double.parseDouble(args[8]);
			startingErrorFactor = Double.parseDouble(args[9]);
			params = new VoterParameters(cosineSimDiff, startingTrust, startingConf,startingErrorFactor);
			algo = new ThreeEstimates(ds, params, ThreeNormalizationWeight);
			break;
		case "Depen":
		case "Accu":
		case "AccuSim":
		case "AccuNoDep":
			double alfa = Double.parseDouble(args[8]);
			double c = Double.parseDouble(args[9]);
			int n = Integer.parseInt(args[10]);
			double similarityConstant = Double.parseDouble(args[11]);
			boolean considerSimilarity = args[12].equals("true");
			boolean considerSourcesAccuracy = args[13].equals("true");
			boolean considerDependency = args[14].equals("true");
			boolean orderSrcByDependence = args[15].equals("true");
			algo = new SourceDependenceModel(ds, params, alfa, c, n, similarityConstant, considerSimilarity, considerSourcesAccuracy, considerDependency, orderSrcByDependence);
			break;
		case "TruthFinder":
			double similarityConstantTF = Double.parseDouble(args[8]); // 0-1
			double dampeningFactorTF = Double.parseDouble(args[9]); // 0-1
			algo = new TruthFinder(ds, params, similarityConstantTF, dampeningFactorTF);
			break;
		case "SimpleLCA":
			double Simplebeta1LCA = Double.parseDouble(args[8]);
			algo = new SimpleLCA(ds, params, Simplebeta1LCA);
			break;
		case "GuessLCA":
			double beta1LCA = Double.parseDouble(args[8]);
			algo = new GuessLCA(ds, params, beta1LCA);
			break;
		case "MLE":
			double beta1MLE = Double.parseDouble(args[8]);
			double rMLE = Double.parseDouble(args[9]);
			algo = new MaximumLikelihoodEstimation(ds, params, beta1MLE, rMLE );
			break;
		case "LTM":
			double b1 = Double.parseDouble(args[8]);
			double b0 = Double.parseDouble(args[9]);
			double a00 = Double.parseDouble(args[10]);
			double a01 = Double.parseDouble(args[11]);
			double a10 = Double.parseDouble(args[12]);
			double a11 = Double.parseDouble(args[13]);
			int iterationCount = Integer.parseInt(args[14]);
			int burnIn = Integer.parseInt(args[15]);
			int sampleGap = Integer.parseInt(args[16]);
			algo = new LatentTruthModel(ds, params, b1, b0, a00, a01,a10, a11, iterationCount, burnIn, sampleGap);
			break;
		case "Combine":
			int number_algorithms = Integer.parseInt(args[8]);
			String[] confidenceFilePaths = new String[number_algorithms];
			int i = 0;
			while(i < number_algorithms)
			{
				confidenceFilePaths[i] = args[i+9];
				i++;
			}
			algo = new Combiner(ds, params, number_algorithms, confidenceFilePaths);
			break;
			
		default:
			throw new RuntimeException("Unknown algorithm specified '" + algo_name + "'");
		}
		
		if (args[args.length -1].equals("Allegate")) {
			String claimID = args[args.length - 4];
			String confFilePath = args[args.length - 3];
			String trustFilePath = args[args.length - 2];
			Allegator allegator = new Allegator(ds, algo, claimID);
			int fakeSourceCount = allegator.Allegate(confFilePath, trustFilePath);
			if(fakeSourceCount != 0)
				writeAllegation(ds, fakeSourceCount,args[3]);
		}
		else
		{
			q = algo.launchVoter(convergence100, profileMemory);
		}
		return q;
	}
	
	
	public static void writeMetricsForFeatureRanking(List<Metrics> metrics, String outputPath)
	throws IOException {
		String metricsFile = outputPath + System.getProperty("file.separator") + "MetricsTrainingData.csv";
		BufferedWriter metricsWriter;
		metricsWriter = Files.newBufferedWriter(Paths.get(metricsFile), Globals.FILE_ENCODING, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		CSVWriter csvWriter = new CSVWriter(metricsWriter, ',', CSVWriter.NO_QUOTE_CHARACTER);
		for(Metrics metricsRow : metrics){
			writeMetricsRowsForFeatureRanking(csvWriter, String.valueOf(metricsRow.getCv()), String.valueOf(metricsRow.getTrust()), String.valueOf(metricsRow.getMinTrust()), String.valueOf(metricsRow.getMaxTrust()), String.valueOf(metricsRow.getNbSS()), String.valueOf(metricsRow.getNbC()), String.valueOf(metricsRow.getNbDI()),String.valueOf(metricsRow.getTotalSources()), String.valueOf(metricsRow.getCvGlobal()), String.valueOf(metricsRow.getCvLocal()), String.valueOf(metricsRow.getTrustGlobal()), String.valueOf(metricsRow.getTrustLocal()), metricsRow.getTruthLabel());
		}
		metricsWriter.close();
	}
			
	private static void writeMetricsRowsForFeatureRanking(CSVWriter writer, String cv, String trust, String minTrust, String maxTrust, String nbSS, String nbC, String totalSources, String nbDI, String cvLocal, String cvGlobal, String trustGlobal, String trustLocal, String truthLabel) {
		String [] lineComponents = new String[]{cv, trust, minTrust, maxTrust, nbSS, nbC, totalSources, nbDI, cvLocal, cvGlobal, trustGlobal, trustLocal, truthLabel};
		writer.writeNext(lineComponents);
	}
	
	
	public static void writeMetrics(List<Metrics> metrics, String outputPath)
	throws IOException {
		String metricsFile = outputPath + System.getProperty("file.separator") + "Metrics.csv";
		BufferedWriter metricsWriter;
		metricsWriter = Files.newBufferedWriter(Paths.get(metricsFile), Globals.FILE_ENCODING, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		CSVWriter csvWriter = new CSVWriter(metricsWriter, ',');
		//*header*
		writeMetricsRows(csvWriter, "claimID", "Cv", "Trust", "Trust of minimum TrustWorthySource", "Trust of maximum TrustWorthySource", "Supporting Sources", "Opposing Sources", "Total Sources", "Distict Values", "Global Confidence Comparison", "Local Confidence Comparison", "Global Trust Comparison", "Local Trust Comparison", "Truth Label");
		for(Metrics metricsRow : metrics){
			writeMetricsRows(csvWriter, metricsRow.getClaimID(), String.valueOf(metricsRow.getCv()), String.valueOf(metricsRow.getTrust()), String.valueOf(metricsRow.getMinTrust()), String.valueOf(metricsRow.getMaxTrust()), String.valueOf(metricsRow.getNbSS()), String.valueOf(metricsRow.getNbC()), String.valueOf(metricsRow.getTotalSources()), String.valueOf(metricsRow.getNbDI()), String.valueOf(metricsRow.getCvGlobal()), String.valueOf(metricsRow.getCvLocal()), String.valueOf(metricsRow.getTrustGlobal()), String.valueOf(metricsRow.getTrustLocal()), metricsRow.getTruthLabel());
		}
		metricsWriter.close();
	}
			
	private static void writeMetricsRows(CSVWriter writer, String claimID, String cv, String trust, String minTrust, String maxTrust, String nbSS, String nbC, String totalSources, String nbDI, String cvGlobal, String cvLocal, String trustGlobal, String trustLocal, String truthLabel) {
		String [] lineComponents = new String[]{claimID, cv, trust, minTrust, maxTrust, nbSS, nbC, totalSources, nbDI, cvGlobal, cvLocal, trustGlobal, trustLocal, truthLabel};
		writer.writeNext(lineComponents);
	}
			
	public static void writeAllegation(DataSet ds, int fakeSourceCount, String outputPath)
	throws IOException {
		String allegationClaimsFile = outputPath + System.getProperty("file.separator") + "AllegationClaims.csv";
		BufferedWriter allegationWriter;
		allegationWriter = Files.newBufferedWriter(Paths.get(allegationClaimsFile), 
				Globals.FILE_ENCODING, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		CSVWriter csvWriter = new CSVWriter(allegationWriter, ',');

		/*header*/
		writeAllegationClaims(csvWriter, "ObjectID", "PropertyID", "PropertyValue", "SourceID", "TimeStamp");
		for(int i = 0; i < fakeSourceCount; i++){
			String SourceIdentifier = Globals.fakeSourceName+String.valueOf(i);
			for(SourceClaim claim : ds.getSourcesHash().get(SourceIdentifier).getClaims())
			{
				writeAllegationClaims(csvWriter, claim.getObjectIdentifier(), claim.getPropertyName(), claim.getPropertyValueString(), claim.getSource().getSourceIdentifier(), claim.getTimeStamp());
			}
		}
		allegationWriter.close();
	}
	
	private static void writeAllegationClaims(CSVWriter writer, String objectID, String propertyID, String propertyValue, String sourceID, String timeStamp) {
		String [] lineComponents = new String[]{objectID, propertyID, propertyValue, sourceID, timeStamp};
		writer.writeNext(lineComponents);
	}
	
	private static void writeConfidence(DataSet ds, String outputPath)
	throws IOException {
		String confidenceResultFile = outputPath + System.getProperty("file.separator") + "Confidences.csv";
		BufferedWriter confidenceWriter;
		confidenceWriter = Files.newBufferedWriter(Paths.get(confidenceResultFile), 
				Globals.FILE_ENCODING, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		CSVWriter csvWriter = new CSVWriter(confidenceWriter, ',');

		/*header*/
		writeConfidenceResult(csvWriter, "ClaimID", "Confidence", "IsTrue", "BucketId");
		for (List<ValueBucket> bList : ds.getDataItemsBuckets().values()) {
			for (ValueBucket b : bList) {
				for (SourceClaim claim :  b.getClaims()) {
					writeConfidenceResult(csvWriter, String.valueOf(claim.getId()), 
							String.valueOf(b.getConfidence()), String.valueOf(claim.isTrueClaimByVoter()), String.valueOf(b.getId()));
				}
			}
		}
		confidenceWriter.close();
	}
	
	private static void writeConfidenceResult(CSVWriter writer, String claimId,	String confidence, String trueOrFalse, String bucketValue) {
		String [] lineComponents = new String[]{claimId, confidence, trueOrFalse, bucketValue};
		writer.writeNext(lineComponents);
	}

	private static void writeTrustworthiness(DataSet ds, String outputPath) 
	throws IOException{
		String trustworthinessResultsFile = outputPath + System.getProperty("file.separator") + "Trustworthiness.csv";
		BufferedWriter trustworthinessWriter;
		trustworthinessWriter = Files.newBufferedWriter(Paths.get(trustworthinessResultsFile), 
				Globals.FILE_ENCODING, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
		CSVWriter csvWriter = new CSVWriter(trustworthinessWriter , ',');
		/* add header */
		writeTrustworthiness(csvWriter, "SourceID", "Trustworthiness");
		HashMap<String, Source> map = ds.getSourcesHash();
		for(String key: map.keySet()){
			writeTrustworthiness(csvWriter, key, String.valueOf(map.get(key).getTrustworthiness()));
		}
		trustworthinessWriter.close();
	}
	
	private static void writeTrustworthiness(CSVWriter writer, String sourceId,	String trustworthiness) {
		String [] lineComponents = new String[]{sourceId, trustworthiness};
		writer.writeNext(lineComponents);
	}

}
