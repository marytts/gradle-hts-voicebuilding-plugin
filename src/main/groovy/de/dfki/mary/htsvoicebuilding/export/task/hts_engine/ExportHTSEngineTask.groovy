package de.dfki.mary.htsvoicebuilding.export.task.hts_engine

import java.nio.file.Files;
import java.io.FileOutputStream;

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
public class ExportHTSEngineTask extends DefaultTask {
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

    @InputFile
    final RegularFileProperty header_file = newInputFile()

    @InputFile
    final RegularFileProperty position_file = newInputFile()

    @OutputFile
    final RegularFileProperty voice_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ExportHTSEngineTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(ExportHTSEngineWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        dur_pdf.getAsFile().get(),
                        dur_tree.getAsFile().get(),
                        cmp_pdfs.getFiles(),
                        cmp_trees.getFiles(),
                        header_file.getAsFile().get(),
                        position_file.getAsFile().get(),
                        voice_file.getAsFile().get(),
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
class ExportHTSEngineWorker implements Runnable {

    /** Configuration object */
    private Object configuration;

    private File dur_pdf;

    private File dur_tree;

    private Set<File> cmp_pdfs;

    private Set<File> cmp_trees;

    private File header_file;

    private File position_file;

    private File voice_file;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ExportHTSEngineWorker(File dur_pdf, File dur_tree,
                                         Set<File> cmp_pdfs, Set<File> cmp_trees,
                                         File header_file, File position_file,
                                         File voice_file, Object configuration) {

        this.dur_pdf = dur_pdf
        this.dur_tree = dur_tree
        this.cmp_pdfs = cmp_pdfs
        this.cmp_trees = cmp_trees
        this.header_file = header_file
        this.position_file = position_file
        this.voice_file = voice_file
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        FileOutputStream fos = new FileOutputStream(voice_file);
        byte[] buf = null;

        // Header
        buf = Files.readAllBytes(header_file.toPath());
        fos.write(buf);

        // Position
        buf = Files.readAllBytes(position_file.toPath());
        fos.write(buf);

        // Data
        int nb_streams = configuration.models.cmp.streams.size()

        //  = Duration PDF
        buf = Files.readAllBytes(dur_pdf.toPath());
        fos.write(buf);

        //  = Duration tree
        buf = Files.readAllBytes(dur_tree.toPath());
        fos.write(buf);

        //  = Window part (FIXME: make a dependency to files directly?)
        configuration.models.cmp.streams.each { stream ->
            stream.winfiles.each { win_file ->
                buf = Files.readAllBytes(win_file.toPath());
                fos.write(buf);
            }
        }

        //  = Stream PDF part
        Iterator<File> it_pdf  = cmp_pdfs.iterator()
        for (int i=0; i<nb_streams; i++) {
            File pdf_file = it_pdf.next()

            buf = Files.readAllBytes(pdf_file.toPath());
            fos.write(buf);
        }

        //  = Stream tree part
        Iterator<File> it_tree = cmp_trees.iterator()
        for (int i=0; i<nb_streams; i++) {
            File tree_file = it_tree.next()


            buf = Files.readAllBytes(tree_file.toPath());
            fos.write(buf);
        }

        fos.close();
    }
}
