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

// HTS wrapper
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Task which Description
 *
 */
public class TrainDNNTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The DNN training configuration file */
    @InputFile
    final RegularFileProperty configuration_file = newInputFile()

    /** The list of data file */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The global variance file for DNN training */
    @InputFile
    final RegularFileProperty global_var_file = newInputFile()

    /** The produced model directory */
    @OutputDirectory
    final DirectoryProperty model_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public TrainDNNTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {

        def train_script_file = new File("$project.utils_dir/DNNTraining.py");

        // Submit the execution
        workerExecutor.submit(TrainDNNWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        train_script_file,
                        configuration_file.getAsFile().get(),
                        scp_file.getAsFile().get(),
                        global_var_file.getAsFile().get(),
                        model_dir.getAsFile().get()
                    );
                }
            });
    }
}

/**
 *  Worker to Description
 *
 */
class TrainDNNWorker implements Runnable {
    /** The train python script file */
    private File train_script_file;

    /** The DNN training configuration file */
    private File configuration_file;

    /** The list of data file */
    private File scp_file;

    /** The global variance file for DNN training */
    private File global_var_file;

    /** The produced model directory */
    private File model_dir;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public TrainDNNWorker(File train_script_file, File configuration_file, File scp_file,
                          File global_var_file, File model_dir) {
        this.train_script_file = train_script_file;
        this.configuration_file = configuration_file;
        this.scp_file = scp_file;
        this.global_var_file = global_var_file;
        this.model_dir = model_dir;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        model_dir.mkdirs()
        def command = sprintf("python -u -B %s -C %s -S %s -H %s -z %s",
                              train_script_file,
                              configuration_file.toString(),
                              scp_file.toString(),
                              model_dir.toString(),
                              global_var_file.toString())
        HTSWrapper.executeOnShell(command.toString())
    }
}
