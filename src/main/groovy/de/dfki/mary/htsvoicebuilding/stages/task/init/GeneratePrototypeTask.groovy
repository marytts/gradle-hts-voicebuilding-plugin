package de.dfki.mary.htsvoicebuilding.stages.task.init

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
 *  Task to generate the prototype file
 *
 */
public class GeneratePrototypeTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The prototype of files to manipulate */
    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty prototype_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GeneratePrototypeTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GeneratePrototypeWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(template_file.getAsFile().get(),
                                  prototype_file.getAsFile().get(),
                                  project.configuration.user_configuration);
                }
            });
    }
}


/**
 *  Worker class to generate the Master Label File (MLF)
 *
 */
class GeneratePrototypeWorker implements Runnable {
    /** Template file */
    private File template_file;

    /** Produced prototype file */
    private File prototype_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     *  @param lab_dir the directory containing the labels
     *  @param mlf_file the MLF file generated
     */
    @Inject
    public GeneratePrototypeWorker(File template_file, File prototype_file, Object configuration) {
        this.template_file = template_file;
        this.prototype_file = prototype_file;
        this.configuration = configuration;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {


        // Global informations
        def total_nb_states = configuration.models.global.nb_emitting_states + 2
        def nb_stream = 0
        def total_vec_size = 0
        def stream_msd_info = ""
        def stream_vec_size = ""
        def sweights = ""
        configuration.models.cmp.streams.each { stream ->
            if (stream.is_msd) {
                for (i in 1..stream.winfiles.size()) {
                    stream_msd_info += " 1"
                    stream_vec_size += " 1"
                    sweights += " " + stream.weight
                }
                total_vec_size += (stream.order + 1) * stream.winfiles.size()
                nb_stream += stream.winfiles.size()
            } else {
                stream_msd_info += " 0"
                stream_vec_size += " " + (stream.order + 1) * stream.winfiles.size()
                sweights += " " + stream.weight
                total_vec_size += (stream.order + 1) * stream.winfiles.size()
                nb_stream += 1
            }
        }

        // Now adapt the proto template
        def binding = [
            configuration:  configuration,
            SWEIGHTS:       sweights,
            GLOBALVECSIZE:  total_vec_size,
            NBSTREAM:       nb_stream,
            STREAMMSDINFO:  stream_msd_info,
            STREAMVECSIZE:  stream_vec_size,
            NBSTATES:       total_nb_states,
        ]

        // Adapt template
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        prototype_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
