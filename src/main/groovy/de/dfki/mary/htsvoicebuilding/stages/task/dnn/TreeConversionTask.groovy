package de.dfki.mary.htsvoicebuilding.stages.task.dnn

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
public class TreeConversionTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    int local_cur_clus_it;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    @InputFile
    final RegularFileProperty dur_script_file = newInputFile();

    @InputFile
    final RegularFileProperty input_dur_model_file = newInputFile();

    @OutputFile
    final RegularFileProperty output_dur_model_file = newOutputFile();

    @InputFile
    final RegularFileProperty cmp_script_file = newInputFile();

    @InputFile
    final RegularFileProperty input_cmp_model_file = newInputFile();

    @OutputFile
    final RegularFileProperty output_cmp_model_file = newOutputFile();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public TreeConversionTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void convert() {

        // Submit the execution for the duration
        workerExecutor.submit(TreeConversionWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        dur_script_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        input_dur_model_file.getAsFile().get(),
                        output_dur_model_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });


        // Submit the execution for the cmpation
        workerExecutor.submit(TreeConversionWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        cmp_script_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        input_cmp_model_file.getAsFile().get(),
                        output_cmp_model_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}



/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class TreeConversionWorker implements Runnable {

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;
    private File list_file;
    private File input_model_file;
    private File output_model_file;
    private File script_file;



    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public ClusteringCMPWorker(File script_file, File list_file,
                               File input_model_file, File output_model_file,
                               HTSWrapper hts_wrapper) {
        this.script_file = script_file;
        this.list_file = list_file;
        this.fullcontext_model_file = fullcontext_model_file;
        this.clustered_model_file = clustered_model_file;
        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        hts_wrapper.HHEdOnMMF(script_file.getAsFile().get().toString(),
                              list_file.getAsFile().get().toString(),
                              input_model_file.getAsFile().get().toString(),
                              output_model_file.getAsFile().get().toString(),
                              [])
    }
}
