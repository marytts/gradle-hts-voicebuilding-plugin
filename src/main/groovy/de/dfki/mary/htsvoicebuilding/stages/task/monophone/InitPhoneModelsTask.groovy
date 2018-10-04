package de.dfki.mary.htsvoicebuilding.stages.task.monophone

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


import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class InitPhoneModelsTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** DAEM initialisation activation */
    Boolean use_daem;

    /** The SCP file containing the list of training files */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** The MLF file */
    @InputFile
    final RegularFileProperty mlf_file = newInputFile();

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The prototype file */
    @InputFile
    final RegularFileProperty prototype_file = newInputFile();

    /** The vfloor file generated for the CMP part */
    @InputFile
    final RegularFileProperty vfloor_cmp_file = newInputFile()

    /** The average file generated for the CMP part */
    @InputFile
    final RegularFileProperty average_cmp_file = newInputFile()

    /** The average file generated for the CMP part */
    @InputFile
    final RegularFileProperty average_dur_file = newInputFile()

    /** The init model file generated for the CMP part */
    @InputFile
    final RegularFileProperty init_cmp_file = newInputFile()

    @OutputDirectory
    final DirectoryProperty cmp_hinit_dir = newOutputDirectory()

    @OutputDirectory
    final DirectoryProperty dur_hrest_dir = newOutputDirectory()

    @OutputDirectory
    final DirectoryProperty cmp_hrest_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public InitPhoneModelsTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void init() {
        for (String phone: list_file.getAsFile().get().readLines()) {

            // Submit the execution
            workerExecutor.submit(InitPhoneModelsWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(phone, use_daem,
                                      scp_file.getAsFile().get(),
                                      mlf_file.getAsFile().get(),
                                      prototype_file.getAsFile().get(),
                                      average_cmp_file.getAsFile().get(),
                                      average_dur_file.getAsFile().get(),
                                      init_cmp_file.getAsFile().get(),
                                      cmp_hinit_dir.getAsFile().get(),
                                      cmp_hrest_dir.getAsFile().get(),
                                      dur_hrest_dir.getAsFile().get(),
                                      project.configurationVoiceBuilding.hts_wrapper
                        );
                    }
                });
        }
    }
}



/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class InitPhoneModelsWorker implements Runnable {

    /** The phone name */
    private String phone;

    /** Boolean to activate DAEM initialisation */
    private boolean use_daem;

    /** The training list file */
    private File scp_file;

    /** The mlf file */
    private File mlf_file;

    /** The prototype file */
    private File prototype_file;

    /** The CMP average file */
    private File average_cmp_file;

    /** The duration average file */
    private File average_dur_file;

    /** The duration average file */
    private File cmp_hinit_dir;

    /** Duration HREST directory */
    private File dur_hrest_dir;

    /** CMP HREST directory */
    private File cmp_hrest_dir;

    /** Initialisation file */
    private File init_cmp_file;

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public InitPhoneModelsWorker(String phone, boolean use_daem,
                                 File scp_file, File mlf_file,
                                 File prototype_file,
                                 File average_cmp_file, File average_dur_file,
                                 File init_cmp_file,
                                 File cmp_hinit_dir, File cmp_hrest_dir, File dur_hrest_dir,
                                 HTSWrapper hts_wrapper) {
        this.phone = phone;
        this.use_daem = use_daem;
        this.scp_file = scp_file;
        this.mlf_file = mlf_file;
        this.prototype_file = prototype_file;
        this.average_cmp_file = average_cmp_file;
        this.average_dur_file = average_dur_file;
        this.init_cmp_file = init_cmp_file;
        this.cmp_hinit_dir = cmp_hinit_dir;
        this.cmp_hrest_dir = cmp_hrest_dir;
        this.dur_hrest_dir = dur_hrest_dir;
        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {
        if (use_daem) {
            // FIXME: logging
            println("use average model instead of $phone")

            // CMP
            def contents = average_cmp_file.text
            (new File(cmp_hrest_dir.toString(), phone)).text = contents.replaceAll("average.mmf", "$phone")

            // Duration
            contents = average_dur_file.text
            (new File(dur_hrest_dir.toString(), phone)).text = contents.replaceAll("average.mmf", "$phone")

        } else {

            /**
             * FIXME: See for
             *   - WARNING [-7032]  OWarn: change HMM Set swidth[0] in HRest
             *   - WARNING [-7032]  OWarn: change HMM Set msdflag[0] in HRest
             */
            // HInit
            hts_wrapper.HInit(phone,
                              scp_file.toString(),
                              prototype_file.toString(),
                              init_cmp_file.toString(),
                              mlf_file.toString(),
                              cmp_hinit_dir.toString())


            // HRest
            hts_wrapper.HRest(phone,
                              scp_file.toString(),
                              cmp_hinit_dir.toString(),
                              init_cmp_file.toString(),
                              mlf_file.toString(),
                              cmp_hrest_dir.toString(),
                              dur_hrest_dir.toString())
        }
    }
}
