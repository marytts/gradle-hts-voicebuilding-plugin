package de.dfki.mary.htsvoicebuilding.stages.task.context

// List utils
import java.util.ArrayList;

// Utils for copy
import java.nio.file.Files;

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

// HTS wrapper
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Task to join isolated clustered models to a common model
 *
 */
public class JoinClusteredCMPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The clustering step index */
    @Internal
    int local_cur_clus_it;

    /** The start index of the manipulated stream */
    @Internal
    int stream_start;

    /** The end index of the manipulated stream */
    @Internal
    int stream_end;

    /** The list of clustered file as I need an ordered collection ! */
    @Internal
    ArrayList<File> clustered_cmp_file_list;

    /** The produced join clustered models */
    @Internal
    RegularFileProperty clustered_model_file

    /** The individual clustered files */
    @InputFiles
    ConfigurableFileCollection clustered_cmp_files = project.files()

    /** The list of full context labels */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The produced joining script file */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    /** The output flag file */
    @OutputFile
    final RegularFileProperty output_flag = newOutputFile();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public JoinClusteredCMPTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void join() {

        // Submit the execution
        workerExecutor.submit(JoinClusteredCMPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        clustered_cmp_file_list,
                        script_file.getAsFile().get(),
                        clustered_model_file.getAsFile().get(),
                        output_flag.getAsFile().get(),
                        stream_start, stream_end,
                        project.configurationVoiceBuilding.hts_wrapper,
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to join isolated clustered models to a common model
 *
 */
class JoinClusteredCMPWorker implements Runnable {
    /** The file containing the list of monophones */
    private File list_file;

    /** Isolated clustered files */
    ArrayList<File> clustered_files;

    /** Produced script file */
    private File script_file;

    /** Produced joined cluster model file */
    private File join_clustered_file;

    /** Produced output flag file to link with other tasks */
    private File output_flag;

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /** Configuration object */
    private Object configuration;

    /** The start index of the manipulated stream */
    private int stream_start;

    /** The end index of the manipulated stream */
    private int stream_end;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public JoinClusteredCMPWorker(File list_file, ArrayList<File> clustered_files,
                                  File script_file, File join_clustered_file, File output_flag,
                                  int stream_start, int stream_end,
                                  HTSWrapper hts_wrapper, Object configuration) {
        // Input files
        this.list_file = list_file;
        this.clustered_files = clustered_files;

        // Generate model and corresponding script
        this.script_file = script_file;
        this.join_clustered_file = join_clustered_file;

        this.output_flag = output_flag

        // Wrapper + configuration
        this.hts_wrapper = hts_wrapper;
        this.configuration = configuration;
        this.stream_start = stream_start;
        this.stream_end = stream_end;
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Join (only if more than one stream are used)
        if (configuration.models.cmp.streams.size() > 1) {

            // Generate script
            def script_content = ""
            for (s in 2..configuration.models.global.nb_emitting_states+1) {

                // Find the accurate coef clustered model file
                File cur_clustered_file = clustered_files.get(s-2);


                // Generate joing part of the script
                script_content += sprintf("JM %s {*.state[%d].stream[%d-%d]}\n",
                                          cur_clustered_file.toString(),
                                          s, stream_start, stream_end)
            }
            script_file.text = script_content;

            // Join !
            hts_wrapper.HHEdOnMMF(script_file.toString(),
                                  list_file.toString(),
                                  join_clustered_file.toString(),
                                  join_clustered_file.toString(),
                                  [])
        }

        output_flag.text = "done"
    }
}
