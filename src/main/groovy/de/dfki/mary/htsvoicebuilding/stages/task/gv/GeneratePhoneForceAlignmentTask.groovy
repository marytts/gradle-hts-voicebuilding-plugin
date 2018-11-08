package de.dfki.mary.htsvoicebuilding.stages.task.gv

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
 *  Task which generates the phone alignment based on the state alignment
 *
 */
public class GeneratePhoneForceAlignmentTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The state aligned files */
    @InputDirectory
    final DirectoryProperty state_aligned_directory = newInputDirectory()

    /** The produced phone aligned files */
    @OutputDirectory
    final DirectoryProperty phone_aligned_directory = newOutputDirectory()


    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GeneratePhoneForceAlignmentTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        def id_last_state = project.configuration.user_configuration.models.global.nb_emitting_states + 1

        for (File state_file: state_aligned_directory.get().getAsFileTree()) {
            def phone_file = new File(state_file.toString().replaceAll("/state/", "/phone/"))

            // Submit the execution
            workerExecutor.submit(GeneratePhoneForceAlignmentWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(state_file, phone_file, id_last_state);
                    }
                });

        }
    }
}


/**
 *  Worker to generate the phone alignment based on the state alignment
 *
 */
class GeneratePhoneForceAlignmentWorker implements Runnable {
    /** The index of the last state */
    private final int id_last_state;

    /** The input stated aligned file */
    private final File state_aligned_file;

    /** The produced phone aligned file */
    private final File phone_aligned_file;

    /**
     *  The contructor which initialize the worker
     *
     */
    @Inject
    public GeneratePhoneForceAlignmentWorker(File state_aligned_file, File phone_aligned_file, int id_last_state) {
        this.id_last_state = id_last_state;
        this.state_aligned_file = state_aligned_file;
        this.phone_aligned_file = phone_aligned_file;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        def start = 0
        def phone_content = ""
        state_aligned_file.eachLine { line ->
            def val = line.split()
            def m = val[2] =~ /[^-]*-([^+]*)[+].*\[([0-9]*)\]$/
            def label = m[0][1]
            def state = Integer.parseInt(m[0][2])

            if (state == 2) {
                start = val[0]
            } else if (state == id_last_state) {
                phone_content += "$start ${val[1]} $label\n"
            }
        }

        phone_aligned_file.text = phone_content
    }
}
