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
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

// IO
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateGVLabFilesTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputDirectory
    final DirectoryProperty full_lab_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty gv_lab_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateGVLabFilesTask(WorkerExecutor workerExecutor) {
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

            // Get lab file
            File full_lab_file = new File(full_lab_dir.getAsFile().get(), basename + ".lab")

            // Get lab file
            File gv_lab_file = new File(gv_lab_dir.getAsFile().get(), basename + ".lab")

            // Submit the execution
            workerExecutor.submit(GenerateGVLabFilesWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(full_lab_file, gv_lab_file);
                    }
                });
        }
    }
}


/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class GenerateGVLabFilesWorker implements Runnable {
    /** The input SP file */
    private final File full_lab_file;

    /** The generated CMP file */
    private final File gv_lab_file;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param output_cmp_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public GenerateGVLabFilesWorker(File full_lab_file, File gv_lab_file) {
        this.full_lab_file = full_lab_file;
        this.gv_lab_file = gv_lab_file;
    }


    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {
        def line
        full_lab_file.withReader { line = it.readLine() }
        def line_arr = line =~ /^[ \t]*([0-9]+)[\t ]+([0-9]+)[ \t]+(.+)/
        gv_lab_file.text = line_arr[0][3]+"\n"
    }
}
