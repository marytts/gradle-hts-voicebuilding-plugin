package de.dfki.mary.htsvoicebuilding.stages.task.gv

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

// MAth part
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

// IO
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Files;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class ExtractGVCoefficientsTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty cmp_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ExtractGVCoefficientsTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        for (String cur_file: scp_file.getAsFile().get().readLines()) {

            String basename = new File(cur_file).getName().split('\\.(?=[^\\.]+$)')[0]

            // List all input files
            ArrayList<File> input_files = new ArrayList<File>();
            for (def stream: project.configuration.user_configuration.models.cmp.streams) {
                input_files.add(new File(stream.coeffDir, basename + "." + stream.kind))
            }

            // Get lab file
            File lab_file = new File(lab_dir.getAsFile().get(), basename + ".lab")

            // Generate output filename
            File cmp_file = new File(cmp_dir.getAsFile().get(), basename + ".cmp");

            // Submit the execution
            workerExecutor.submit(ExtractGVCoefficientsWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(input_files, lab_file, cmp_file,
                                      project.configuration.user_configuration);
                    }
                });
        }
    }
}


/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class ExtractGVCoefficientsWorker implements Runnable {
    /** The input SP file */
    private final ArrayList<File> input_files;

    /** The label file */
    private final File lab_file;

    /** The generated CMP file */
    private final File output_cmp_file;

    /** The configuration object */
    private final Object configuration;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param output_cmp_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public ExtractGVCoefficientsWorker(ArrayList<File> input_files, File lab_file, File output_cmp_file, Object configuration) {
	this.input_files = input_files;
        this.lab_file = lab_file;
	this.output_cmp_file = output_cmp_file;
        this.configuration = configuration
    }


    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {


        // Compute CMP size
        int cmp_size = 0
        configuration.models.cmp.streams.each { stream ->
            cmp_size += stream.order + 1
        }
        double[] variance = new double[cmp_size]

        // Compute Variance per stream
        int cmp_index = 0;
        int stream_index = 0

        configuration.models.cmp.streams.each { stream ->

            // Load file
            double[][] input_data = loadFile(input_files.get(stream_index), stream.order + 1)

            // Clean indexes
            int end = -1;
            boolean is_silence = false;
            def indexes = []
            def labels = lab_file.readLines()
            def label = ""

            // Generate the list of index we want to play with !
            for (int i=0; i<input_data.length; i++) {

                if ((configuration.gv.nosil) &&
                    (configuration.gv.silences.size() > 0)) {


                    if (i >= end) { // Assumed to start at 0 and be consecutive

                        def line = labels.remove(0)
                        def cur_lab_arr = line.split()
                        end = Integer.parseInt(cur_lab_arr[1]) / (1.0e4 * configuration.signal.frameshift.intValue())
                        def match_sil = configuration.gv.silences.findResults {
                            it.toString().equals(cur_lab_arr[2]) ? it.toString() : null
                        }
                        label = cur_lab_arr[2]
                        is_silence = match_sil.size() > 0
                    } else if (labels.size() == 0) { // Any additional frames are defacto considered as silence !
                        end = input_data.length
                        is_silence = true
                    }

                    // Ignore the silence
                    if (is_silence) {
                        continue;
                    }
                }

                // Add if
                if ((!stream.is_msd) || (input_data[i][0] != -1e+10)) { // FIXME: check value
                    indexes.add(i);
                }
            }

            if (indexes.size() == 0)
                throw new IllegalArgumentException("indexes is empty!");

            // Generate filtered array
            double[][] filtered_data = new double[indexes.size()][stream.order+1]
            for (int i=0; i<indexes.size(); i++)
                filtered_data[i] = input_data[indexes[i]];

            // Now compute variance !
            RealMatrix mx = MatrixUtils.createRealMatrix(filtered_data);
            RealMatrix cov = new Covariance(mx).getCovarianceMatrix();

            // Add variance to variance array
            for (int i=0; i<stream.order+1; i++)
                variance[cmp_index+i] = cov.getEntry(i, i)

            cmp_index += stream.order + 1
        }

        // Compute HTK header
        byte[] header = computeHTKHeader(variance, configuration.signal.frameshift, (short) 9);

        // Generate data byte array
        ByteBuffer data_bf = ByteBuffer.allocate(Float.BYTES * variance.length);
        data_bf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i=0; i<variance.length; i++) {
            data_bf.putFloat((float) variance[i]);
        }
        byte[] data = data_bf.array();

        // Generate full byte array
        byte[] output_data = new byte[header.length + data.length];
        System.arraycopy(header, 0, output_data, 0, header.length);
        System.arraycopy(data, 0, output_data, header.length, data.length);

        // Save the cmp file
        Files.write(output_cmp_file.toPath(), output_data);
    }

    /**
     *  Add an HTK header to the input cmp file
     *
     *   @param input_file_name the input cmp file path
     *   @param output_file_name the output cmp file with header path
     *   @param frameshift the used frameshift in HTK format
     *   @param framesize the number of coefficients for one frame
     *   @param HTK_feature_type the HTK feature type information
     */
    public static byte[] computeHTKHeader(double[] O, int frameshift, short HTK_feature_type)
    throws IOException
    {

        // Prepare buffer
        ByteBuffer buffer = ByteBuffer.allocate((Integer.BYTES + Short.BYTES) * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Get needed information
        short framesize = (short) (Float.BYTES * O.length);

        // Generate header
        buffer.putInt(1);
        buffer.putInt(frameshift);

        buffer.putShort(framesize);
        buffer.putShort(HTK_feature_type);

        // Return generated header
        return buffer.array();
    }

    private double[][] loadFile(File input_file, int dim) throws FileNotFoundException, IOException {
        Path p_input = input_file.toPath();
        byte[] data_bytes = Files.readAllBytes(p_input);
        ByteBuffer buffer = ByteBuffer.wrap(data_bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Compute nb frames
        int T = data_bytes.length / (dim * Float.BYTES);

        // Generate vector C
        double[][] input_data = new double[T][dim];
        for (int i=0; i<T; i++) {
            for (int j=0; j<dim; j++)  {
                input_data[i][j] = buffer.getFloat();
                if (Double.isNaN(input_data[i][j])) {
                    throw new IOException(input_file.toString() + " contains nan values! ");
                }
            }
        }

        return input_data;
    }
}
