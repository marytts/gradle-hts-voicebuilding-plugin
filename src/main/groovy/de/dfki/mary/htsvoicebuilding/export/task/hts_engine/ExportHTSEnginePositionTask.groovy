package de.dfki.mary.htsvoicebuilding.export.task.hts_engine

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
 *  Task which exports the position information
 *
 */
public class ExportHTSEnginePositionTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    @InputFile
    final RegularFileProperty dur_pdf = newInputFile()

    @InputFile
    final RegularFileProperty dur_tree = newInputFile()

    @InputFiles
    ConfigurableFileCollection cmp_pdfs = project.files()

    @InputFiles
    ConfigurableFileCollection cmp_trees = project.files()

    @OutputFile
    final RegularFileProperty position_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ExportHTSEnginePositionTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(ExportHTSEnginePositionWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        dur_pdf.getAsFile().get(),
                        dur_tree.getAsFile().get(),
                        cmp_pdfs.getFiles(),
                        cmp_trees.getFiles(),
                        position_file.getAsFile().get(),
                        project.configuration.user_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to exports the position information
 *
 */
class ExportHTSEnginePositionWorker implements Runnable {

    /** Configuration object */
    private Object configuration;

    private File dur_pdf;

    private File dur_tree;

    private Set<File> cmp_pdfs;

    private Set<File> cmp_trees;

    private File position_file;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ExportHTSEnginePositionWorker(File dur_pdf, File dur_tree,
                                         Set<File> cmp_pdfs, Set<File> cmp_trees,
                                         File position_file, Object configuration) {

        this.dur_pdf = dur_pdf
        this.dur_tree = dur_tree
        this.cmp_pdfs = cmp_pdfs
        this.cmp_trees = cmp_trees
        this.position_file = position_file
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def position_content = "[POSITION]\n"
        int f_index = 0;

        // Duration part
        position_content += "DURATION_PDF:${f_index}-${f_index + dur_pdf.length() - 1}\n"
        f_index += dur_pdf.length()
        position_content += "DURATION_TREE:${f_index}-${f_index + dur_tree.length() - 1}\n"
        f_index += dur_tree.length()

        // Window part (FIXME: make a dependency to files directly?)
        int nb_streams = configuration.models.cmp.streams.size()

        configuration.models.cmp.streams.each { stream ->
            // Generate content
            def win_content_list = []
            stream.winfiles.each { win_file ->
                win_content_list.add("${f_index}-${f_index + win_file.length() - 1}")
                f_index += win_file.length()
            }

            // Add to position
            def win_content = String.join( ",", win_content_list)
            position_content += "STREAM_WIN[${stream.name}]:${win_content}\n"
        }

        // Stream PDF part
        Iterator<File> it_pdf  = cmp_pdfs.iterator()
        for (int i=0; i<nb_streams; i++) {
            Object stream = configuration.models.cmp.streams[i]
            File pdf = it_pdf.next()

            position_content += "STREAM_PDF[${stream.name}]:${f_index}-${f_index + pdf.length() - 1}\n"
            f_index += pdf.length()
        }

        // Stream tree part
        Iterator<File> it_tree = cmp_trees.iterator()
        for (int i=0; i<nb_streams; i++) {
            Object stream = configuration.models.cmp.streams[i]
            File tree = it_tree.next()

            position_content += "STREAM_TREE[${stream.name}]:${f_index}-${f_index + tree.length() - 1}\n"
            f_index += tree.length()
        }

        // TODO: GV part

        // Save the export
        position_file.text = position_content
    }
}
