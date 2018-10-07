package de.dfki.mary.htsvoicebuilding.stages.task.dnn

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
 *  Task which generates the synthesis configuration file needed for the DNN training
 *
 */
public class GenerateSynthConfigFileTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The configuration template file */
    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The produced configuration file */
    @OutputFile
    final RegularFileProperty configuration_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateSynthConfigFileTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateSynthConfigFileWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        template_file.getAsFile().get(),
                        configuration_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to generate the synthesis configuration file needed for the DNN training
 *
 */
class GenerateSynthConfigFileWorker implements Runnable {

    /** The configuration template file */
    private File template_file;

    /** The produced configuration file */
    private File configuration_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateSynthConfigFileWorker(File template_file, File configuration_file, Object configuration) {
        this.template_file = template_file;
        this.configuration_file = configuration_file;

        // Utilities
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // train.cfg
        def nbstream = 0
        def cmpstream = []
        def pdfstrkind = []
        def pdfstrorder = []
        def pdfstrwin = []

        configuration.models.cmp.streams.each { stream ->
            pdfstrkind << stream.kind
            pdfstrorder << (stream.order + 1).toString()

            pdfstrwin << "StrVec"
            pdfstrwin << stream.winfiles.size().toString()
            stream.winfiles.each {
                pdfstrwin << it
            }

            if (stream.is_msd) {
                cmpstream << stream.winfiles.size().toString()

            } else {
                cmpstream << "1"
            }
            nbstream += 1
        }


        def binding = [
            NB_STREAMS: nbstream,
            MAXEMITER: 20, // FIXME: hardcoded
            CMP_STREAM: cmpstream.join(" "),
            VEC_SIZE: pdfstrorder.join(" "),
            EXT_LIST: pdfstrkind.join(" "),
            WIN_LIST: pdfstrwin.join(" ")

        ]


        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        configuration_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
