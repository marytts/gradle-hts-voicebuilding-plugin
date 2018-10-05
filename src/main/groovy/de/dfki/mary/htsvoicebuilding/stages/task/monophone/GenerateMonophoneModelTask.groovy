package de.dfki.mary.htsvoicebuilding.stages.task.monophone

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
 *  Task to generate the monophone models
 *
 */
public class GenerateMonophoneModelTask extends DefaultTask {

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The template for the script conversion file for the CMP part */
    @InputFile
    final RegularFileProperty script_template_cmp_file = newInputFile()

    /** The file containing the list of phones */
    @InputFile
    final RegularFileProperty list_file = newInputFile()

    /** The variance floor file for the CMP part */
    @InputFile
    final RegularFileProperty vfloor_cmp_file = newInputFile()

    /** The variance floor file for the duration part */
    @InputFile
    final RegularFileProperty vfloor_dur_file = newInputFile()

    /** The directory containing the bootstraped phone models for the CMP part */
    @InputDirectory
    final DirectoryProperty cmp_hrest_dir  = newInputDirectory()

    /** The directory containing the bootstraped phone models for the duration part */
    @InputDirectory
    final DirectoryProperty dur_hrest_dir  = newInputDirectory()

    /** The produced conversion script file for the CMP part */
    @OutputFile
    final RegularFileProperty script_cmp_file = newOutputFile()

    /** The produced monophone model file for the CMP part */
    @OutputFile
    final RegularFileProperty cmp_mmf_file = newOutputFile()

    /** The produced conversion script file for the duration part */
    @OutputFile
    final RegularFileProperty script_dur_file = newOutputFile()

    /** The produced monophone model file for the duration part */
    @OutputFile
    final RegularFileProperty dur_mmf_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateMonophoneModelTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateCMPMonophoneModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(list_file.getAsFile().get(),
                                  script_template_cmp_file.getAsFile().get(),
                                  vfloor_cmp_file.getAsFile().get(),
                                  cmp_hrest_dir.getAsFile().get(),
                                  script_cmp_file.getAsFile().get(),
                                  cmp_mmf_file.getAsFile().get(),
                                  (project.configuration.user_configuration.models.global.nb_emitting_states+1).intValue(),
                                  project.configuration.user_configuration.models.cmp.streams.size(),
                                  project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });

        // Submit the execution of the duration part
        workerExecutor.submit(GenerateDurationMonophoneModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(list_file.getAsFile().get(),
                                  vfloor_dur_file.getAsFile().get(),
                                  dur_hrest_dir.getAsFile().get(),
                                  script_dur_file.getAsFile().get(),
                                  dur_mmf_file.getAsFile().get(),
                                  project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}



/**
 *  Worker class to generate the generate the CMP monophone model
 *
 */
class GenerateCMPMonophoneModelWorker implements Runnable {
    /** The file containing the list of data files*/
    private File list_file;

    /** The script template file */
    private File script_template_cmp_file;

    /** The input variance floor file */
    private File vfloor_cmp_file;

    /** The input phone model directory */
    private File cmp_hrest_dir;

    /** The produced conversion script file */
    private File script_cmp_file;

    /** The produced monopyhone model file */
    private File cmp_mmf_file;

    /** The last state index */
    private int end_state;

    /** The number of streams in the HMM */
    private int nb_streams;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateCMPMonophoneModelWorker(File list_file, File script_template_cmp_file,
                                           File vfloor_cmp_file, File cmp_hrest_dir,
                                           File script_cmp_file, File cmp_mmf_file, int end_state, int nb_streams,
                                           HTSWrapper hts_wrapper) {
        // Input files
        this.list_file = list_file;
        this.script_template_cmp_file = script_template_cmp_file;
        this.vfloor_cmp_file = vfloor_cmp_file;
        this.cmp_hrest_dir = cmp_hrest_dir;

        // Produced files
        this.script_cmp_file = script_cmp_file;
        this.cmp_mmf_file = cmp_mmf_file;

        // some parameters
        this.end_state = end_state;
        this.nb_streams = nb_streams;

        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        // 1. Generate script
        def binding = [
            STARTSTATE: 2,
            ENDSTATE: end_state,
            VFLOORFILE: vfloor_cmp_file.toString(),
            NB_STREAMS: nb_streams
        ]

        def simple = new SimpleTemplateEngine()
        def source = script_template_cmp_file.text
        script_cmp_file.text = simple.createTemplate(source).make(binding).toString()

        //  2. Model conversion
        hts_wrapper.HHEdOnDir(script_cmp_file.toString(),
                              list_file.toString(),
                              cmp_hrest_dir.toString(),
                              cmp_mmf_file.toString())

    }
}



/**
 *  Worker class to generate the generate the duration monophone model
 *
 */
class GenerateDurationMonophoneModelWorker implements Runnable {
    /** The file containing the list of data files*/
    private File list_file;

    /** The duration vfloor file */
    private File vfloor_dur_file;

    /** The input phone model directory */
    private File dur_hrest_dir;

    /** The produced conversion script file */
    private File script_dur_file;

    /** The produced monopyhone model file */
    private File dur_mmf_file;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateDurationMonophoneModelWorker(File list_file, File vfloor_dur_file, File dur_hrest_dir,
                                                File script_dur_file, File dur_mmf_file, HTSWrapper hts_wrapper) {
        // Input files
        this.list_file = list_file;
        this.vfloor_dur_file = vfloor_dur_file;
        this.dur_hrest_dir = dur_hrest_dir;

        // Produced files
        this.script_dur_file = script_dur_file;
        this.dur_mmf_file = dur_mmf_file;


        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        //  1. Generate HHEd script
        def content = "// Load variance flooring macro\n"
        content += "FV \"${vfloor_dur_file}\""
        script_dur_file.text = content

        //  2. Model conversion
        hts_wrapper.HHEdOnDir(script_dur_file.toString(),
                              list_file.toString(),
                              dur_hrest_dir.toString(),
                              dur_mmf_file.toString())

    }
}
