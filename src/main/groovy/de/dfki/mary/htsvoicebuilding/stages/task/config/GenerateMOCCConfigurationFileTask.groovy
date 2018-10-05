package de.dfki.mary.htsvoicebuilding.stages.task.config

// Template import
import groovy.text.*

// Hashtable to link mocc files and values
import java.util.Hashtable

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.*;


/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateMOCCConfigurationFileTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The MOCC value/file associations */
    @Internal
    Hashtable<File, Float> mocc_values = new Hashtable<File, Float>();

    /** The list of files to manipulate */
    @OutputFiles
    ConfigurableFileCollection mocc_files = project.files();

    /** The directory containing the spectrum files */
    @InputFile
    final RegularFileProperty template_file = newInputFile();

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateMOCCConfigurationFileTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        for (File mocc_file: mocc_files.getFiles()) {
            // Submit the execution
            workerExecutor.submit(GenerateMOCCConfigurationFileWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(template_file.getAsFile().get(),
                                      (Float) mocc_values.get(mocc_file),
                                      mocc_file);
                    }
                });
        }
    }
}


/**
 *  Worker class to generate the Master Label File (MLF)
 *
 */
class GenerateMOCCConfigurationFileWorker implements Runnable {
    /** Template file */
    private File template_file;

    /** MOCC file produced by the worker */
    private File mocc_file;

    /** MOCC value */
    private Float mocc_value;

    /**
     *  The constructor of the worker
     *
     *  @param template_file the template file to generate the MOCC configuration
     *  @param mocc_file the mocc file
     *  @param mocc_value the MOCC value
     */
    @Inject
    public GenerateMOCCConfigurationFileWorker(File template_file, Float mocc_value, File mocc_file) {
        this.template_file = template_file;
        this.mocc_value = mocc_value;
        this.mocc_file = mocc_file;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        def binding = [mocc: mocc_value]
        mocc_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
