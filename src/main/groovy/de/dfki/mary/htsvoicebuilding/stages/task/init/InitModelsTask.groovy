package de.dfki.mary.htsvoicebuilding.stages.task.init

// Template import
import groovy.text.*;

// HTS Wrapper import
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

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
 *  Task to bootstrap model files
 *
 */
public class InitModelsTask extends DefaultTask {

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The SCP file containing the list of training files */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The prototype file used to bootstrap the models */
    @InputFile
    final RegularFileProperty prototype_file = newInputFile();

    /** The template file for the duration vfloor file */
    @InputFile
    final RegularFileProperty vfloor_dur_template_file = newInputFile()

    /** The template file for the duration average file */
    @InputFile
    final RegularFileProperty average_dur_template_file = newInputFile()

    /** The vfloor file generated for the CMP part */
    @OutputFile
    final RegularFileProperty vfloor_cmp_file = newOutputFile()

    /** The average file generated for the CMP part */
    @OutputFile
    final RegularFileProperty average_cmp_file = newOutputFile()

    /** The init model file generated for the CMP part */
    @OutputFile
    final RegularFileProperty init_cmp_file = newOutputFile()

    /** The vfloor file generated for the duration part */
    @OutputFile
    final RegularFileProperty vfloor_dur_file = newOutputFile()

    /** The average file generated for the duration part */
    @OutputFile
    final RegularFileProperty average_dur_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public InitModelsTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {
        // Submit the execution of the CMP part
        workerExecutor.submit(InitCMPModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(scp_file.getAsFile().get(),
                                  prototype_file.getAsFile().get(),
                                  vfloor_cmp_file.getAsFile().get(),
                                  average_cmp_file.getAsFile().get(),
                                  init_cmp_file.getAsFile().get(),
                                  project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });

        // Submit the execution of the duration part
        workerExecutor.submit(InitDurModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(vfloor_dur_template_file.getAsFile().get(),
                                  average_dur_template_file.getAsFile().get(),
                                  vfloor_dur_file.getAsFile().get(),
                                  average_dur_file.getAsFile().get(),
                                  project.configuration.user_configuration);
                }
            });
    }
}


/**
 *  Worker class to bootstrap the models for the CMP part
 *
 */
class InitCMPModelWorker implements Runnable {
    /** The file containing the list of data files*/
    private File scp_file;

    /** The prototype file */
    private File prototype_file;

    /** The produced average model file */
    private File average_cmp_file;

    /** The produced vfloor model file */
    private File vfloor_cmp_file;

    /** The produced initialised model file */
    private File init_cmp_file;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     *  @param lab_dir the directory containing the labels
     *  @param mlf_file the MLF file generated
     */
    @Inject
    public InitCMPModelWorker(File scp_file, File prototype_file, File vfloor_cmp_file,
                              File average_cmp_file, File init_cmp_file, HTSWrapper hts_wrapper) {
        this.scp_file = scp_file;
        this.prototype_file = prototype_file;
        this.vfloor_cmp_file = vfloor_cmp_file;
        this.average_cmp_file = average_cmp_file;
        this.init_cmp_file = init_cmp_file;
        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        //   1. Get average model
        hts_wrapper.HCompV(scp_file.toString(),
                           prototype_file.toString(),
                           average_cmp_file.getName(),
                           average_cmp_file.getParent())

        //   2. Get Init model
        def header = prototype_file.readLines()[0]
        init_cmp_file.text = header + "\n" + vfloor_cmp_file.text
    }
}

/**
 *  Worker class to bootstrap the models for the duration part
 *
 */
class InitDurModelWorker implements Runnable {
    /** Average model template file */
    private File vfloor_dur_template_file;

    /** Average model template file */
    private File average_dur_template_file;

    /** The produced vfloor model file */
    private File vfloor_dur_file;

    /** The produced average model file */
    private File average_dur_file;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     *  @param lab_dir the directory containing the labels
     *  @param mlf_file the MLF file generated
     */
    @Inject
    public InitDurModelWorker(File vfloor_dur_template_file, File average_dur_template_file,
                              File vfloor_dur_file, File average_dur_file, Object configuration) {
        this.vfloor_dur_template_file = vfloor_dur_template_file;
        this.average_dur_template_file = average_dur_template_file;
        this.vfloor_dur_file = vfloor_dur_file;
        this.average_dur_file = average_dur_file;
        this.configuration = configuration;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Get a template engine instance
        def simple = new SimpleTemplateEngine()

        // 1. vfloor file
        def vfloor_template = vfloor_dur_template_file.text // FIXME: update template path
        def content = ""
        for (i in 1..configuration.models.global.nb_emitting_states) {
            def variance = configuration.models.dur.vflr
            variance *= configuration.models.dur.initvar

            def binding = [
                STATEID:i,
                VARIANCE:variance
            ]
            content += simple.createTemplate(vfloor_template).make(binding)
        }
        vfloor_dur_file.text = content

        // 2. average file (TODO: move that into the template and deal properly with the template !)
        content = ""
        for (i in 1..configuration.models.global.nb_emitting_states) {
            content += "\t\t<STREAM> $i\n"
            content += "\t\t\t<MEAN> 1\n"
            content += "\t\t\t\t" + configuration.models.dur.initmean + "\n"
            content += "\t\t\t<VARIANCE> 1\n"
            content += "\t\t\t\t" + configuration.models.dur.initvar + "\n"

        }

        def binding = [
            NBSTATES:configuration.models.global.nb_emitting_states,
            STATECONTENT:content,
            NAME:"average.mmf"
        ]

        def average_template = average_dur_template_file.text
        average_dur_file.text = simple.createTemplate(average_template).make(binding).toString()
    }
}
