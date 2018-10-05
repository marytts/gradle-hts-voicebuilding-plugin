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
 *  Task to cluster the duration
 *
 */
public class ClusteringDURTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The configuration file */
    @InputFile
    final RegularFileProperty config_file = newInputFile();

    /** The question file */
    @InputFile
    final RegularFileProperty question_file = newInputFile();

    /** The template for the tree generation script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** The input fullcontext model file */
    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile();

    /** Statistics CMP file */
    @InputFile
    final RegularFileProperty stats_cmp_file = newInputFile();

    /** Produced statistics duration file */
    @OutputFile
    final RegularFileProperty stats_dur_file = newOutputFile();

    /** Produced tree generation script file */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    /** Produced tree file */
    @OutputFile
    final RegularFileProperty tree_file = newOutputFile();

    /** Produced clustered model file */
    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ClusteringDURTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void cluster() {
        // Submit the execution
        workerExecutor.submit(ClusteringDurWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        script_template_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        config_file.getAsFile().get(),
                        stats_cmp_file.getAsFile().get(),
                        fullcontext_model_file.getAsFile().get(),
                        question_file.getAsFile().get(),
                        tree_file.getAsFile().get(),
                        stats_dur_file.getAsFile().get(),
                        script_file.getAsFile().get(),
                        clustered_model_file.getAsFile().get(),
                        project.configurationVoiceBuilding.hts_wrapper,
                        project.configuration.user_configuration
                    );
                }
            });
    }
}


/**
 *  Worker class which cluster the duration
 *
 */
class ClusteringDurWorker implements Runnable {

    /** Template for script file */
    private File script_template_file;

    /** List file */
    private File list_file;

    /** Configuration file */
    private File config_file;

    /** Fullcontext model file */
    private File fullcontext_model_file;

    /** Question file */
    private File question_file;

    /** Produced stats CMP file */
    private File stats_cmp_file;

    /** Produced stats duration file */
    private File stats_dur_file;

    /** Produced tree file */
    private File tree_file;

    /** Produced script file */
    private File script_file;

    /** Produced lustered model file*/
    private File clustered_model_file;

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;

    /** Configuration object */
    private Object configuration;

    /**
     *  The contructor which initialize the worker
     *
     */
    @Inject
    public ClusteringDurWorker(File script_template_file, File list_file, File config_file,
                               File stats_cmp_file, File fullcontext_model_file, File question_file,
                               File tree_file, File stats_dur_file,
                               File script_file, File clustered_model_file,
                               HTSWrapper hts_wrapper, Object configuration) {

        this.script_template_file = script_template_file;
        this.stats_cmp_file = stats_cmp_file;
        this.list_file = list_file;
        this.config_file = config_file;
        this.question_file = question_file;
        this.tree_file = tree_file;
        this.fullcontext_model_file = fullcontext_model_file;
        this.clustered_model_file = clustered_model_file;
        this.stats_dur_file = stats_dur_file;
        this.script_file = script_file;
        this.hts_wrapper = hts_wrapper;
        this.configuration = configuration;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        // 1. Generate stats file
        def content = ""
        stats_cmp_file.eachLine { line ->
            def array = line.split()
            content += sprintf("%4d %14s %4d %4d\n",
                               Integer.parseInt(array[0]), array[1],
                               Integer.parseInt(array[2]), Integer.parseInt(array[2]))
        }
        stats_dur_file.text = content

        //   2. generate HHEd scripts
        def questions = question_file.text
        def streamline = "TB " + configuration.models.dur.thr + " dur_s2_ {*.state[2].stream[1-5]}"
        def binding = [
            GAM: sprintf("%03d", configuration.models.dur.gam),
            STATSFILE: stats_dur_file.toString(),
            QUESTIONS: questions,
            STREAMLINE: streamline,
            OUTPUT: tree_file.toString()
        ]

        def simple = new SimpleTemplateEngine()
        def source = script_template_file.text
        script_file.text = simple.createTemplate(source).make(binding).toString()

        //   3. build the decision tree
        def params = ["-C", config_file.toString()]
        if (configuration.models.dur.thr == 0) {
            params += ["-m", "-a", configuration.models.dur.mdlf]
        }

        hts_wrapper.HHEdOnMMF(script_file.toString(),
                              list_file.toString(),
                              fullcontext_model_file.toString(),
                              clustered_model_file.toString(),
                              params)
    }
}
