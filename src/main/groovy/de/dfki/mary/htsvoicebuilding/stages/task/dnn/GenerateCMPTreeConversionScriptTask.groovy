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


/**
 *  Task which generates the CMP tree conversion script
 *
 */
public class GenerateCMPTreeConversionScriptTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateCMPTreeConversionScriptTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateCMPTreeConversionScriptWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        new File(project.tree_dir), // FIXME: externalize
                        new File("$project.list_dir/tiedlist_cmp"), // FIXME: externalize
                        script_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to generate the CMP tree conversion script
 *
 */
class GenerateCMPTreeConversionScriptWorker implements Runnable {

    private File list_file;

    private File tree_dir;

    private File tied_list_file;

    private File script_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateCMPTreeConversionScriptWorker(File list_file, File tree_dir,
                                                 File tied_list_file, File script_file, Object configuration) {
        this.list_file = list_file;
        this.tied_list_file = tied_list_file;
        this.script_file = script_file;
        this.tree_dir = tree_dir;

        // Utilities
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        def output = "TR 2\n"

        configuration.models.cmp.streams.each { stream ->
            output += "LT \"$tree_dir/${stream.kind}.${configuration.settings.training.nb_clustering-1}.inf\"\n"
        }

        output += "AU \"$list_file\"\n"
        output += "CO \"$tied_list_file\"\n"

        script_file.text = output
    }
}
