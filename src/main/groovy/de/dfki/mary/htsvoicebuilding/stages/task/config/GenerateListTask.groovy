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
 *  Task to generate the file containing the list of labels
 *
 */
public class GenerateListTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** File containing list of basenames */
    @InputFile
    final RegularFileProperty list_basenames = newInputFile()

    /** Directory containing the labels */
    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** List file produced by the worker */
    @OutputFile
    final RegularFileProperty list_file = newOutputFile()


    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateListTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateListWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(list_basenames.getAsFile().get(),
                                  lab_dir.getAsFile().get(),
                                  list_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the file containing the list of labels
 *
 */
class GenerateListWorker implements Runnable {
    /** File containing list of basenames */
    private File list_basenames_file;

    /** Directory containing the labels */
    private File lab_dir;

    /** List file produced by the worker */
    private File list_file;

    /**
     *  The constructor of the worker
     *
     *  @param list_basenames_file file containing the basename list
     *  @param lab_dir the directory containing the labels
     *  @param list_file the List file generated
     */
    @Inject
    public GenerateListWorker(File list_basenames_file, File lab_dir, File list_file) {
        this.list_basenames_file = list_basenames_file;
        this.lab_dir = lab_dir;
        this.list_file = list_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Generate set of labels
        def model_set = new HashSet();
        for (String basename: list_basenames_file.readLines()) {
            (new File(lab_dir.toString(), basename + ".lab")).eachLine { line ->
                def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                if (line_arr.size() == 0) {
                    model_set.add(line)
                } else {
                    model_set.add(line_arr[0][3])
                }
            }
        }

        // Save it into the list
        list_file.text = model_set.join("\n")
    }
}
