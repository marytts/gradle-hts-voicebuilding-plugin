package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file

import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.io.FileUtils

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


class ExportHTSEngine {
    public static void addTasks(Project project) {
        def last_cluster = project.configuration.user_configuration.settings.training.nb_clustering - 1
        def export_dir = new File("$project.buildDir/hts_engine")
        export_dir.mkdirs()

        project.task("exportHTSVoiceHeader") {
            doLast {
                def binding = [configuration: project.configuration.user_configuration]
                project.copy {
                    from "${project.configurationVoiceBuilding.template_dir}/htsvoice"
                    into export_dir

                    rename { file -> "voice_global_header.htsvoice" }
                    expand(binding)
                }
            }
        }

        project.task("exportHTSEngine") {
            dependsOn project.exportHTSVoiceHeader
        }
    }
}
