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
 *  Task to get the global variance for DNN training
 *
 */
public class ComputeVarTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The observation data directory */
    @InputDirectory
    final DirectoryProperty ffo_dir = newInputDirectory()

    /** The produced variance file */
    @OutputFile
    final RegularFileProperty global_var_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ComputeVarTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(ComputeVarWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        ffo_dir.getAsFile().get(),
                        global_var_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to compute the variance needed for the DNN training
 *
 */
class ComputeVarWorker implements Runnable {

    /** The directory containing the observation files */
    private File ffo_dir;

    /** The produced global variance file */
    private File global_var_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ComputeVarWorker(File ffo_dir, File global_var_file, Object configuration) {

        this.ffo_dir = ffo_dir;
        this.global_var_file = global_var_file;

        // Utilities
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Get dimension
        def ffodim = 0
        for (def stream: configuration.models.ffo.streams) {
            ffodim += (stream.order + 1) * stream.winfiles.size()
        }

        // Compute global variance
        def command_global_var = "cat ${ffo_dir}/*.ffo | vstat -l $ffodim -d -o 2 > ${global_var_file}"
        HTSWrapper.executeOnShell(command_global_var)

        // Extract global variance per stream (FIXME: output file !)
        def start = 0
        for (def stream: configuration.models.ffo.streams) {
            def dim = (stream.order + 1) * stream.winfiles.size()
            if (stream.stats)  {
                def command_stream_var = "bcut +f -s $start -e ${start+dim-1} -l 1 ${global_var_file} > ${global_var_file.getParent()}/${stream.kind}.var"
                HTSWrapper.executeOnShell(command_stream_var)
            }
            start += dim
        }
    }
}
