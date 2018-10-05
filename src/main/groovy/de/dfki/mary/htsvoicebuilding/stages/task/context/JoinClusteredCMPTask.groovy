package de.dfki.mary.htsvoicebuilding.stages.task.context

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

    /** The individual clustered files */
    @InputFiles
    ConfigurableFileCollection clustered_cmp_files = project.files()

    /** The list of full context labels */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The produced joining script file */
    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    /** The produced join clustered models */
    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile();


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
        // Generate list of files
        ArrayList<File> clustered_files = new ArrayList<File>();

        project.configuration.user_configuration.models.cmp.streams.each { stream ->
            for (File cur_file: clustered_cmp_files) {
                if (cur_file.getName().endsWith("${stream.name}.${local_cur_clus_it}")) {
                    clustered_files.add(cur_file)
                    break;
                }
            }
        }

        // Submit the execution
        workerExecutor.submit(JoinClusteredCMPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        list_file.getAsFile().get(),
                        clustered_files,
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

    /** HTSWrapper object */
    private HTSWrapper hts_wrapper;

    /** Configuration object */
    private Object configuration;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public JoinClusteredCMPWorker(File list_file, ArrayList<File> clustered_files,
                                  File script_file, File join_clustered_file,
                                  HTSWrapper hts_wrapper, Object configuration) {
        // Input files
        this.list_file = list_file;
        this.clustered_files = clustered_files;

        // Generate model and corresponding script
        this.script_file = script_file;
        this.join_clustered_file = join_clustered_file;

        // Wrapper + configuration
        this.hts_wrapper = hts_wrapper;
        this.configuration = configuration
    }

    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Prepare the file part
        File cur_clustered_file = clustered_files.get(0);

        //   1. copy the first stream models
        Files.copy(cur_clustered_file.toPath(), join_clustered_file.toPath())

        // Join (only if more than one stream are used)
        //  2. join the other one
        if (configuration.models.cmp.streams.size() > 1) {

            // Generate script
            def script_content = ""
            for(def s=0; s<configuration.models.global.nb_emitting_states; s++) {
                def cur_stream = 1
                def i_stream = 0
                configuration.models.cmp.streams.each { stream ->

                    // Find the accurate coef clustered model file
                    cur_clustered_file = clustered_files.get(i_stream);

                    // Adapt end stream index
                    def end_stream = cur_stream
                    if (stream.is_msd) {
                        end_stream += stream.winfiles.size() - 1
                    }

                    // Generate joing part of the script
                    if (cur_stream > 1) {
                        script_content += sprintf("JM %s {*.state[%d].stream[%d-%d]}\n",
                                                  cur_clustered_file.toString(),
                                                  s+2, cur_stream, end_stream)
                    }

                    // Update stream indexes
                    cur_stream = end_stream + 1
                    i_stream += 1
                }
                script_content += "\n"
            }
            script_file.text = script_content;

            // Join !
            hts_wrapper.HHEdOnMMF(script_file.toString(),
                                  list_file.toString(),
                                  join_clustered_file.toString(),
                                  join_clustered_file.toString(),
                                  [])
        }
    }
}
