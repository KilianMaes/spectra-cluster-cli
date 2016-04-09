package uk.ac.ebi.pride.spectracluster.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import uk.ac.ebi.pride.spectracluster.cdf.CdfLearner;
import uk.ac.ebi.pride.spectracluster.cdf.CdfResult;
import uk.ac.ebi.pride.spectracluster.cdf.CumulativeDistributionFunction;
import uk.ac.ebi.pride.spectracluster.cdf.IComparisonProgressListener;
import uk.ac.ebi.pride.spectracluster.cluster.ICluster;
import uk.ac.ebi.pride.spectracluster.clustering.BinaryFileClusterer;
import uk.ac.ebi.pride.spectracluster.clustering.BinaryFileClusteringCallable;
import uk.ac.ebi.pride.spectracluster.clustering.BinaryClusterFileReference;
import uk.ac.ebi.pride.spectracluster.conversion.MergingCGFConverter;
import uk.ac.ebi.pride.spectracluster.io.*;
import uk.ac.ebi.pride.spectracluster.merging.BinaryFileMergingClusterer;
import uk.ac.ebi.pride.spectracluster.spectra_list.*;
import uk.ac.ebi.pride.spectracluster.util.BinaryFileScanner;
import uk.ac.ebi.pride.spectracluster.util.CliSettings;
import uk.ac.ebi.pride.spectracluster.util.Defaults;
import uk.ac.ebi.pride.spectracluster.util.MissingParameterException;
import uk.ac.ebi.pride.spectracluster.util.function.peak.HighestNPeakFunction;

import java.io.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: jg
 * Date: 9/15/13
 * Time: 11:19 AM
 * To change this template use File | Settings | File Templates.
 */
public class SpectraClusterCliMain implements IComparisonProgressListener {
    public final static int MAJOR_PEAK_CLUSTERING_JOBS = 4;
    public static final boolean DELETE_TEMPORARY_CLUSTERING_RESULTS = true;

    public static void main(String[] args) {
        CommandLineParser parser = new GnuParser();

        try {
            CommandLine commandLine = parser.parse(CliOptions.getOptions(), args);

            // HELP
            if (commandLine.hasOption(CliOptions.OPTIONS.HELP.getValue())) {
                printUsage();
                return;
            }

            // RESULT FILE PATH
            if (!commandLine.hasOption(CliOptions.OPTIONS.OUTPUT_PATH.getValue()))
                throw new MissingParameterException("Missing required option " + CliOptions.OPTIONS.OUTPUT_PATH.getValue());
            File finalResultFile = new File(commandLine.getOptionValue(CliOptions.OPTIONS.OUTPUT_PATH.getValue()));

            if (finalResultFile.exists())
                throw new Exception("Result file " + finalResultFile + " already exists");

            // NUMBER OF JOBS
            int nMajorPeakJobs = MAJOR_PEAK_CLUSTERING_JOBS;
            if (commandLine.hasOption(CliOptions.OPTIONS.MAJOR_PEAK_JOBS.getValue())) {
                nMajorPeakJobs = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.MAJOR_PEAK_JOBS.getValue()));
            }

