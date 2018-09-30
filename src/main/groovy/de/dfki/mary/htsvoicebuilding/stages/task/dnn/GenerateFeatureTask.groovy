package de.dfki.mary.htsvoicebuilding.stages.task.dnn

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

import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateFeatureTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    int local_cur_clus_it;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    @InputFile
    final RegularFileProperty qconf_file = newInputFile();

    @InputDirectory
    final DirectoryProperty aligned_lab_dir = newInputDirectory();

    @OutputDirectory
    final DirectoryProperty ffi_dir = newOutputDirectory();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateFeatureTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void convert() {

        for (String cur_file: list_file.getAsFile().get().readLines()) {

            // Submit the execution for the cmpation
            workerExecutor.submit(GenerateFeatureWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            new File(project.utils_dir, "makefeature.pl"),
                            new File(aligned_lab_dir.getAsFile().get().toString(), "${cur_file}.lab"),
                            new File(ffi_dir.getAsFile().get().toString(), "${cur_file}.ffi"),
                            qconf_file.getAsFile().get(),
                            (float) project.configuration.user_configuration.signal.frameshift
                        );
                    }
                });
        }
    }
}



/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class GenerateFeatureWorker implements Runnable {

    private float frameshift;
    private File mkf_script_file;
    private File aligned_lab_file;
    private File qconf_file;
    private File ffi_file;


    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public GenerateFeatureWorker(File mkf_script_file, File aligned_lab_file, File ffi_file, File qconf_file, float frameshift) {
        this.mkf_script_file = mkf_script_file;
        this.aligned_lab_file = aligned_lab_file;
        this.ffi_file = ffi_file;
        this.qconf_file = qconf_file
        this.frameshift = frameshift
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {
        def command = sprintf("perl %s %s %d %s | x2x +af > %s",
                              mkf_script_file.toString(),
                              qconf_file.toString(),
                              (1E+4 * frameshift).intValue(),
                              aligned_lab_file.toString(),
                              ffi_file.toString())
        HTSWrapper.executeOnShell(command)

    }
}
