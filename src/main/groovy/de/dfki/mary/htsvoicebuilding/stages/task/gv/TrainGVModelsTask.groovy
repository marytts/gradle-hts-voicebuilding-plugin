package de.dfki.mary.htsvoicebuilding.stages.task.gv

//
import java.util.ArrayList;

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
 *  Task which trains the global variance models
 *
 */
public class TrainGVModelsTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** Options given to HERest */
    @Internal
    ArrayList<String> options = [];

    /** The SCP file containing the list of training files */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The list of labels to manipulate */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** Master label file */
    @InputFile
    final RegularFileProperty mlf_file = newInputFile()

    /** The initialised model file */
    @InputFile
    final RegularFileProperty init_model_file = newInputFile()

    /** The statistics file */
    @OutputFile
    final RegularFileProperty stats_file = newOutputFile()

    /** The produced trained model file */
    @OutputFile
    final RegularFileProperty trained_model_file = newOutputFile()

    /** The produced stats file */

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public TrainGVModelsTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(TrainGVModelsWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {

                    // Adapt options
                    options += ["-s", stats_file.getAsFile().get().toString()]

                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        scp_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        mlf_file.getAsFile().get(),
                        init_model_file.getAsFile().get(),
                        trained_model_file.getAsFile().get(),
                        options,
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}

/**
 *  Worker to trains the global variance models
 *
 */
class TrainGVModelsWorker implements Runnable {

    /** The SCP file containing the list of training files */
    private File scp_file;

    /** The list of labels to manipulate */
    private File list_file;

    /** Master label file */
    private File mlf_file;

    /** The initialised model file */
    private File init_model_file;

    /** The produced trained model file */
    private File trained_model_file;

    /** Options given to HERest */
    private ArrayList<String> options;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;


    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public TrainGVModelsWorker(File scp_file, File list_file, File mlf_file,
                               File init_model_file, File trained_model_file,
                               ArrayList<String> options, HTSWrapper hts_wrapper) {
        // Inputs
        this.scp_file = scp_file;
        this.list_file = list_file;
        this.mlf_file = mlf_file;
        this.init_model_file = init_model_file;

        // Outputs
        this.trained_model_file = trained_model_file;

        // Utilities
        this.hts_wrapper = hts_wrapper;
        this.options = options;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        hts_wrapper.HERestGV(scp_file.toString(),
                             list_file.toString(),
                             mlf_file.toString(),
                             init_model_file.toString(),
                             trained_model_file.getParent(),
                             options)
    }
}
