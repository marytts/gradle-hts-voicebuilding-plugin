package de.dfki.mary.htsvoicebuilding.export

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import de.dfki.mary.htsvoicebuilding.export.task.hts_engine.*

class ExportHTSEngine {
    public static void addTasks(Project project) {
        def last_cluster = project.configuration.user_configuration.settings.training.nb_clustering - 1

        def export_dir = new File("$project.buildDir/hts_engine")
        export_dir.mkdirs()

        project.task("exportHTSVoiceHeader") {
            description "Generate the header for the HTS engine voice export"

            def template_file = project.file("${project.configurationVoiceBuilding.template_dir}/htsvoice")
            def header_file = project.file("${export_dir}/voice_global_header.htsvoice")

            inputs.files template_file
            outputs.files header_file

            doLast {
                def binding = [configuration: project.configuration.user_configuration]
                project.copy {
                    from template_file
                    into header_file.getParent()

                    rename { file -> header_file.getName() }
                    expand(binding)
                }
            }
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

        project.task("exportHTSEngine") {
            dependsOn project.exportHTSVoiceHeader
            dependsOn project.convertCMPToHTSEngine
            dependsOn project.convertDURToHTSEngine
        }
    }
}
