package qcri.dafna.combiner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;
import qcri.dafna.dataModel.data.DataSet;
import qcri.dafna.dataModel.data.Globals;
import qcri.dafna.dataModel.data.SourceClaim;
import qcri.dafna.dataModel.data.ValueBucket;
import qcri.dafna.dataModel.quality.voterResults.VoterQualityMeasures;
import qcri.dafna.experiment.ExperimentDataSetConstructor;
import qcri.dafna.voter.VoterParameters;

public class LaunchCombiner {
	
	public static void main(String args[])
	{
		launchCombiner();
	}
	
	public static void launchCombiner()
	{
		double toleranceFactor = 0.01; // 0.1 max, 0 min
		String dataSetDirectory = Globals.directory_formattedDAFNADataset_Books_Folder + "/claims";
		String groundTruthDir = Globals.directory_formattedDAFNADataset_Books_Folder + "/truth";
		String outputPath = Globals.directory_formattedDAFNADataset_Books_Folder + "/experimentResult";
		String delim = ",";
		DataSet ds = ExperimentDataSetConstructor.readDataSet(dataSetDirectory, toleranceFactor, groundTruthDir, outputPath, delim);
		
		VoterQualityMeasures q = null;
		boolean convergence100 = false;
		boolean profileMemory = false;
		double cosineSimDiff = 0;  // 0-1
		double startingTrust = 0;  // 0-1
		double startingConf = 0;  // 0-1 
		double startingErrorFactor = 0;  // 0-1
		VoterParameters params = new VoterParameters(cosineSimDiff, startingTrust, startingConf, startingErrorFactor);
		int n = 3;
		
		String[] confidenceFilePaths = new String[3];
		confidenceFilePaths[0] = "/home/dalia/Desktop/Backups/results/1/Confidences.csv";
		confidenceFilePaths[1] = "/home/dalia/Desktop/Backups/results/2/Confidences.csv";
		confidenceFilePaths[2] = "/home/dalia/Desktop/Backups/results/3/Confidences.csv";
		Combiner algo5 = new Combiner(ds, params, n, confidenceFilePaths);
		q = algo5.launchVoter(convergence100, profileMemory);
		System.out.println(q.getPrecision());
		System.out.println(q.getRecall());
		System.out.println(q.getAccuracy());
		System.out.println(q.getSpecificity());
		
		String confidenceResultFile = outputPath + System.getProperty("file.separator") + "Confidences.csv";
		BufferedWriter confidenceWriter;
		try {
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
		} catch (IOException e) {
			System.out.println("Cannot write the confidence results");
			e.printStackTrace();
		}
	}
	
	private static void writeConfidenceResult(CSVWriter writer, String claimId,	String confidence, String trueOrFalse, String bucketValue) {
		String [] lineComponents = new String[]{claimId, confidence, trueOrFalse, bucketValue};
		writer.writeNext(lineComponents);
	}
}
