package qcri.dafna.main;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;
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
import qcri.dafna.voter.VoterParameters;
import qcri.dafna.voter.dependence.SourceDependenceModel;
import qcri.dafna.voter.latentTruthModel.LatentTruthModel;

public class UIMain {

	public static void main(String[] args) throws IOException {

		String outputPath = args[3];

		DataSet ds = createDataSet(args, outputPath);
		VoterQualityMeasures qualityMeasures = launch(args, ds);
		writeTrustworthiness(ds, outputPath);
		writeConfidence(ds, outputPath);
		
		qualityMeasures.printMeasures();
		System.out.println("Finished");
	}

	private static DataSet createDataSet(String[] args, String outputPath) {
		double toleranceFactor = 0.01; // 0.1 max, 0 min
		String dataSetDirectory = args[1];
		String groundTruthDir = args[2];
		String delim = ",";

		return ExperimentDataSetConstructor.readDataSet(
			dataSetDirectory, toleranceFactor, groundTruthDir, outputPath, delim);
	}
	
	private static VoterQualityMeasures launch(String[] args, DataSet ds) {
		VoterQualityMeasures q = null;
		boolean convergence100 = false;
		boolean profileMemory = false;
		// for all voters
		double cosineSimDiff = Double.parseDouble(args[4]);  // 0-1
		double startingTrust = Double.parseDouble(args[5]);  // 0-1
		double startingConf = Double.parseDouble(args[6]);  // 0-1 
		double startingErrorFactor = Double.parseDouble(args[7]);  // 0-1
		VoterParameters params = new VoterParameters(cosineSimDiff, startingTrust, startingConf, startingErrorFactor);

		// specific params
		String algo_name = args[0];
		switch(algo_name){
		case "Cosine":
			double dampeningFactorCosine = Double.parseDouble(args[8]); // 0-1
			startingConf = Double.parseDouble(args[9]);
			params = new VoterParameters(cosineSimDiff, startingTrust, startingConf,startingErrorFactor);
			Cosine algo1 = new Cosine(ds, params, dampeningFactorCosine);	
			q = algo1.launchVoter(convergence100 , profileMemory);

			break;
		case "2-Estimates":
			double normalizationWeight = Double.parseDouble(args[8]);
			TwoEstimates algo2 = new TwoEstimates(ds, params,normalizationWeight );
			q = algo2.launchVoter(convergence100 , profileMemory);
			break;
		case "3-Estimates":
			double ThreeNormalizationWeight = Double.parseDouble(args[8]);
			startingErrorFactor = Double.parseDouble(args[9]);
			params = new VoterParameters(cosineSimDiff, startingTrust, startingConf,startingErrorFactor);
			ThreeEstimates algo3 = new ThreeEstimates(ds, params, ThreeNormalizationWeight);
			q = algo3.launchVoter(convergence100 , profileMemory);
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
			SourceDependenceModel algo4 = new SourceDependenceModel(ds, params, alfa, c, n, similarityConstant, considerSimilarity, considerSourcesAccuracy, considerDependency, orderSrcByDependence);
			q = algo4.launchVoter(convergence100, profileMemory);
			break;
		case "TruthFinder":
			double similarityConstantTF = Double.parseDouble(args[8]); // 0-1
			double dampeningFactorTF = Double.parseDouble(args[9]); // 0-1
			TruthFinder algo5 = new TruthFinder(ds, params, similarityConstantTF, dampeningFactorTF);
			q = algo5.launchVoter(convergence100, profileMemory);
			break;
		case "SimpleLCA":
			double Simplebeta1LCA = Double.parseDouble(args[8]);
			SimpleLCA algo6 = new SimpleLCA(ds, params, Simplebeta1LCA);
			q = algo6.launchVoter(convergence100, profileMemory);
			break;
		case "GuessLCA":
			double beta1LCA = Double.parseDouble(args[8]);
			GuessLCA algo7 = new GuessLCA(ds, params, beta1LCA);
			q = algo7.launchVoter(convergence100, profileMemory);
			break;
		case "MLE":
			double beta1MLE = Double.parseDouble(args[8]);
			double rMLE = Double.parseDouble(args[9]);
			MaximumLikelihoodEstimation algo8 = new MaximumLikelihoodEstimation(ds, params, beta1MLE, rMLE );
			q = algo8.launchVoter(convergence100, profileMemory);
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
			LatentTruthModel algo9 = new LatentTruthModel(ds, params, b1, b0, a00, a01,a10, a11, iterationCount, burnIn, sampleGap);
			q = algo9.launchVoter(convergence100, profileMemory);
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
			Combiner algo10 = new Combiner(ds, params, number_algorithms, confidenceFilePaths);
			q = algo10.launchVoter(convergence100, profileMemory);
			break;
			
		default:
			throw new RuntimeException("Unknown algorithm specified '" + algo_name + "'");
		}
		
		return q;
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