            // NUMBER OF ROUNDS
            int rounds = 4;
            if (commandLine.hasOption(CliOptions.OPTIONS.ROUNDS.getValue()))
                rounds = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ROUNDS.getValue()));

            // START THRESHOLD
            float startThreshold = 0.999F;
            if (commandLine.hasOption(CliOptions.OPTIONS.START_THRESHOLD.getValue()))
                startThreshold = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.START_THRESHOLD.getValue()));

            // END THRESHOLD
            float endThreshold = 0.99F;
            if (commandLine.hasOption(CliOptions.OPTIONS.END_THRESHOLD.getValue()))
                endThreshold = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.END_THRESHOLD.getValue()));

            List<Float> thresholds = generateThreshold(startThreshold, endThreshold, rounds);

            // PRECURSOR TOLERANCE
            if (commandLine.hasOption(CliOptions.OPTIONS.PRECURSOR_TOLERANCE.getValue())) {
                float precursorTolerance = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.PRECURSOR_TOLERANCE.getValue()));
                Defaults.setDefaultPrecursorIonTolerance(precursorTolerance);
            }

            // FRAGMENT ION TOLERANCE
            if (commandLine.hasOption(CliOptions.OPTIONS.FRAGMENT_TOLERANCE.getValue())) {
                float fragmentTolerance = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.FRAGMENT_TOLERANCE.getValue()));
                Defaults.setFragmentIonTolerance(fragmentTolerance);
            }

            // BINARY TMP DIR
            File binaryTmpDirectory;

            if (commandLine.hasOption(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue()))
                binaryTmpDirectory = new File(commandLine.getOptionValue(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue()));
            else
                binaryTmpDirectory = createTemporaryDirectory("binary_converted_spectra");

            // KEEP BINARY FILES
            boolean keepBinaryFiles = commandLine.hasOption(CliOptions.OPTIONS.KEEP_BINARY_FILE.getValue());

            // RE-USE BINARY FILES
            boolean reUseBinaryFiles = commandLine.hasOption(CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue());

            // FAST MODE
            boolean useFastMode = commandLine.hasOption(CliOptions.OPTIONS.FAST_MODE.getValue());

            // FILES TO PROCESS
            String[] peaklistFilenames = commandLine.getArgs();

            // if re-use is set, binaryTmpDirectory is required and merging is impossible
            if (reUseBinaryFiles && !commandLine.hasOption(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue()))
                throw new MissingParameterException("Missing required option '" + CliOptions.OPTIONS.BINARY_TMP_DIR.getValue() + "' with " + CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue());

            if (reUseBinaryFiles && peaklistFilenames.length > 0)
                System.out.println("WARNING: " + CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue() + " set, input files will be ignored");

            // make sure input files were set
            if (!reUseBinaryFiles && peaklistFilenames.length < 1)
                throw new MissingParameterException("No spectrum files passed. Please list the peak list files to process after the command.");

            /**
             * Advanced options
             */
            // MIN NUMBER COMPARISONS
            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_MIN_NUMBER_COMPARISONS.getValue())) {
                int minComparisons = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_MIN_NUMBER_COMPARISONS.getValue()));
                Defaults.setMinNumberComparisons(minComparisons);
            }

            // N HIGHEST PEAKS
            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_NUMBER_PREFILTERED_PEAKS.getValue())) {
                int nHighestPeaks = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_NUMBER_PREFILTERED_PEAKS.getValue()));
                CliSettings.setLoadingSpectrumFilter(new HighestNPeakFunction(nHighestPeaks));
            }

            /**
             * SPECIAL MODES
             */
            // cluster binary file
            if (commandLine.hasOption(CliOptions.OPTIONS.CLUSTER_BINARY_FILE.getValue())) {
                clusterBinaryFile(commandLine.getOptionValue(CliOptions.OPTIONS.CLUSTER_BINARY_FILE.getValue()), finalResultFile, thresholds, useFastMode);
                return;
            }

            // merge binary files
            if (commandLine.hasOption(CliOptions.OPTIONS.MERGE_BINARY_RESULTS.getValue())) {
                mergeBinaryFiles(commandLine.getArgs(), finalResultFile);
                return;
            }

            // convert cgf
            if (commandLine.hasOption(CliOptions.OPTIONS.CONVERT_CGF.getValue())) {
                if (commandLine.getArgs().length > 1)
                    throw new Exception("Can only convert a single file at the time.");

                convertClusters(new File(commandLine.getArgs()[0]), finalResultFile, endThreshold);
                return;
            }

            /**
             * ------ Learn the CDF if set --------
             */
            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_LEARN_CDF.getValue())) {
                String cdfOuputFilename = commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_LEARN_CDF.getValue());
                File cdfOutputFile = new File(cdfOuputFilename);

                if (cdfOutputFile.exists()) {
                    throw new Exception("CDF output file " + cdfOuputFilename + " already exists.");
                }

                CdfLearner cdfLearner = new CdfLearner();
                System.out.println("Learning CDF...");
                CdfResult cdfResult = cdfLearner.learnCumulativeDistribution(peaklistFilenames, nMajorPeakJobs);

                // write it to the file
                FileWriter writer = new FileWriter(cdfOutputFile);
                writer.write(cdfResult.toString());
                writer.close();

                System.out.println("CDF successfully written to " + cdfOuputFilename);
                return;
            }

            /**
             * ------ Load the CDF from file -------
             */
            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_LOAD_CDF_FILE.getValue())) {
                BufferedReader reader = new BufferedReader(
                        new FileReader(
                                commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_LOAD_CDF_FILE.getValue())));

                StringBuilder cdfString = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    cdfString.append(line);
                }
                reader.close();

                Defaults.setCumulativeDistributionFunction(CumulativeDistributionFunction.fromString(cdfString.toString()));
            }

            /**
             * ------- THE ACTUAL LOGIC STARTS HERE -----------
             */
            printSettings(finalResultFile, nMajorPeakJobs, startThreshold, endThreshold, rounds, keepBinaryFiles,
                    binaryTmpDirectory, peaklistFilenames, reUseBinaryFiles, useFastMode);

            List<BinaryClusterFileReference> binaryFiles = null;
            BinningSpectrumConverter binningSpectrumConverter = null;

            if (reUseBinaryFiles) {
                // get the list of files
                File[] existingBinaryFiles = binaryTmpDirectory.listFiles((FilenameFilter) FileFilterUtils.suffixFileFilter(".cls"));

                // if no binary files were found, simply create them
                if (existingBinaryFiles.length < 1) {
                    System.out.println("No binary files found. Re-creating binary files...");
                    reUseBinaryFiles = false;
                }
                else {
                    System.out.print("Scanning " + String.valueOf(existingBinaryFiles.length) + " binary files...");
                    // scan the binary files to get the basic metadata about each file
                    binaryFiles = BinaryFileScanner.scanBinaryFiles(existingBinaryFiles);

                    System.out.println("Found " + binaryFiles.size() + " existing binary files.");
                }
            }

            if (!reUseBinaryFiles) {
                System.out.print("Writing binary files...");
                long start = System.currentTimeMillis();

                binningSpectrumConverter = new BinningSpectrumConverter(binaryTmpDirectory, nMajorPeakJobs, useFastMode);
                binningSpectrumConverter.processPeaklistFiles(peaklistFilenames);
                binaryFiles = binningSpectrumConverter.getWrittenFiles();

                String message = String.format("Done. Found %d spectra", binningSpectrumConverter.getSpectrumReferences().size());
                printDone(start, message);
            }

            // create a temporary directory for the clustering results
            File clusteringResultDirectory = createTemporaryDirectory("clustering_results", binaryTmpDirectory);
            File tmpClusteringResultDirectory = createTemporaryDirectory("clustering_results", binaryTmpDirectory);

            // cluster the binary files and immediately convert the results
            BinaryFileClusterer binaryFileClusterer = new BinaryFileClusterer(nMajorPeakJobs, clusteringResultDirectory,
                    thresholds, useFastMode, tmpClusteringResultDirectory);

            System.out.println("Clustering " + binaryFiles.size() + " binary files...");
            long start = System.currentTimeMillis();

            binaryFileClusterer.clusterFiles(binaryFiles);

            printDone(start, "Completed clustering.");

            // delete the temporary directory
            if (!tmpClusteringResultDirectory.delete()) {
                System.out.println("Warning: Failed to delete " + tmpClusteringResultDirectory.getName());
            }

            // sort result files by min m/z
            List<BinaryClusterFileReference> clusteredFiles = binaryFileClusterer.getResultFiles();
            clusteredFiles = new ArrayList<BinaryClusterFileReference>(clusteredFiles);
            Collections.sort(clusteredFiles);

            // merge the results
            File mergedResultsDirectory = createTemporaryDirectory("merged_results", binaryTmpDirectory);
            File mergedResultsDirectoryTmp = createTemporaryDirectory("merged_results_tmp", binaryTmpDirectory);
            BinaryFileMergingClusterer mergingClusterer = new BinaryFileMergingClusterer(nMajorPeakJobs, mergedResultsDirectory,
                    thresholds, useFastMode, Defaults.getDefaultPrecursorIonTolerance(), DELETE_TEMPORARY_CLUSTERING_RESULTS,
                    mergedResultsDirectory);

            // create the combined output file as soon as a job is done
            File combinedResultFile = File.createTempFile("combined_clustering_results", ".cgf", binaryTmpDirectory);
            MergingCGFConverter mergingCGFConverter = new MergingCGFConverter(combinedResultFile, DELETE_TEMPORARY_CLUSTERING_RESULTS, !keepBinaryFiles, binaryTmpDirectory);
            mergingClusterer.addListener(mergingCGFConverter);

            // launch the merging job
            System.out.println("Merging " + clusteredFiles.size() + " binary files...");
            start = System.currentTimeMillis();
            mergingClusterer.clusterFiles(clusteredFiles);

            printDone(start, "Completed merging.");

            // delete the temporary directory after merging - this directory should be empty
            if (!mergedResultsDirectoryTmp.delete()) {
                System.out.println("Warning: Failed to delete " + mergedResultsDirectoryTmp.getName());
            }
            // delete the temporary directories if set
            if (DELETE_TEMPORARY_CLUSTERING_RESULTS) {
                if (!clusteringResultDirectory.delete())
                    System.out.println("Warning: Failed to delete " + clusteringResultDirectory);
            }

            if (!keepBinaryFiles) {
                if (!binaryTmpDirectory.delete())
                    System.out.println("Warning: Failed to delete " + binaryTmpDirectory);
            }

            // create the output file
            convertClusters(combinedResultFile, finalResultFile, endThreshold);

            if (!combinedResultFile.delete())
                System.out.println("Warning: Failed to delete " + combinedResultFile);

            System.out.println("Clustering completed. Results written to " + finalResultFile);
        } catch (MissingParameterException e) {
            System.out.println("Error: " + e.getMessage() + "\n\n");
            printUsage();

            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());

            System.exit(1);
        }
    }

    private static void mergeBinaryFiles(String[] binaryFilenames, File finalResultFile) throws Exception {
        File tmpResultFile = File.createTempFile("combined_results", ".cgf");

        // scan the binary files
        File[] binaryFiles = new File[binaryFilenames.length];
        for (int i = 0; i < binaryFilenames.length; i++) {
            binaryFiles[i] = new File(binaryFilenames[i]);
        }
        List<BinaryClusterFileReference> binaryFileRefs = BinaryFileScanner.scanBinaryFiles(binaryFiles);

        MergingCGFConverter mergingCGFConverter = new MergingCGFConverter(tmpResultFile, false, false, null); // do not delete any files

        System.out.print("Merging " + binaryFilenames.length + " binary files...");
        long start = System.currentTimeMillis();

        for (BinaryClusterFileReference binaryClusterFileReference : binaryFileRefs) {
            mergingCGFConverter.onNewResultFile(binaryClusterFileReference);
        }

        printDone(start);

        // copy the temporary file to the final destination
        start = System.currentTimeMillis();
        System.out.print("Copying result to " + finalResultFile + "...");

        FileUtils.copyFile(tmpResultFile, finalResultFile);

        printDone(start);
        tmpResultFile.delete();
    }

    /**
     * Clusters the passed binary file in a single thread and writes the result to "binaryFilename"
     * @param binaryFilename
     * @param finalResultFile
     * @param thresholds
     */
    private static void clusterBinaryFile(String binaryFilename, File finalResultFile, List<Float> thresholds, boolean fastMode) throws Exception {
        System.out.println("spectra-cluster API Version 1.0");
        System.out.println("Created by Rui Wang & Johannes Griss\n");

        System.out.println("Clustering single binary file: " + binaryFilename);
        System.out.println("Result file: " + finalResultFile);

        // write to a (local) temporary file
        File tmpResultFile = File.createTempFile("clustering_result", ".cls");

        long start = System.currentTimeMillis();
        System.out.print("Clustering file...");

        BinaryFileClusteringCallable binaryFileClusteringCallable = new BinaryFileClusteringCallable(
                tmpResultFile, new File(binaryFilename), thresholds, fastMode, tmpResultFile.getParentFile());
        binaryFileClusteringCallable.call();

        printDone(start);

        // copy the file
        System.out.println("Copying result file to " + finalResultFile);
        FileUtils.copyFile(tmpResultFile, finalResultFile);

        tmpResultFile.delete();
    }

    private static void printSettings(File finalResultFile, int nMajorPeakJobs, float startThreshold,
                                      float endThreshold, int rounds, boolean keepBinaryFiles, File binaryTmpDirectory,
                                      String[] peaklistFilenames, boolean reUseBinaryFiles, boolean fastMode) {
        System.out.println("spectra-cluster API Version 1.0");
        System.out.println("Created by Rui Wang & Johannes Griss\n");

        System.out.println("-- Settings --");
        System.out.println("Number of threads: " + String.valueOf(nMajorPeakJobs));
        System.out.println("Thresholds: " + String.valueOf(startThreshold) + " - " + String.valueOf(endThreshold) + " in " + rounds + " rounds");
        System.out.println("Keeping binary files: " + (keepBinaryFiles ? "true" : "false"));
        System.out.println("Binary file directory: " + binaryTmpDirectory);
        System.out.println("Result file: " + finalResultFile);
        System.out.println("Reuse binary files: " + (reUseBinaryFiles ? "true" : "false"));
        System.out.println("Input files: " + peaklistFilenames.length);
        System.out.println("Using fast mode: " + (fastMode ? "yes" : "no"));

        System.out.println("\nOther settings:");
        System.out.println("Precursor tolerance: " + Defaults.getDefaultPrecursorIonTolerance());
        System.out.println("Fragment ion tolerance: " + Defaults.getFragmentIonTolerance());

        // only show certain settings if they were changed
        if (Defaults.getMinNumberComparisons() != Defaults.DEFAULT_MIN_NUMBER_COMPARISONS)
            System.out.println("Minimum number of comparisons: " + Defaults.getMinNumberComparisons());

        System.out.println();
    }

    /**
     * Generates the actual thresholds to use based on the
     * start and end threshold and the number of iterations
     * to perform. The result is sorted from highest to
     * lowest threshold.
     *
     * @param startThreshold Starting threshold (highest threshold)
     * @param endThreshold Final threshold
     * @param rounds Number of rounds of clustering to perform
     * @return
     */
    private static List<Float> generateThreshold(float startThreshold, float endThreshold, int rounds) {
        List<Float> thresholds = new ArrayList<Float>(rounds);
        float stepSize = (startThreshold - endThreshold) / (rounds - 1);

        for (int i = 0; i < rounds; i++) {
            thresholds.add(startThreshold - (stepSize * i));
        }

        return thresholds;
    }

    private static void convertClusters(File combinedResultFile, File finalResultFile, float endThreshold) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(combinedResultFile);
        FileWriter writer = new FileWriter(finalResultFile);

        int nClusterWritten = 0;
        System.out.print("Converting results to .clustering...");
        long start = System.currentTimeMillis();

        DotClusterClusterAppender.INSTANCE.appendStart(writer, "GreedyClustering_" + String.valueOf(endThreshold));
        CGFSpectrumIterable iterable = new CGFSpectrumIterable(fileInputStream);

        for (ICluster cluster : iterable) {
            DotClusterClusterAppender.INSTANCE.appendCluster(writer, cluster);
            nClusterWritten++;
        }

        writer.close();

        System.out.println("Done. (Took " + String.format("%.2f", (float) (System.currentTimeMillis() - start) / 1000 / 60) + " min. " + nClusterWritten + " final clusters)");

        // copy the final CGF file as well
        FileUtils.copyFile(combinedResultFile, new File(finalResultFile.getAbsolutePath() + ".cgf"));
        System.out.println("Copied CGF result to " + finalResultFile.getAbsolutePath() + ".cgf");
    }

    private static Map<String, SpectrumReference> getSpectrumReferencesPerId(List<SpectrumReference> spectrumReferences) {
        Map<String, SpectrumReference> spectrumReferencePerId = new HashMap<String, SpectrumReference>();

        // save the spectrum references per id
       for (SpectrumReference spectrumReference : spectrumReferences) {
            if (spectrumReferencePerId.containsKey(spectrumReference.getSpectrumId()))
                continue;

            spectrumReferencePerId.put(spectrumReference.getSpectrumId(), spectrumReference);
        }

        return spectrumReferencePerId;
    }

    private static void printDone(long start) {
        printDone(start, "Done");
    }

    private static void printDone(long start, String message) {
        long duration = System.currentTimeMillis() - start;
        System.out.println(message + " (" + String.format("%.2f", (double) duration / 1000 / 60) + " min)");
    }

    private static File createTemporaryDirectory(String prefix) throws Exception {
        return createTemporaryDirectory(prefix, null);
    }

    private static File createTemporaryDirectory(String prefix, File tmpDirectory) throws Exception {
        File tmpFile;

        if (tmpDirectory != null && tmpDirectory.isDirectory()) {
            tmpFile = File.createTempFile(prefix, "", tmpDirectory);
        }
        else {
            tmpFile = File.createTempFile(prefix, "");
        }

        if (!tmpFile.delete())
            throw new Exception("Failed to delete temporary file");

        if (!tmpFile.mkdir())
            throw new Exception("Failed to create temporary directory");

        return tmpFile;
    }

    private static void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Spectra Cluster - Clusterer",
                "Clusters the spectra found in an MGF file and writes the results in a text-based file.\n",
                CliOptions.getOptions(), "\n\n", true);
    }

    @Override
    public void progress(int completedCalculations, int totalCalculations) {
        System.out.println("  Completed " + completedCalculations + " / " + totalCalculations);
    }
}
