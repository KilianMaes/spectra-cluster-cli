package uk.ac.ebi.pride.spectracluster.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

/**
 * Created with IntelliJ IDEA.
 * User: jg
 * Date: 9/15/13
 * Time: 11:35 AM
 * To change this template use File | Settings | File Templates.
 */
public class CliOptions {
    public enum OPTIONS {
       OUTPUT_PATH("output_path"),
       MAJOR_PEAK_JOBS("major_peak_jobs"),
       START_THRESHOLD("threshold_start"),
       END_THRESHOLD("threshold_end"),
       ROUNDS("rounds"),
       MERGE_DUPLICATE("merge_duplicate"),
       BINARY_TMP_DIR("binary_directory"),
       KEEP_BINARY_FILE("keep_binary_files"),
       REUSE_BINARY_FILES("reuse_binary_files"),
       CLUSTER_BINARY_FILE("cluster_binary_file"),
       MERGE_BINARY_RESULTS("merge_binary_results"),
       CONVERT_CGF("convert_cgf"),
       FAST_MODE("fast_mode"),
       HELP("help");

        private String value;

        OPTIONS(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static final Options options = new Options();

    static {
        Option outputPath = OptionBuilder
                .hasArg()
                .withDescription("path to the outputfile. Outputfile must not exist.")
                .create(OPTIONS.OUTPUT_PATH.getValue());
        options.addOption(outputPath);

        Option startThreshold = OptionBuilder
                .hasArg()
                .withDescription("(highest) starting threshold")
                .create(OPTIONS.START_THRESHOLD.getValue());
        options.addOption(startThreshold);

        Option endThreshold = OptionBuilder
                .hasArg()
                .withDescription("(lowest) final clustering threshold")
                .create(OPTIONS.END_THRESHOLD.getValue());
        options.addOption(endThreshold);

        Option rounds = OptionBuilder
                .hasArg()
                .withDescription("number of clustering rounds to use.")
                .create(OPTIONS.ROUNDS.getValue());
        options.addOption(rounds);

        Option majorPeakJobs = OptionBuilder
                .hasArg()
                .withDescription("number of threads to use for major peak clustering.")
                .create(OPTIONS.MAJOR_PEAK_JOBS.getValue());
        options.addOption(majorPeakJobs);

        Option mergeDuplicate = OptionBuilder
                .withDescription("if this option is set duplicate clusters are merged based on the proportion of shared spectra.")
                .create(OPTIONS.MERGE_DUPLICATE.getValue());
        options.addOption(mergeDuplicate);

        Option binaryDirectory = OptionBuilder
                .hasArg()
                .withDescription("path to the directory to (temporarily) store the binary files. By default a temporary directory is being created")
                .create(OPTIONS.BINARY_TMP_DIR.getValue());
        options.addOption(binaryDirectory);

        Option keepBinary = OptionBuilder
                .withDescription("if this options is set, the binary files are not deleted after clustering.")
                .create(OPTIONS.KEEP_BINARY_FILE.getValue());
        options.addOption(keepBinary);

        Option reuseBinaryFiles = OptionBuilder
                .withDescription("if this option is set, the binary files found in the binary file directory will be used for clustering.")
                .create(OPTIONS.REUSE_BINARY_FILES.getValue());
        options.addOption(reuseBinaryFiles);

        Option clusterBinaryFile = OptionBuilder
                .withDescription("if this option is set, only the passed binary file will be clustered and the result written to the file specified in '-output_path' in the binary format")
                .hasArg()
                .create(OPTIONS.CLUSTER_BINARY_FILE.getValue());
        options.addOption(clusterBinaryFile);

        Option mergeBinaryResuls = OptionBuilder
                .withDescription("if this option is set, the passed binary results files are merged into a single .cgf file and written to '-output_path'")
                .create(OPTIONS.MERGE_BINARY_RESULTS.getValue());
        options.addOption(mergeBinaryResuls);

        Option convertCgf = OptionBuilder
                .withDescription("if this option is set the passed CGF file is converted into a .clustering file")
                .create(OPTIONS.CONVERT_CGF.getValue());
        options.addOption(convertCgf);

        Option fastMode = OptionBuilder
                .withDescription("if this option is set the 'fast mode' is enabled. In this mode, the radical peak filtering used for the comparison function is already applied during spectrum conversion. Thereby, the clustering and consensus spectrum quality is slightly decreased but speed increases 2-3 fold.")
                .create(OPTIONS.FAST_MODE.getValue());
        options.addOption(fastMode);

        Option help = new Option(
                OPTIONS.HELP.toString(),
                "print this message.");
        options.addOption(help);

    }

    public static Options getOptions() {
        return options;
    }
}
