package de.dfki.mary.htsvoicebuilding.export.task.hts_engine

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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*


/**
 *  Task which Description
 *
 */
public class ConvertCMPToHTSEngineTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The template for the conversion script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** The label list file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The file collection of the tree files */
    @InputFiles
    final ConfigurableFileCollection input_tree_files = project.files();

    /** The input clustered mode file */
    @InputFile
    final RegularFileProperty input_model_file = newInputFile();

    /** The generated conversion script files */
    @OutputFiles
    final ConfigurableFileCollection script_files = project.files();

    /** The converted script files */
    @OutputFiles
    final ConfigurableFileCollection output_tree_files = project.files();

    /** The converted model files */
    @OutputFiles
    final ConfigurableFileCollection output_model_files = project.files();


    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ConvertCMPToHTSEngineTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {

        def start_stream = 1;
        for (def stream: project.configuration.user_configuration.models.cmp.streams) {

            // Get the script file
            File script_file = null;
            for (File cur_file: script_files.getFiles()) {
                if (cur_file.getName().startsWith("cv_hts_engine_${stream.kind}.")) {
                    script_file = cur_file;
                    break;
                }
            }

            // Get the input tree file
            File input_tree_file = null;
            for (File cur_file: input_tree_files.getFiles()) {
                if (cur_file.getName().startsWith("${stream.kind}.")) {
                    input_tree_file = cur_file;
                    break;
                }
            }

            // Get the output tree file
            File output_tree_file = null;
            for (File cur_file: output_tree_files.getFiles()) {
                if (cur_file.getName().startsWith("${stream.kind}.")) {
                    output_tree_file = cur_file;
                    break;
                }
            }

            // Get the output model file
            File output_model_file = null;
            for (File cur_file: output_model_files.getFiles()) {
                if (cur_file.getName().startsWith("${stream.kind}.")) {
                    output_model_file = cur_file;
                    break;
                }
            }


            // Submit the execution
            workerExecutor.submit(ConvertCMPToHTSEngineWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            script_template_file.getAsFile().get(),
                            list_file.getAsFile().get(),
                            start_stream,
                            input_tree_file,
                            input_model_file.getAsFile().get(),
                            script_file,
                            output_tree_file,
                            output_model_file,
                            project.configurationVoiceBuilding.hts_wrapper,
                        );
                    }
                });

            // Next stream start
            if (stream.is_msd) {
                start_stream += stream.winfiles.size()
            } else {
                start_stream += 1
            }
        }
    }
}

/**
 *  Worker to Description
 *
 */
class ConvertCMPToHTSEngineWorker implements Runnable {
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
    public ConvertCMPToHTSEngineWorker(File template_file, File list_file, int stream_id,
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

        // NOTE: HTS have a segfault when you are outputin the hts voice in another directory than
        // the input model directory
        def tmp_model = new File(input_model_file.getParent().toString(),  "pdf.${stream_id}")
        def tmp_tree = new File(input_tree_file.getParent().toString(), "tree.${stream_id}")

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
        tmp_model.renameTo(output_model_file)
        tmp_tree.renameTo(output_tree_file)
    }
}
