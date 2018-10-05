package de.dfki.mary.htsvoicebuilding.stages.task.config

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


/**
 *  Task to generate the SCP file listing label files
 *
 */
public class GenerateLabSCPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** File containing list of basenames */
    @InputFile
    final RegularFileProperty list_basenames = newInputFile()

    /** Directory containing the labels */
    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** SCP file produced by the worker */
    @OutputFile
    final RegularFileProperty scp_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateLabSCPTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateLabSCPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(list_basenames.getAsFile().get(),
                                  lab_dir.getAsFile().get(),
                                  scp_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the SCP file listing label files.
 *
 */
class GenerateLabSCPWorker implements Runnable {
    /** File containing list of basenames */
    private File list_basenames_file;

    /** Directory containing the labels */
    private File lab_dir;

    /** SCP file produced by the worker */
    private File scp_file;

    /**
     *  The constructor of the worker
     *
     *  @param list_basenames_file file containing the basename list
     *  @param lab_dir the directory containing the labels
     *  @param scp_file the SCP file generated
     */
    @Inject
    public GenerateLabSCPWorker(File list_basenames_file, File lab_dir, File scp_file) {
        this.list_basenames_file = list_basenames_file;
        this.lab_dir = lab_dir;
        this.scp_file = scp_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def output = ""
        for (String basename: list_basenames.readLines()) {
            output += "$lab_dir/${basename}.lab\n"
        }

        scp_file.text = output
    }
}
