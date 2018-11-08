package de.dfki.mary.htsvoicebuilding.stages.task.context

// For copying
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*


/**
 *  Task which prepares the CMP clustering part by copying the full context to an initial
 *  clustered. This task type also provide an "output flag" to be sure that everything is following
 *  the desired order
 *
 */
public class PrepareCMPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** the  fullcontext model file to copy */
    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile()

    /** The initial clustered model file */
    @Internal
    File clustered_model_file // FIXME: why file?

    /** The output flag file to keep track of the process */
    @OutputFile
    final RegularFileProperty output_flag = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public PrepareCMPTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(PrepareCMPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        fullcontext_model_file.getAsFile().get(),
                        clustered_model_file,
                        output_flag.getAsFile().get()
                    );
                }
            });
    }
}

/**
 *  Worker to prepares the CMP clustering part by copying the full context to an initial clustered. This task type also provide an "output flag" to be sure that everythins if following
 *
 */
class PrepareCMPWorker implements Runnable {

    /** the  fullcontext model file to copy */
    private File fullcontext_model_file

    /** The initial clustered model file */
    private File clustered_model_file

    /** The output flag file to keep track of the process */
    private File output_flag

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public PrepareCMPWorker(File fullcontext_model_file, File clustered_model_file, File output_flag) {
        this.fullcontext_model_file = fullcontext_model_file
        this.clustered_model_file = clustered_model_file
        this.output_flag = output_flag
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Copy the model file
        Files.copy(fullcontext_model_file.toPath(), clustered_model_file.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        // Indicate that the task is done
        output_flag.text = "done"
    }
}
