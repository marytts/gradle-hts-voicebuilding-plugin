package de.dfki.mary.htsvoicebuilding.stages.task.config

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
 *  Task to generate the training configuration file
 *
 */
public class GenerateTrainingConfigurationTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The configuration template file */
    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The generated configuration file */
    @OutputFile
    final RegularFileProperty configuration_file = newOutputFile()


    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateTrainingConfigurationTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {
        // train.cfg
        int nbstream = 0
        String vfloorvalues = ""
        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            if (stream.is_msd) {
                nbstream += stream.winfiles.size()
                for (i in 0..(stream.winfiles.size()-1)) {
                    vfloorvalues += " " + stream.vflr
                }
            } else {
                nbstream += 1
                vfloorvalues += " " + stream.vflr
            }
        }

        // Submit the execution
        workerExecutor.submit(GenerateTrainingConfigurationWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(template_file.getAsFile().get(),
                                  configuration_file.getAsFile().get(),
                                  (project.configuration.user_configuration.models.dur.vflr * 100).floatValue(),
                                  project.configuration.user_configuration.settings.training.maxdev.intValue(),
                                  project.configuration.user_configuration.settings.training.mindur.intValue(),
                                  nbstream, vfloorvalues);
                }
            });
    }
}


/**
 *  Worker class to generate the training configuration file
 *
 */
class GenerateTrainingConfigurationWorker implements Runnable {
    /** Template file */
    private File template_file;

    /** Produced configuration file */
     private File configuration_file;

     /** Duration variance floor value */
    private float vfloordur;

    /** FIXME: what is that? */
    private int maxdev;


    /** Minimum duration (in frames) */
    private int mindur;

    /** Number of streams */
    private int nbstream;

    /** Vfloor value string for CMP part */
    private String vfloorvalues;


    /**
     *  The constructor of the worker
     *
     *  @param list_basenames_file file containing the basename list
     *  @param data_dir the directory containing the data
     *  @param scp_file the SCP file generated
     */
    @Inject
    public GenerateTrainingConfigurationWorker(File template_file, File configuration_file,
                                               float vfloordur, int maxdev, int mindur, int nbstream, String vfloorvalues) {
        this.template_file = template_file;
        this.configuration_file = configuration_file;
        this.vfloordur = vfloordur;
        this.maxdev = maxdev;
        this.mindur = mindur;
        this.nbstream = nbstream;
        this.vfloorvalues = vfloorvalues;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {


        def binding = [
            VFLOORDUR : vfloordur,
            MAXDEV : maxdev,
            MINDUR : mindur,
            NBSTREAM : nbstream,
            VFLOORVALUES: vfloorvalues
        ]

        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        configuration_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
