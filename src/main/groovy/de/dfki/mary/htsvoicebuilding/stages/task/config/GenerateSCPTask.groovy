package de.dfki.mary.htsvoicebuilding.stages.task.config

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


/**
 *  Task to generate the SCP file listing data files
 *
 */
public class GenerateSCPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** Directory containing the data */
    @InputDirectory
    final DirectoryProperty data_directory = newInputDirectory()

    /** SCP file produced by the worker */
    @OutputFile
    final RegularFileProperty scp_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateSCPTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateSCPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(data_directory.get().getAsFileTree().getFiles(),
                                  scp_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the SCP file listing data files.
 *
 */
class GenerateSCPWorker implements Runnable {
    /** Directory containing the data */
    private Set<File> data_files;

    /** SCP file produced by the worker */
    private File scp_file;

    /**
     *  The constructor of the worker
     *
     *  @param list_basenames_file file containing the basename list
     *  @param data_dir the directory containing the data
     *  @param scp_file the SCP file generated
     */
    @Inject
    public GenerateSCPWorker(Set<File> data_files, File scp_file) {
        this.data_files = data_files;
        this.scp_file = scp_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def output = ""

        for (File data_file: data_files) {
            output += "${data_file}\n"
        }

        scp_file.text = output
    }
}
