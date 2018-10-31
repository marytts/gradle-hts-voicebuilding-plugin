package de.dfki.mary.htsvoicebuilding.export.task.hts_engine

// IO
import java.nio.file.Files;

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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*


/**
 *  Task which Description
 *
 */
public class ConvertDURToHTSEngineTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The template for the conversion script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** The label list file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The input tree file */
    @InputFile
    final RegularFileProperty input_tree_file = newInputFile();

    /** The input clustered mode file */
    @InputFile
    final RegularFileProperty input_model_file = newInputFile();

    /** The generated conversion script files */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    /** The converted script files */
    @OutputFile
    final RegularFileProperty output_tree_file = newOutputFile();

    /** The converted model files */
    @OutputFile
    final RegularFileProperty output_model_file = newOutputFile();


    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ConvertDURToHTSEngineTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(ConvertDURToHTSEngineWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        script_template_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        1, // NOTE: defacto start stream id for the duration is 1!
                        input_tree_file.getAsFile().get(),
                        input_model_file.getAsFile().get(),
                        script_file.getAsFile().get(),
                        output_tree_file.getAsFile().get(),
                        output_model_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper,
                    );
                }
            });
    }
}

/**
 *  Worker to Description
 *
 */
class ConvertDURToHTSEngineWorker implements Runnable {
    /** The template file for the script */
    private File template_file;

    /** The input tree file */
    private File input_tree_file;

    /** The input model file */
    private File input_model_file;

    /** The list file */
    private File list_file;

    /** The produced script file */
    private File script_file;

    /** The converted tree file */
    private File output_tree_file;

    /** The converted model file */
    private File output_model_file;

    /** The start index of the stream */
    private int stream_id;

    /** HTS Wrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ConvertDURToHTSEngineWorker(File template_file, File list_file, int stream_id,
                                       File input_tree_file, File input_model_file,
                                       File script_file, File output_tree_file, File output_model_file,
                                       HTSWrapper hts_wrapper) {
        // Inputs
        this.template_file = template_file;
        this.list_file = list_file;
        this.input_tree_file = input_tree_file;
        this.input_model_file = input_model_file;

        // Outputs
        this.script_file = script_file;
        this.output_tree_file = output_tree_file;
        this.output_model_file = output_model_file;

        // Utils
        this.stream_id = stream_id;
        this.hts_wrapper = hts_wrapper;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {


        def tmp_dir = new File(output_model_file.getParent().toString() + "/dur")
        tmp_dir.mkdir()
        def tmp_model = new File(tmp_dir.toString(),  "pdf.${stream_id}")
        def tmp_tree = new File(tmp_dir.toString(), "tree.${stream_id}")

        // Generate script file
        def binding = [
            tree_in: input_tree_file.toString(),
            model_out: tmp_model.getParent().toString(),
            tree_out: tmp_tree.getParent().toString(),
        ];
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        script_file.text = simple.createTemplate(source).make(binding).toString()


        // Apply conversion
        hts_wrapper.HHEdOnMMFNoOutputArgs(script_file.toString(),
                                          list_file.toString(),
                                          input_model_file.toString(),
                                          [])

        // Move the files
        print(output_tree_file)
        // Files.move(tmp_model.toPath(), output_model_file.toPath());
        // Files.move(tmp_tree.toPath(), output_tree_file.toPath());
    }
}
