package de.dfki.mary.htsvoicebuilding.stages.task.gv

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


import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Task to cluster the GV part
 *
 */
public class ClusteringGVTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;


    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The template for the tree generation script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** Question file */
    @InputFile
    final RegularFileProperty question_file = newInputFile(); // FIXME: split per stream

    /** Statistics GV file */
    @InputFile
    final RegularFileProperty stats_file = newInputFile();

    /** The input fullcontext model file */
    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile();

    /** The output clustered files */
    @OutputFiles
    ConfigurableFileCollection clustered_model_files = project.files();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ClusteringGVTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void cluster() {

        def clustered_model_files_set = clustered_model_files.getFiles();
        def project_cur_stream = 1

        // Prepare parallelism part !
        def streams = []
        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            // FIXME: Define indexes
            stream.start = project_cur_stream
            stream.end   = project_cur_stream
            if (stream.is_msd) {
                stream.end += stream.winfiles.size() - 1
            }

            project_cur_stream = stream.end + 1
            streams << stream
        }

        int s = 1
        for (def stream: streams) {

            def streamname = stream.name

            //   2. generate HHEd scripts (FIXME: output file)
            def questions = question_file.getAsFile().get().text
            def streamline = "TB " + stream.gv.thr + " gv_" + stream.name +  "_ {*.state[2].stream[$s]}\n"
            def tree_file = new File("${project.configurationVoiceBuilding.gv_dir}/${stream.name}.inf")
            def binding = [
                GAM : stream.gv.gam,
                STATSFILE: stats_file.getAsFile().get().toString(),
                QUESTIONS: questions,
                STREAMLINE: streamline,
                OUTPUT: tree_file.toString()
            ]

            File script_file = new File("${project.configurationVoiceBuilding.hhed_script_dir}/cxc_${stream.name}.gv.hed")
            project.copy {
                from script_template_file.getAsFile().get()
                into script_file.getParent()
                rename {file -> script_file.getName()}
                expand(binding)
            }


            //   3. build the decision tree
            def params = []
            if (stream.thr == 0) {
                params += ["-m", "-a", stream.gv.mdlf]
            }

            // Get cluster model file
            File clustered_model_file = null;
            for (File cur_file: clustered_model_files_set) {
                if (cur_file.getName().endsWith("${stream.name}")) {
                    clustered_model_file = cur_file;
                    break;
                }
            }

            // Submit the execution
            workerExecutor.submit(ClusteringGVWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            script_file,
                            list_file.getAsFile().get(),
                            fullcontext_model_file.getAsFile().get(),
                            clustered_model_file,
                            project.configurationVoiceBuilding.hts_wrapper,
                            params
                        );
                    }
                });

            // Next stream
            s += 1
        }
    }
}

/**
 *  Worker class which cluster one kind of coefficient part of the GV part
 *
 */
class ClusteringGVWorker implements Runnable {


    /** The clustering script file */
    private File script_file;

    /** The list of label file */
    private File list_file;

    /** The input full context model file */
    private File fullcontext_model_file;

    /** The produced cluster model file */
    private File clustered_model_file;

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;

    /** Some parameters */
    private ArrayList<String> params;

    /**
     *  The contructor which initialize the worker
     *
     */
    @Inject
    public ClusteringGVWorker(File script_file, File list_file,
                              File fullcontext_model_file, File clustered_model_file,
                              HTSWrapper hts_wrapper, ArrayList<String> params) {
        this.script_file = script_file;
        this.list_file = list_file;
        this.fullcontext_model_file = fullcontext_model_file;
        this.clustered_model_file = clustered_model_file;
        this.hts_wrapper = hts_wrapper;
        this.params = params;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        hts_wrapper.HHEdOnMMF(script_file.toString(),
                              list_file.toString(),
                              fullcontext_model_file.toString(),
                              clustered_model_file.toString(),
                              params)
    }
}
