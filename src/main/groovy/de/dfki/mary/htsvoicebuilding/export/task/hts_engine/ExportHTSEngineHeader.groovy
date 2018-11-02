package de.dfki.mary.htsvoicebuilding.export.task.hts_engine

// Template import
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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*


/**
 *  Task which export the HTS engine formatted header
 *
 */
public class ExportHTSEngineHeaderTask extends DefaultTask {

    @InputFile
    final RegularFileProperty template_file = newInputFile()

    @OutputFile
    final RegularFileProperty header_file = newOutputFile()

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ExportHTSEngineHeaderTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(ExportHTSEngineHeaderWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        template_file.getAsFile().get(),
                        header_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to export the HTS engine formatted header
 *
 */
class ExportHTSEngineHeaderWorker implements Runnable {

    private File template_file;

    private File header_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ExportHTSEngineHeaderWorker(File template_file, File header_file, Object configuration) {
        this.template_file = template_file
        this.header_file = header_file;
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Define needed bindings
        def binding = [configuration: configuration]


        // Now generate the header
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        header_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
