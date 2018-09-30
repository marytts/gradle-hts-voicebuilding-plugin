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
public class ConvertDurToLabTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    int local_cur_clus_it;

    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    @InputFile
    final DirectoryProperty dur_dir = newInputDirectory();

    @OutputFile
    final DirectoryProperty lab_dir = newOutputDirectory();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ConvertDurToLabTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void convert() {

        for (String cur_file: list_file.getAsFile().get().readLines()) {

            // Submit the execution for the cmpation
            workerExecutor.submit(ConvertDurToLabWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            new File(dur_dir.getAsFile().get().toString(), "${cur_file}.dur"),
                            new File(lab_dir.getAsFile().get().toString(), "${cur_file}.lab"),
                            project.configuration.user_configuration.signal.frameshift
                        );
                    }
                });
        }
    }
}



/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class ConvertDurToLabWorker implements Runnable {

    private float framehift;
    private File dur_file;
    private File lab_file;


    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param cmp_output_file the output CMP file
     *  @param configuration the configuration object
     */
    @Inject
    public ClusteringCMPWorker(File dur_file, File lab_file, float frameshift) {
        this.dur_file = dur_file;
        this.lab_file = lab_file;
        this.frameshift = frameshift;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {
        def dur_regexp =  "(.*)\\.state\\[[0-9]*\\].*duration=([0-9]*) .*"

        def input = new File("${project.buildDir}/gen_align/${cur_file}.dur")
        def output_lab = new File("${project.buildDir}/alignment/${cur_file}.lab")

        output_lab.text = "";
        def total_state = 5 // FIXME:
        def id_state = 1
        def t = 0
        input.eachLine { line ->
            line = line.trim()

            if (id_state <= total_state)
                {
                // Retrieve infos
                def pattern = ~dur_regexp
                def matcher = pattern.matcher(line)

                def label = matcher[0][1]
                def nb_frames = Integer.parseInt(matcher[0][2])


                // Compute start
                def start = t * frameshift * 1E+4
                def end = (t + nb_frames) * frameshift * 1E+4

                // Output
                def result = String.format("%d %d %s[%d]",
                                           start.intValue(), end.intValue(), label, id_state+1)
                if (id_state == 1)
                    {
                    result += " " + label
                }
                output_lab << result + "\n"

                t += nb_frames
                id_state += 1
            }
            else
                {
                id_state = 1;
            }
        }
    }
}
