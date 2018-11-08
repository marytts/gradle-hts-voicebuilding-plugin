package de.dfki.mary.htsvoicebuilding.stages.task.gv

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

// HTS Wrapper
import de.dfki.mary.htsvoicebuilding.HTSWrapper;


/**
 *  Task which generate the state force alignment
 *
 */
public class GenerateStateForceAlignmentTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of data file */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The list of label file */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The master label file */
    @InputFile
    final RegularFileProperty mlf_file = newInputFile()

    /** The CMP model file */
    @InputFile
    final RegularFileProperty model_cmp_file = newInputFile()

    /** The duration model file */
    @InputFile
    final RegularFileProperty model_dur_file = newInputFile()

    /** The directory which contains produced state aligned labels */
    @OutputDirectory
    final DirectoryProperty aligned_directory = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateStateForceAlignmentTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateStateForceAlignmentWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        scp_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        mlf_file.getAsFile().get(),
                        model_cmp_file.getAsFile().get(),
                        model_dur_file.getAsFile().get(),
                        aligned_directory.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}

/**
 *  Worker to generate the state force alignment
 *
 */
class GenerateStateForceAlignmentWorker implements Runnable {

    /** The list of data file */
    private File scp_file;

    /** The list of label file */
    private File list_file;

    /** The master label file */
    private File mlf_file;

    /** The CMP model file */
    private File model_cmp_file;

    /** The duration model file */
    private File model_dur_file;

    /** The directory which contains produced state aligned labels */
    private File alignment_dir;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;


    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateStateForceAlignmentWorker(File scp_file, File list_file, File mlf_file,
                                             File model_cmp_file, File model_dur_file,
                                             File alignment_dir, HTSWrapper hts_wrapper) {
        // Inputs
        this.scp_file = scp_file;
        this.list_file = list_file;
        this.mlf_file = mlf_file;
        this.model_cmp_file = model_cmp_file;
        this.model_dur_file = model_dur_file;

        // Outputs
        this.alignment_dir = alignment_dir;

        // Utilities
        this.hts_wrapper = hts_wrapper;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        hts_wrapper.HSMMAlign(scp_file.toString(),
                              list_file.toString(),
                              mlf_file.toString(),
                              model_cmp_file.toString(),
                              model_dur_file.toString(),
                              alignment_dir.toString(),
                              true)
    }
}
