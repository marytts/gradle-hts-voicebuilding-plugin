package de.dfki.mary.htsvoicebuilding.stages.task.gv

// Template
import groovy.text.*;

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
 *  Task which generates the prototype for the GV
 *
 */
public class GenerateGVProtoTask extends DefaultTask {

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The prototype template file */
    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The produced prototype file */
    @OutputFile
    final RegularFileProperty proto_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateGVProtoTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateGVProtoWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        template_file.getAsFile().get(),
                        proto_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to generate the prototype for the GV
 *
 */
class GenerateGVProtoWorker implements Runnable {

    /** The prototype template file */
    private File template_file;

    /** The produced prototype file */
    private File proto_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateGVProtoWorker(File template_file, File proto_file,
                                 Object configuration) {
        this.template_file = template_file;
        this.proto_file = proto_file;
        // Utilities
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        def nb_stream = 0
        def total_vec_size = 0
        def stream_msd_info = ""
        def stream_vec_size = ""

        configuration.models.cmp.streams.each { stream ->
            stream_msd_info += " 0"
            stream_vec_size += " " + (stream.order + 1)
            total_vec_size += (stream.order + 1)
            nb_stream += 1
        }

        def binding = [
            configuration: configuration,
            GLOBALVECSIZE: total_vec_size,
            NBSTREAM: nb_stream,
            STREAMMSDINFO: stream_msd_info,
            STREAMVECSIZE: stream_vec_size
        ]

        // Now adapt the proto template
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        proto_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
