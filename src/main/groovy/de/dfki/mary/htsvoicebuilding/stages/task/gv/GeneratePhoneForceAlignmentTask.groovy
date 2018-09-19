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
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GeneratePhoneForceAlignmentTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    @InputDirectory
    final DirectoryProperty state_alignment_dir = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty phone_alignment_dir = newOutputDirectory()


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

        for (String cur_file: scp_file.getAsFile().get().readLines()) {
            String basename = new File(cur_file).getName().split('\\.(?=[^\\.]+$)')[0]
            def state_file = new File(state_alignment_dir.getAsFile().get().toString(), "${basename}.lab")
            def phone_file = new File(phone_alignment_dir.getAsFile().get().toString(), "${basename}.lab")

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
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class GeneratePhoneForceAlignmentWorker implements Runnable {
    private final int id_last_state;

    /** The input SP file */
    private final File state_aligned_file;

    /** The generated CMP file */
    private final File phone_aligned_file;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
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
