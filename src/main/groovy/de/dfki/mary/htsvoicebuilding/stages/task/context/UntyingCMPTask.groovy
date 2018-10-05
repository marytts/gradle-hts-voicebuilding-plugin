package de.dfki.mary.htsvoicebuilding.stages.task.context

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
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class UntyingCMPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile()


    /** The input clustered model file */
    @InputFile
    final RegularFileProperty input_model_file = newInputFile()

    /** The produced untied model file */
    @OutputFile
    final RegularFileProperty output_model_file = newOutputFile()

    /** The produced untying script file */
    @OutputFile
    final RegularFileProperty untying_script_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public UntyingCMPTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(UntyingCMPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        input_model_file.getAsFile().get(),
                        untying_script_file.getAsFile().get(),
                        output_model_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper,
                        project.configuration.user_configuration
                    );
                }
            });
    }
}



/**
 *  Worker to join isolated clustered models to a common model
 *
 */
class UntyingCMPWorker implements Runnable {
    /** The file containing the list of monophones */
    private File list_file;

    /** Trained clustered files */
    private File clustered_file;

    /** Produced script file */
    private File script_file;

    /** Produced untied model file */
    private File untied_file;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public UntyingCMPWorker(File list_file, File clustered_file,
                            File script_file, File untied_file,
                            HTSWrapper hts_wrapper, Object configuration) {

        // Input
        this.list_file = list_file;
        this.clustered_file = clustered_file;

        // Output
        this.script_file = script_file;
        this.untied_file = untied_file;

        // Utilies
        this.hts_wrapper = hts_wrapper;
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        //  1. Generate hhed script file
        def cur_stream = 1
        def script_content = "// untie parameter sharing structure\n"

        configuration.models.cmp.streams.each { stream ->

            def end_stream = cur_stream
            if (stream.is_msd) {
                end_stream += stream.winfiles.size() - 1
            }

            if (configuration.models.cmp.streams.size() > 1) {
                for (i in 2..configuration.models.global.nb_emitting_states+1) {
                    script_content += "UT {*.state[$i].stream[$cur_stream-$end_stream]}\n"
                }
            }  else {
                for (i in 2..configuration.models.global.nb_emitting_states+1) {
                    script_content += "UT {*.state[$i]\n}"
                }

            }
            cur_stream = end_stream + 1

        }
        script_file.text = script_content

        //  2. untying
        hts_wrapper.HHEdOnMMF(script_file.toString(),
                              list_file.toString(),
                              clustered_file.toString(),
                              untied_file.toString(),
                              [])
    }
}
