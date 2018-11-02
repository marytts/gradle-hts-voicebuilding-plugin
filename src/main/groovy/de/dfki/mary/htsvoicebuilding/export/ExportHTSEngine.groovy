package de.dfki.mary.htsvoicebuilding.export

// Gradle basline import
import org.gradle.api.Project

// HTS Engine export tasks import
import de.dfki.mary.htsvoicebuilding.export.task.hts_engine.*

class ExportHTSEngine {
    public static void addTasks(Project project) {
        def last_cluster = project.configuration.user_configuration.settings.training.nb_clustering - 1

        def export_dir = new File("$project.buildDir/hts_engine")
        export_dir.mkdirs()

        project.task("exportHTSVoiceHeader", type:ExportHTSEngineHeaderTask) {
            description "Generate the header for the HTS engine voice export"

            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/htsvoice")
            header_file = project.file("${export_dir}/voice_global_header.cfg")
        }

        project.task("convertCMPToHTSEngine", type: ConvertCMPToHTSEngineTask) {
            description "Convert the CMP set to HTS Engine voice compatible format"

            // Inputs
            script_template_file = project.file("${project.configurationVoiceBuilding.template_dir}/cv_hts_engine.hed");
            list_file = project.generateFullList.list_file

            // FIXME: find a more direct part for the trees!
            def m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                    def f = project.file("${project.configurationVoiceBuilding.tree_dir}/fullcontext_${last_cluster}/${stream.name}_${i}.inf")
                    m_files.add(f)
                }
            }
            input_tree_files.setFrom(m_files)

            input_model_file = project.property("trainClusteredModels_${last_cluster}").trained_cmp_file

            // Outputs
            m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/cv_hts_engine_${stream.kind}.hed")
                m_files.add(f)
            }
            script_files.setFrom(m_files)

            m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${export_dir}/${stream.kind}.inf")
                m_files.add(f)
            }
            output_tree_files.setFrom(m_files)


            m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${export_dir}/${stream.kind}.pdf")
                m_files.add(f)
            }
            output_model_files.setFrom(m_files)
        }

        project.task("convertDURToHTSEngine", type: ConvertDURToHTSEngineTask) {
            description "Convert the duration set to HTS Engine voice compatible format"

            // Inputs
            script_template_file = project.file("${project.configurationVoiceBuilding.template_dir}/cv_hts_engine.hed");
            list_file = project.generateFullList.list_file

            input_tree_file = project.file("${project.configurationVoiceBuilding.tree_dir}/dur.${last_cluster}.inf")
            input_model_file = project.property("trainClusteredModels_${last_cluster}").trained_dur_file

            // Outputs
            script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/cv_hts_engine_dur.hed")
            output_tree_file = project.file("${export_dir}/dur.inf")
            output_model_file = project.file("${export_dir}/dur.pdf")
        }


        project.task("exportHTSEnginePosition", type: ExportHTSEnginePositionTask) {
            // Duration dependency !
            dur_pdf   = project.property("convertDURToHTSEngine").output_model_file
            dur_tree  = project.property("convertDURToHTSEngine").output_tree_file

            // FIXME: why this dependency is needed !
            dependsOn "convertCMPToHTSEngine"
            cmp_pdfs  = project.property("convertCMPToHTSEngine").output_model_files
            cmp_trees = project.property("convertCMPToHTSEngine").output_tree_files

            // Produced dependency file
            position_file = project.file("${export_dir}/position.cfg")
        }

        project.task("exportHTSEngine", type: ExportHTSEngineTask) {
            header_file = project.exportHTSVoiceHeader.header_file
            position_file = project.exportHTSEnginePosition.position_file

            // Duration dependency !
            dur_pdf   = project.property("convertDURToHTSEngine").output_model_file
            dur_tree  = project.property("convertDURToHTSEngine").output_tree_file

            // CMP Dependencies
            cmp_pdfs  = project.property("convertCMPToHTSEngine").output_model_files
            cmp_trees = project.property("convertCMPToHTSEngine").output_tree_files

            // Final voice file
            voice_file = project.file("${export_dir}/voice.hts_voice")
        }
    }
}
