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
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class JoinClusteredCMPTask extends DefaultTask {
    /** Externalize */
    int local_cur_clus_it;

    @InputFiles
    ConfigurableFileCollection clustered_cmp_files = project.files()

    @InputFile
    final RegularFileProperty list_file = newInputFile();

    @OutputFile
    final RegularFileProperty script_file = newOutputFile();

    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile();


    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void join() {

        // Prepare the file part
        def clustered_cmp_files_set = clustered_cmp_files.getFiles();
        File cur_clustered_cmp_file = null;

        // Join (only if more than one stream are used)
        //   1. copy the first stream models
        def tmp_stream = project.configuration.user_configuration.models.cmp.streams[0]
        for (File cur_file: clustered_cmp_files_set) {
            if (cur_file.getName().endsWith("${tmp_stream.name}.${local_cur_clus_it}")) {
                cur_clustered_cmp_file = cur_file;
                break;
            }
        }
        project.copy {
            from cur_clustered_cmp_file
            into clustered_model_file.getAsFile().get().getParent()
            rename { file -> clustered_model_file.getAsFile().get().getName() }
        }

        //  2. join the other one
        if (project.configuration.user_configuration.models.cmp.streams.size() > 1) {

            // Generate script
            def script_content = ""
            for(def s=0; s<project.configuration.user_configuration.models.global.nb_emitting_states; s++) {
                def cur_stream = 1
                project.configuration.user_configuration.models.cmp.streams.each { stream ->

                    // Find the accurate coef clustered model file
                    cur_clustered_cmp_file = null;
                    for (File cur_file: clustered_cmp_files_set) {
                        if (cur_file.getName().endsWith("${stream.name}.${local_cur_clus_it}")) {
                            cur_clustered_cmp_file = cur_file;
                            break;
                        }
                    }
                    def end_stream = cur_stream
                    if (stream.is_msd) {
                        end_stream += stream.winfiles.size() - 1
                    }

                    if (cur_stream > 1) {
                        script_content += sprintf("JM %s {*.state[%d].stream[%d-%d]}\n",
                                                  cur_clustered_cmp_file.toString(),
                                                  s+2, cur_stream, end_stream)
                    }

                    cur_stream = end_stream + 1
                }
                script_content += "\n"
            }
            script_file.getAsFile().get().text = script_content;

            // Join !
            project.configurationVoiceBuilding.hts_wrapper.HHEdOnMMF(script_file.getAsFile().get().toString(),
                                                                     list_file.getAsFile().get().toString(),
                                                                     clustered_model_file.getAsFile().get().toString(),
                                                                     clustered_model_file.getAsFile().get().toString(),
                                                                     [])
        }

    }
}
