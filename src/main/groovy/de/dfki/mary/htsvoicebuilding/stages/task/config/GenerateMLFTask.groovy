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
 *  Task to generate the Master Label File (MLF)
 *
 */
public class GenerateMLFTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The label directory */
    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** The produced MLF file */
    @OutputFile
    final RegularFileProperty mlf_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateMLFTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateMLFWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(lab_dir.getAsFile().get(),
                                  mlf_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the Master Label File (MLF)
 *
 */
class GenerateMLFWorker implements Runnable {
    /** Directory containing the labels */
    private File lab_dir;

    /** MLF file produced by the worker */
    private File mlf_file;

    /**
     *  The constructor of the worker
     *
     *  @param lab_dir the directory containing the labels
     *  @param mlf_file the MLF file generated
     */
    @Inject
    public GenerateMLFWorker(File lab_dir, File mlf_file) {
        this.lab_dir = lab_dir;
        this.mlf_file = mlf_file;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        mlf_file.text = "#!MLF!#\n" + '"*/*.lab" -> "' + lab_dir +'"'
    }
}
