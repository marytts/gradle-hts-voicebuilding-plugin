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


/**
 *  Task which Description
 *
 */
public class GenerateGVFullContextTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of label file */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The average model file */
    @InputFile
    final RegularFileProperty average_file = newInputFile()

    /** The vfloor file */
    @InputFile
    final RegularFileProperty vfloor_file = newInputFile()

    /** The produced model file */
    @OutputFile
    final RegularFileProperty model_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateGVFullContextTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateGVFullContextWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        average_file.getAsFile().get(),
                        vfloor_file.getAsFile().get(),
                        model_file.getAsFile().get()
                    );
                }
            });
    }
}

/**
 *  Worker to Description
 *
 */
class GenerateGVFullContextWorker implements Runnable {

    /** The average file */
    private File average_file;

    /** The vfloor file */
    private File vfloor_file;

    /** The list file */
    private File list_file;

    /** The produced modelf file */
    private File model_file;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateGVFullContextWorker(File list_file, File average_file,
                                       File vfloor_file, File model_file) {
        // Inputs
        this.average_file = average_file;
        this.vfloor_file = vfloor_file;
        this.list_file = list_file;

        // Outputs
        this.model_file = model_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        // Get average informations into head and tail variables
        def found = false
        def head = ""
        def tail = ""
        average_file.eachLine { line ->
            if (line.indexOf("~h") >= 0) {
                found = true
            } else if (found) {
                tail += line + "\n"
            } else {
                head += line + "\n"
            }
        }

        // Adding vFloor to head
        vfloor_file.eachLine { line ->
            head += line + "\n"
        }

        // Generate full context average model
        model_file.write(head)
        list_file.eachLine { line ->
            model_file.append("~h \"$line\"\n")
            model_file.append(tail)
        }
    }
}
