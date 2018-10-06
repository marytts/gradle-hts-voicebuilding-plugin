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

// HTS Wrapper
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Task which computes the average GV model
 *
 */
public class GenerateGVAverageTask extends DefaultTask {

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The data list file */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The input prototype file */
    @InputFile
    final RegularFileProperty proto_file = newInputFile()

    /** The produced average file */
    @OutputFile
    final RegularFileProperty average_file = newOutputFile()

    /** The produced vfloor file */
    @OutputFile
    final RegularFileProperty vfloor_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateGVAverageTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {
        // Submit the execution
        workerExecutor.submit(GenerateGVAverageWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        scp_file.getAsFile().get(),
                        proto_file.getAsFile().get(),
                        average_file.getAsFile().get(),
                        vfloor_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}

/**
 *  Worker to compute the average GV model
 *
 */
class GenerateGVAverageWorker implements Runnable {

    /** The list of data file */
    private File scp_file;

    /** The prototype file */
    private File proto_file;

    /** The produced average file */
    private File average_file;

    /** The produced vfloor file */
    private File vfloor_file;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;


    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateGVAverageWorker(File scp_file, File proto_file,
                                   File average_file, File vfloor_file,
                                   HTSWrapper hts_wrapper) {
        // Inputs
        this.scp_file = scp_file;
        this.proto_file = proto_file;

        // Outputs
        this.average_file = average_file;
        this.vfloor_file = vfloor_file;

        // Utilities
        this.hts_wrapper = hts_wrapper;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        hts_wrapper.HCompV(scp_file.toString(),
                           proto_file.toString(),
                           average_file.toString(),
                           vfloor_file.getParent())

    }
}
