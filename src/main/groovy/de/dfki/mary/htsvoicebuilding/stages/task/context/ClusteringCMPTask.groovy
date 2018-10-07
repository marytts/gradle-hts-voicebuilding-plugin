package de.dfki.mary.htsvoicebuilding.stages.task.context

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
 *  Task to cluster the CMP part
 *
 */
public class ClusteringCMPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** Local cluster index */
    @Internal
    int local_cur_clus_it;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The template for the tree generation script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** Question file */
    @InputFile
    final RegularFileProperty question_file = newInputFile(); // FIXME: split per stream

    /** Statistics CMP file */
    @InputFile
    final RegularFileProperty stats_cmp_file = newInputFile();

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
    public ClusteringCMPTask(WorkerExecutor workerExecutor) {
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

        for (def stream: streams) {
            // FIXME: Define indexes
            def end_stream = project_cur_stream
            def cur_stream = project_cur_stream
            if (stream.is_msd) {
                end_stream += stream.winfiles.size() - 1
            }

            def streamname = stream.name

            //   2. generate HHEd scripts (FIXME: output_file would be better for that)
            def script_file = new File("${project.configurationVoiceBuilding.hhed_script_dir}/cxc_${stream.name}.${local_cur_clus_it}.hed")
            def tree_file = new File("${project.configurationVoiceBuilding.tree_dir}/${stream.name}.${local_cur_clus_it}.inf")
            project.copy {

                from script_template_file.getAsFile().get().getParent()
                into script_file.getParent()
                include script_template_file.getAsFile().get().getName()
                rename { file -> script_file.getName() }

                def questions = question_file.getAsFile().get().text
                def streamline = ""
                for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                    streamline += "TB " + stream.thr + " " + stream.name +  "_s" + i + "_ "
                    streamline += "{*.state[" + i + "].stream[" + stream.start +  "-" + (stream.end)+ "]}\n"
                }

                def binding = [
                    GAM : sprintf("%03d", stream.gam),
                    STATSFILE: stats_cmp_file.getAsFile().get().toString(),
                    QUESTIONS: questions,
                    STREAMLINE: streamline,
                    OUTPUT: tree_file.toString()
                ]

                expand(binding)
            }


            //   3. build the decision tree (FIXME: input file would be better!)
            def params = ["-C", "${project.configurationVoiceBuilding.config_dir}/${stream.name}.cfg"]

            if (stream.thr == 0) {
                params += ["-m", "-a", stream.mdlf]
            }

            // Get cluster model file
            File clustered_model_file = null;
            for (File cur_file: clustered_model_files_set) {
                if (cur_file.getName().endsWith("${stream.name}.${local_cur_clus_it}")) {
                    clustered_model_file = cur_file;
                    break;
                }
            }

            // Submit the execution
            workerExecutor.submit(ClusteringCMPWorker.class,
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
        }
    }
}

/**
 *  Worker class which cluster one kind of coefficient part of the CMP part
 *
 */
class ClusteringCMPWorker implements Runnable {

    /** List of labels file */
    private File list_file;

    /** Conversion script file */
    private File script_file;

    /** Input full context model file */
    private File fullcontext_model_file;

    /** Produced clustered model file */
    private File clustered_model_file;

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;

    /** Conversion parameters */
    private ArrayList<String> params;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public ClusteringCMPWorker(File script_file, File list_file,
                               File fullcontext_model_file, File clustered_model_file,
                               HTSWrapper hts_wrapper, ArrayList<String> params) {
        // Input
        this.script_file = script_file;
        this.list_file = list_file;
        this.fullcontext_model_file = fullcontext_model_file;

        // Outputs
        this.clustered_model_file = clustered_model_file;

        // Utilities
        this.hts_wrapper = hts_wrapper;
        this.params = params;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        // Cluster
        hts_wrapper.HHEdOnMMF(script_file.toString(),
                              list_file.toString(),
                              fullcontext_model_file.toString(),
                              clustered_model_file.toString(),
                              params)
    }
}
