package de.dfki.mary.htsvoicebuilding.stages.task.dnn

// Template import
import groovy.text.*

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
 *  Task which generates the configuration fule needed for the DNN training
 *
 */
public class GenerateDNNSCPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of files to manipulate */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The directory of observation data */
    @InputDirectory
    final DirectoryProperty ffo_dir = newInputDirectory()

    /** The directory of input data */
    @InputDirectory
    final DirectoryProperty ffi_dir = newInputDirectory()

    /** The produced SCP file */
    @OutputFile
    final RegularFileProperty scp_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateDNNSCPTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateDNNSCPTaskWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        ffi_dir.getAsFile().get(),
                        ffo_dir.getAsFile().get(),
                        scp_file.getAsFile().get()
                    );
                }
            });
    }
}

/**
 *  Worker to generate the configuration fule needed for the DNN training
 *
 */
class GenerateDNNSCPTaskWorker implements Runnable {

    /** The list of files to manipulate */
    private File list_file;

    /** The directory of observation data */
    private File ffo_dir;

    /** The directory of input data */
    private File ffi_dir;

    /** The produced SCP file */
    private File scp_file;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateDNNSCPTaskWorker(File list_file, File ffi_dir, File ffo_dir, File scp_file) {
        this.list_file = list_file;
        this.ffo_dir = ffo_dir;
        this.ffi_dir = ffi_dir;
        this.scp_file = scp_file;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def output = ""
        for (String basename: list_file.readLines()) {
            output += "${ffi_dir}/${basename}.ffi ${ffo_dir}/${basename}.ffo\n"
        }

        scp_file.text = output
    }
}
