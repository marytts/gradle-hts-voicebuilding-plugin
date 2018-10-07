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

// HTS Wrapper
import de.dfki.mary.htsvoicebuilding.HTSWrapper;


/**
 *  Task which generates the aligned parameters to get the duration for the DNN training
 *
 */
public class GenerateAlignedParametersTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The configuration needed by HMGenS */
    @InputFile
    final RegularFileProperty configuration_file = newOutputFile()

    /** The list of files to generate */
    @InputFile
    final RegularFileProperty scp_file = newOutputFile()

    /** The cmp list of tied models */
    @InputFile
    final RegularFileProperty cmp_tiedlist_file = newOutputFile()

    /** The duration list of tied models */
    @InputFile
    final RegularFileProperty dur_tiedlist_file = newOutputFile()

    /** The CMP model file */
    @InputFile
    final RegularFileProperty cmp_model_file = newOutputFile()

    /** The duration model file */
    @InputFile
    final RegularFileProperty dur_model_file = newOutputFile()

    /** The produced parameters directory */
    @OutputDirectory
    final DirectoryProperty parameters_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateAlignedParametersTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateAlignedParametersWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        configuration_file.getAsFile().get(),
                        scp_file.getAsFile().get(),
                        cmp_tiedlist_file.getAsFile().get(),
                        dur_tiedlist_file.getAsFile().get(),
                        cmp_model_file.getAsFile().get(),
                        dur_model_file.getAsFile().get(),
                        output_dir.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}

/**
 *  Worker to generate the aligned parameters to get the duration for the DNN training
 *
 */
class GenerateAlignedParametersWorker implements Runnable {

    /** The configuration needed by HMGenS */
    private File configuration_file;

    /** The list of files to generate */
    private File scp_file;

    /** The cmp list of tied models */
    private File cmp_tiedlist_file;

    /** The duration list of tied models */
    private File dur_tiedlist_file;

    /** The CMP model file */
    private File cmp_model_file;

    /** The duration model file */
    private File dur_model_file;

    /** The produced parameters directory */
    private File parameters_dir;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateAlignedParametersWorker(HTSWrapper hts_wrapper) {

        this.configuration_file = configuration_file;
        this.scp_file = scp_file;
        this.cmp_tiedlist_file = cmp_tiedlist_file;
        this.dur_tiedlist_file = dur_tiedlist_file;
        this.cmp_model_file = cmp_model_file;
        this.dur_model_file = dur_model_file;
        this.output_dir = output_dir;

        // Utilities
        this.hts_wrapper = hts_wrapper;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        hts_wrapper.HMGenS(
            configuration_file.toString(),
            scp_file.toString(),
            cmp_tiedlist_file.toString(),
            dur_tiedlist_file.toString(),
            cmp_model_file.toString(),
            dur_model_file.toString(),
            0,
            output_dir.toString())
    }
}
