package de.dfki.mary.htsvoicebuilding.stages.task.context

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
 *  Task to generate the fullcontext models from the monophone models
 *
 */
public class GenerateFullModelsTask extends DefaultTask {

    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of monophones */
    @InputFile
    final RegularFileProperty mono_list_file = newInputFile()

    /** The list of fullcontext labels */
    @InputFile
    final RegularFileProperty full_list_file = newInputFile()

    /** The monophone CMP model file */
    @InputFile
    final RegularFileProperty mono_model_cmp_file = newInputFile()

    /** The monophone duration model file */
    @InputFile
    final RegularFileProperty mono_model_dur_file = newInputFile()

    /** The produced fullcontext CMP model file */
    @OutputFile
    final RegularFileProperty full_model_cmp_file = newOutputFile()

    /** The produced fullcontext duration model file */
    @OutputFile
    final RegularFileProperty full_model_dur_file = newOutputFile()

    /** The produced monophone to fullcontext script file */
    @OutputFile
    final RegularFileProperty m2f_script_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateFullModelsTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // CMP
        def content = "// copy monophone models to fullcontext ones\n"
        content += "CL \"" + full_list_file.getAsFile().get().toString() + "\"\n\n"
        content += "// tie state transition probability\n"
        mono_list_file.getAsFile().get().eachLine { phone ->
            if (phone != "") {
                content += "TI T_$phone {*-$phone+*.transP}\n"
            }
        }
        m2f_script_file.getAsFile().get().text = content

        // Submit the execution of the CMP part
        workerExecutor.submit(GenerateFullcontextModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(m2f_script_file.getAsFile().get(),
                                  mono_list_file.getAsFile().get(),
                                  mono_model_cmp_file.getAsFile().get(),
                                  full_model_cmp_file.getAsFile().get(),
                                  project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });

        // Submit the execution of the duration part
        workerExecutor.submit(GenerateFullcontextModelWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(m2f_script_file.getAsFile().get(),
                                  mono_list_file.getAsFile().get(),
                                  mono_model_dur_file.getAsFile().get(),
                                  full_model_dur_file.getAsFile().get(),
                                  project.configurationVoiceBuilding.hts_wrapper
                    );
                }
            });
    }
}

/**
 *  Worker class to generate the fullcontext model from the corresponding monphone model
 *
 */
class GenerateFullcontextModelWorker implements Runnable {
    /** The file containing the list of monophones */
    private File list_file;

    /** The script file */
    private File script_file;

    /** Monophone model file */
    private File mono_model_file;

    /** Produced ullcontext model file */
    private File full_model_file;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateFullcontextModelWorker(File script_file, File list_file,
                                          File mono_model_file, File full_model_file,
                                          HTSWrapper hts_wrapper) {
        // Input files
        this.list_file = list_file;
        this.script_file = script_file
        this.mono_model_file = mono_model_file;

        // Generate model
        this.full_model_file = full_model_file;

        // Wrapper
        this.hts_wrapper = hts_wrapper;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        hts_wrapper.HHEdOnMMF(script_file.toString(),
                              list_file.toString(),
                              mono_model_file.toString(),
                              full_model_file.toString(),
                              [])
    }
}
