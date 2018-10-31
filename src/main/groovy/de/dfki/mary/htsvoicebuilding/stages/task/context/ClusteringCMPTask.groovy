package de.dfki.mary.htsvoicebuilding.stages.task.context

import java.util.ArrayList;

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

    /** Local stream name */
    @Internal
    String stream_name;

    /** Local stream start */
    @Internal
    int stream_start;

    /** Local stream start */
    @Internal
    int stream_end;

    /** Local stream thr */
    @Internal
    String stream_thr;

    /** Local stream gam */
    @Internal
    int stream_gam;

    /** Local stream mdlf */
    @Internal
    double stream_mdlf;

    /** Configuration file */
    @InputFile
    final RegularFileProperty config_file = newInputFile();

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

    /** The original input model file */
    @InputFile
    final RegularFileProperty transitive_model_file = newInputFile();

    /** The output tree files */
    @OutputFiles
    ConfigurableFileCollection tree_files = project.files();

    /** The output tree files */
    @Internal
    ArrayList<File> tree_file_list

    /** The output tree files */
    @OutputFiles
    ConfigurableFileCollection clustered_model_files = project.files();

    /** The output tree files */
    @Internal
    ArrayList<File> clustered_model_file_list

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

        for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
            // 0. Get/init some baseline files
            File script_file = new File("${project.configurationVoiceBuilding.hhed_script_dir}/${local_cur_clus_it}/cxc_${stream_name}_${i}.hed")
            File tree_file = tree_file_list.get(i-2)
            File clustered_model_file = clustered_model_file_list.get(i-2);

            // 1. Prepare directories
            if (! tree_file.getParentFile().exists()) {
                tree_file.getParentFile().mkdirs();
            }
            if (! script_file.getParentFile().exists()) {
                script_file.getParentFile().mkdirs();
            }

            // 2. generate HHEd scripts (FIXME: output_file would be better for that)
            project.copy {
                from script_template_file.getAsFile().get().getParent()
                into script_file.getParent()
                include script_template_file.getAsFile().get().getName()
                rename { file -> script_file.getName() }

                def questions = question_file.getAsFile().get().text
                def streamline = ""
                streamline += "TB " + stream_thr + " " + stream_name +  "_s" + i + "_ "
                streamline += "{*.state[" + i + "].stream[" + stream_start +  "-" + (stream_end)+ "]}\n"

                def binding = [
                    GAM : sprintf("%03d", stream_gam),
                    STATSFILE: stats_cmp_file.getAsFile().get().toString(),
                    QUESTIONS: questions,
                    STREAMLINE: streamline,
                    OUTPUT: tree_file.toString()
                ]

                expand(binding)
            }


            //  3. Add needed options for clustering
            def params = ["-C", config_file.getAsFile().get().toString()]
            if (stream_thr == "000") {
                params += ["-m", "-a", stream_mdlf]
            }


            // Submit the execution
            workerExecutor.submit(ClusteringStateCMPWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            script_file,
                            list_file.getAsFile().get(),
                            transitive_model_file.getAsFile().get(),
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
class ClusteringStateCMPWorker implements Runnable {

    /** List of labels file */
    private File list_file;

    /** Conversion script file */
    private File script_file;

    /** Input full context model file */
    private File input_model_file;

    /** Produced clustered model file */
    private File output_model_file

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
    public ClusteringStateCMPWorker(File script_file, File list_file,
                               File input_model_file, File output_model_file,
                               HTSWrapper hts_wrapper, ArrayList<String> params) {
        // Input
        this.script_file = script_file;
        this.list_file = list_file;
        this.input_model_file = input_model_file;

        // Outputs
        this.output_model_file = output_model_file;

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
                              input_model_file.toString(),
                              output_model_file.toString(),
                              params)
    }
}
